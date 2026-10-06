package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.storeEmptyPantry
import uk.veritaslabs.vesta.automerge.storeEmptyShoppingList
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.homegroup.HubResult
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails
import uk.veritaslabs.vesta.x0x.AbstractX0xClient

/**
 * Drives three real x0xd daemons through a Home's mailbox and hub (docs/plans/ADR-020-implementation-plan.md, slice 7): two
 * "phones" running the real [DeviceDataSyncService] and a plain daemon as the hub. Skipped unless `VESTA_X0X_HUB_NAMES` names three
 * x0x instances (comma separated: phone A, phone B, hub). The test starts and stops them itself with `x0x start|stop --name <name>`,
 * so use names nothing else uses, never a daemon in use; a named instance keeps its data under `~/.local/share/x0x-<name>` and its
 * identity under `~/.x0x-<name>`, which survive the test (delete both to start clean). Run with:
 * `VESTA_X0X_HUB_NAMES=vhub-a,vhub-b,vhub-h ./gradlew :shared:jvmTest --tests '*HubDaemonIntegrationTest'` (it takes a few minutes).
 *
 * The scenario: A and B are in one Home; a hub joins its group as the printed commands say and is chosen as the hub; B is stopped;
 * A changes the Pantry and is stopped; B is started and gets the change from the hub alone, with A down. Then the hub is
 * removed (the group is rekeyed), enrolled again, and the same thing is repeated.
 */
class HubDaemonIntegrationTest {

    /** A named x0xd, started and stopped through the `x0x` command. */
    private class Daemon(val name: String) {
        private val dir = File(System.getProperty("user.home"), ".local/share/x0x-$name")

        val client = object : AbstractX0xClient() {
            override val endpoint: String get() = dir.path

            // Read each time: a restarted daemon may listen on another port.
            override suspend fun resolveDaemon(): Pair<String, String?>? {
                val port = dir.resolve("api.port").takeIf { it.exists() }?.readText()?.trim()?.substringAfterLast(':') ?: return null
                return "http://127.0.0.1:$port" to dir.resolve("api-token").takeIf { it.exists() }?.readText()?.trim()
            }
        }

        /** The daemon's own output, kept so a daemon that doesn't come up can be explained. */
        private val log = File(System.getProperty("java.io.tmpdir"), "vesta-hub-it-$name.log")
        private var process: Process? = null

        suspend fun start() {
            if (client.health() != null) return
            withContext(Dispatchers.IO) {
                process = ProcessBuilder("x0xd", "--name", name)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                    .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                    .start()
            }
            until("$name to answer (its output is in $log)", seconds = 90) { client.health() != null }
        }

        suspend fun stop() {
            withContext(Dispatchers.IO) {
                runCatching { ProcessBuilder("x0x", "stop", "--name", name).redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor(30, TimeUnit.SECONDS) }
                process?.let { if (!it.waitFor(30, TimeUnit.SECONDS)) it.destroy() }
                process = null
            }
            until("$name to stop", seconds = 90) { client.health() == null }
        }
    }

    /** A phone: the real data sync over a daemon, with its own database. */
    private class Phone(val daemon: Daemon, private val scope: CoroutineScope) {
        val database = openInMemoryDatabase()
        val devices = SyncedDeviceRepository(database)
        val pantries = PantrySources(database)
        val shopping = ShoppingListSources(database, pantries)
        val sync = DeviceDataSyncService(daemon.client, database, devices, pantries, shopping, interval = 3.seconds)
        val homes = HomeRepository(database, sync.homeChanges)
        val profiles = ProfileRepository(database, changes = sync.profileChanges)
        private var running: Job? = null

        /** Starts the daemon and the sync, as opening the app does. */
        suspend fun open() {
            daemon.start()
            running = scope.launch { sync.run() }
        }

        /** Stops the sync and the daemon, as closing the app does. */
        suspend fun close() {
            running?.cancel()
            running = null
            daemon.stop()
        }

        suspend fun agentId() = daemon.client.agent().agentId!!

        suspend fun joinedHome(id: String) {
            database.homeQueries.insert(id, "Maple Street", HomeShape.Family.storageValue, 1L)
            storeEmptyPantry(database, id)
            storeEmptyShoppingList(database, id)
        }

        suspend fun group(homeId: String) = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()
        suspend fun titles(homeId: String) = pantries.forHome(homeId)!!.contents.first().items.map { it.title }.sorted()

        suspend fun add(homeId: String, title: String) =
            pantries.forHome(homeId)!!.save(PantryItemDetails(primaryCategory = "Food", title = title, packageSize = "1 kg", stockCount = 1))

        /** Whether everything in the Pantry is in the mailbox. */
        suspend fun deposited(homeId: String): Boolean {
            val heads = pantries.forHome(homeId)!!.heads()
            return database.mailboxQueries.selectHeads(homeId, pantryDocumentId(homeId)).awaitAsOneOrNull()?.contentEquals(heads) == true
        }
    }

