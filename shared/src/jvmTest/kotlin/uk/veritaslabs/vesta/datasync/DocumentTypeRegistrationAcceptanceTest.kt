package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.automerge.VestaHomeSeeding
import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.vesta.home.homeRepository
import uk.veritaslabs.vesta.getDeviceName
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.encoding.Base64
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.SyncState
import uk.veritaslabs.vesta.automerge.storeEmptyPantry
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.docs.AutomergeDocumentCore
import uk.veritaslabs.vesta.docs.HomeDocument
import uk.veritaslabs.vesta.docs.HomeDocumentStore
import uk.veritaslabs.vesta.docs.HomeDocumentType
import uk.veritaslabs.vesta.docs.Scope
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

/** A throwaway document type that exists only in this test: a map of note ids to text, as an Automerge document. */
private class ProbeDocument(document: Document, persist: suspend (ByteArray) -> Unit, onLocalChange: () -> Unit) : HomeDocument {
    private val state = MutableStateFlow(document.readNotes())
    private val core = AutomergeDocumentCore(document, { state.value = document.readNotes() }, persist, onLocalChange)
    val notes get() = state

    suspend fun add(key: String, text: String) = core.edit {
        val tx = startTransaction()
        tx.set(ObjectId.ROOT, key, text)
        tx.commit()
    }

    suspend fun refresh() = core.refresh()

    override suspend fun generateSync(state: SyncState) = core.generateSync(state)
    override suspend fun receiveSync(state: SyncState, message: ByteArray) = core.receiveSync(state, message)
    override suspend fun isInSync(state: SyncState) = core.isInSync(state)
    override suspend fun heads() = core.heads()
    override suspend fun changesSince(saved: ByteArray) = core.changesSince(saved)
    override suspend fun applyChanges(changes: ByteArray) = core.applyChanges(changes)
}

private fun Document.readNotes(): List<String> =
    mapEntries(ObjectId.ROOT).getOrNull().orEmpty().mapNotNull { (it.value as? AmValue.Str)?.value }.sorted()

/**
 * A document type the sync engine has never heard of, registered through [HomeDocumentType] only
 * (docs/plans/document-registry-and-source-collapse-plan.md, WP9; criteria in docs/testing/acceptance-tests.md,
 * "Document type registry (this plan)"). Nothing in the engine, the mailbox, the acknowledgement check, Sync activity or
 * Changes waiting names `probe`; the only thing done to the engine is handing it the extra type.
 */
class DocumentTypeRegistrationAcceptanceTest {

    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val probe = "probe"
    private val privateProbe = "private-probe"
    private val probeModule = "probe"

    /** One of the user's devices, with a shared `probe` document and a private `private-probe` document per Home. */
    private inner class Device(val agentId: String) {
        val node = network.node(agentId)
        val database: VestaDatabase = openInMemoryDatabase()
        val devices = SyncedDeviceRepository(database)
        val receivedDocuments = ConcurrentLinkedQueue<String>()

        private fun store(feature: String) = HomeDocumentStore(
            database, feature, { System.currentTimeMillis() },
            refreshNew = { it.refresh() },
            create = { _, document, persist, changed -> ProbeDocument(document, persist, changed) },
        )

        private val shared = store(probe)
        private val personal = store(privateProbe)

        val sync = DeviceDataSyncService(
            node, database, devices, ::getDeviceName,
            VestaHomeSources(database).documentTypes() + listOf(
                HomeDocumentType(probe, probeModule, Scope.Shared, shared::forHome, shared.localChanges),
                HomeDocumentType(privateProbe, probeModule, Scope.Private, personal::forHome, personal.localChanges),
            ),
            interval = 50.milliseconds, activityRefresh = 20.milliseconds,
            seeding = VestaHomeSeeding(VestaHomeSources(database), database),
        )
        val homes = homeRepository(database, sync.homeChanges)
        val profiles = ProfileRepository(database, deviceName = ::getDeviceName, changes = sync.profileChanges)

        init {
            node.onDirect = { message: X0xDirectMessage ->
                message.payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() }?.let { SyncEnvelope.decode(it) }
                    ?.let { receivedDocuments.add(it.documentId) }
                scope.launch { sync.handle(message) }
            }
        }

        fun start() {
            scope.launch { sync.run() }
        }

        suspend fun pairedWith(other: Device) = apply { devices.add(other.agentId, other.agentId) }

        suspend fun joinedHome(id: String, name: String, shape: HomeShape) {
            database.homeQueries.insert(id, name, shape.storageValue, 1L)
            storeEmptyPantry(database, id)
        }

        suspend fun addProbe(homeId: String, key: String, text: String) = shared.forHome(homeId)!!.add(key, text)
        suspend fun addPrivateProbe(homeId: String, key: String, text: String) = personal.forHome(homeId)!!.add(key, text)
        suspend fun probeNotes(homeId: String) = shared.forHome(homeId)?.notes?.value
        suspend fun privateProbeNotes(homeId: String) = personal.forHome(homeId)?.notes?.value
        suspend fun probeHeads(homeId: String) = shared.forHome(homeId)!!.heads()

        suspend fun activity(homeId: String) = database.syncEventQueries.selectPage(homeId, Long.MAX_VALUE, 100).awaitAsList()
        suspend fun changesWaiting(homeId: String) = sync.syncActivity.snapshot(homeId).first().changesWaiting
        suspend fun deposited(homeId: String, feature: String) = database.mailboxQueries.selectHeads(homeId, "$homeId:$feature").awaitAsOneOrNull()
    }

    private suspend fun eventually(check: suspend () -> Boolean) {
        withTimeout(10.seconds) { while (!runCatching { check() }.getOrDefault(false)) delay(20.milliseconds) }
    }

    /** Two people in one Home: X made it and admitted Y, as in the sync engine's own tests. */
    private suspend fun twoPeopleInOneHome(): Triple<Device, Device, String> {
        val x = Device("agent-x")
        val y = Device("agent-y")
        val homeId = x.homes.create("Maple Street", HomeShape.Family.storageValue)
        val xMember = x.profiles.current()
        val yMember = y.profiles.current()
        y.joinedHome(homeId, "Maple Street", HomeShape.Family)
        x.sync.admit(homeId, yMember.id, yMember.displayName, "member", y.agentId)
        y.sync.admit(homeId, xMember.id, xMember.displayName, "admin", x.agentId)
        return Triple(x, y, homeId)
    }

    @Test
    fun aDocumentTypeTheEngineNeverHeardOfSyncsDirectlyBetweenTwoDevicesOfAHome() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()

        // Act
        x.addProbe(homeId, "n1", "hello")

        // Assert
        eventually { y.probeNotes(homeId) == listOf("hello") }
        assertTrue("$homeId:$probe" in y.receivedDocuments)
        scope.cancel()
    }

    @Test
    fun aMemberWhoWasAwayCatchesUpOnTheNewDocumentTypeThroughTheMailbox() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually {
            x.database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.storeId != null &&
                y.database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.storeId != null
        }
        val groupId = x.database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()!!.groupId
        val hub = network.node("agent-hub")
        hub.joinGroup(x.node.createGroupInvite(groupId))
        hub.openGroupStore(groupId, "mailbox")
        network.offline += y.agentId

        // Act: X edits while Y is away and is gone by the time Y is back, so no direct message can carry it
        x.addProbe(homeId, "n1", "left for you")
        eventually { x.deposited(homeId, probe)?.contentEquals(x.probeHeads(homeId)) == true }
        network.offline += x.agentId
        network.comeOnline(y.agentId)

        // Assert
        eventually { y.probeNotes(homeId) == listOf("left for you") }
        scope.cancel()
    }

    @Test
    fun aPrivateDocumentTypeReachesTheUsersOtherDeviceButNeverAnotherMemberOrTheMailbox() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        val x2 = Device("agent-x2")
        x.pairedWith(x2)
        x2.pairedWith(x)
        x.start()
        x2.start()
        y.start()

        // Act
        x.addPrivateProbe(homeId, "p1", "secret")
        x.addProbe(homeId, "n1", "shared")
        eventually { x2.privateProbeNotes(homeId) == listOf("secret") }
        eventually { y.probeNotes(homeId) == listOf("shared") }
        eventually { x.deposited(homeId, probe) != null }
        delay(500.milliseconds) // long enough for several rounds to have passed

        // Assert
        assertTrue("$homeId:$privateProbe" !in y.receivedDocuments)
        assertEquals(emptyList(), y.privateProbeNotes(homeId))
        assertNull(x.deposited(homeId, privateProbe))
        scope.cancel()
    }

    @Test
    fun syncActivityShowsTheNewTypesEventsUnderItsModuleAndChangesWaitingFollowsItsUnacknowledgedEdit() = runBlocking {
        // Arrange: X's other device is away, so a private edit has nowhere to go and no mailbox to wait in
        val (x, y, homeId) = twoPeopleInOneHome()
        val x2 = Device("agent-x2")
        x.pairedWith(x2)
        x2.pairedWith(x)
        x.start()
        x2.start()
        y.start()
        eventually { x2.database.homeQueries.selectAll().awaitAsList().isNotEmpty() }
        network.offline += x2.agentId

        // Act
        x.addPrivateProbe(homeId, "p1", "waiting")
        val waitingWhileAway = runCatching { eventually { x.changesWaiting(homeId) } }.isSuccess
        network.comeOnline(x2.agentId)
        x.addProbe(homeId, "n1", "shared")
        eventually { y.probeNotes(homeId) == listOf("shared") }

        // Assert
        assertTrue(waitingWhileAway)
        eventually { !x.changesWaiting(homeId) }
        eventually { y.activity(homeId).any { it.kind == "received" && it.module == probeModule } }
        eventually { x.activity(homeId).any { it.kind == "sent" && it.module == probeModule } }
        assertNotNull(y.activity(homeId).firstOrNull { it.module == probeModule && it.peerAgent == "agent-x" })
        scope.cancel()
    }
}