    private companion object {
        suspend fun until(what: String, seconds: Int = 180, condition: suspend () -> Boolean) {
            var waited = 0
            while (!runCatching { condition() }.getOrDefault(false)) {
                check(waited++ < seconds / 2) { "timed out waiting for $what" }
                delay(2.seconds)
            }
        }
    }

    @Test
    fun aPhoneReadsWhatAnotherLeftThroughTheHubAloneAndAgainAfterTheHubIsReplaced() {
        val names = System.getenv("VESTA_X0X_HUB_NAMES")?.split(',')?.map { it.trim() }?.takeIf { it.size == 3 } ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val a = Phone(Daemon(names[0]), scope)
        val b = Phone(Daemon(names[1]), scope)
        val hub = Daemon(names[2])
        try {
            runBlocking {
                withTimeout(25.minutes) {
                    // Arrange: A made the Home and admitted B; both phones are open.
                    hub.start()
                    a.daemon.start()
                    b.daemon.start()
                    val aId = a.agentId()
                    val bId = b.agentId()
                    val hubId = hub.client.agent().agentId!!
                    val homeId = a.homes.create("Maple Street", HomeShape.Family)
                    b.joinedHome(homeId)
                    a.sync.admit(homeId, b.profiles.current().id, b.profiles.current().displayName, "member", bId)
                    b.sync.admit(homeId, a.profiles.current().id, a.profiles.current().displayName, "admin", aId)
                    a.open()
                    b.open()
                    until("both phones to be in the Home's group with the mailbox open") {
                        a.group(homeId)?.storeId != null && b.group(homeId)?.storeId != null
                    }
                    val groupId = a.group(homeId)!!.groupId

                    // Act: the hub is enrolled with the commands the app prints, and chosen.
                    enrolHub(a, hub, homeId, hubId)
                    until("the hub to be an admin of the group") {
                        hub.client.getGroupMembers(groupId).first { it.agentId == hubId }.role == "admin"
                    }

                    // Round one: B is away while A edits, and A is gone before B is back.
                    leaveOneItemAndComeBack(a, b, hub, hubId, aId, homeId, "Flour")

                    // Act: the hub is removed, which changes the group's keys, and enrolled again.
                    a.open()
                    until("A to be in the group again") { a.sync.hubs.hubs(homeId).any { it.agentId == hubId } }
                    assertEquals(HubResult.Done, a.sync.hubs.removeHub(homeId, hubId))
                    assertTrue(a.daemon.client.getGroupMembers(groupId).none { it.agentId == hubId && it.state == "active" })
                    enrolHub(a, hub, homeId, hubId)

                    // Round two, under the new keys.
                    leaveOneItemAndComeBack(a, b, hub, hubId, aId, homeId, "Rice")
                    assertEquals(listOf("Flour", "Rice"), b.titles(homeId))
                }
            }
        } finally {
            scope.cancel()
            runBlocking {
                listOf(a.daemon, b.daemon, hub).forEach { runCatching { it.stop() } }
            }
        }
    }

    /** What an admin does: runs the printed commands on the hub machine, then chooses the agent that joined. */
    private suspend fun enrolHub(admin: Phone, hub: Daemon, homeId: String, hubId: String) {
        until("the enrolment commands to be ready") { admin.sync.hubs.enrolment(homeId) != null }
        val enrolment = admin.sync.hubs.enrolment(homeId)!!
        val invite = enrolment.commands.lines().first().removePrefix("x0x group join ")
        hub.client.joinGroup(invite)
        until("the hub to be active in the group") {
            hub.client.getGroupMembers(enrolment.groupId).any { it.agentId == hubId && it.state == "active" }
        }
        hub.client.openGroupStore(enrolment.groupId, "mailbox")
        until("the hub to be offered as a candidate") { admin.sync.hubs.candidates(homeId) == listOf(hubId) }
        assertEquals(HubResult.Done, admin.sync.hubs.useAsHub(homeId, hubId))
    }

    /**
     * B is closed; A (open) adds [title] and waits until it is in the mailbox and on the hub, then is closed; B is opened and must
     * get [title] with A down, which only the hub can give it. Leaves B open and A closed.
     */
    private suspend fun leaveOneItemAndComeBack(a: Phone, b: Phone, hub: Daemon, hubId: String, aId: String, homeId: String, title: String) {
        b.close()
        a.add(homeId, title)
        until("A to deposit $title") { a.deposited(homeId) }
        val store = a.group(homeId)!!.storeId!!
        until("the hub to hold A's entries") { hub.client.listStoreKeys(store).any { it.key.startsWith("$aId/") } }
        a.close()

        b.open()
        until("B to read $title from the hub, with A down", seconds = 300) { title in b.titles(homeId) }
    }
}
