package uk.veritaslabs.vesta.datasync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.homegroup.HubResult
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.kits.x0x.X0xApiException

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
                    val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
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
                    leaveOneItemAndComeBack(a, b, listOf(hub), aId, homeId, "Flour")

                    // Act: the hub is removed, which changes the group's keys, and enrolled again.
                    a.open()
                    until("A to be in the group again") { a.sync.hubs.hubs(homeId).any { it.agentId == hubId } }
                    assertEquals(HubResult.Done, a.sync.hubs.removeHub(homeId, hubId))
                    assertTrue(a.daemon.client.getGroupMembers(groupId).none { it.agentId == hubId && it.state == "active" })
                    // x0x refuses a removed daemon's new join (`join_already_pending`) until that daemon has dropped the group.
                    until("the removed hub's daemon to drop the group", seconds = 600) {
                        (runCatching { hub.client.getGroupMembers(groupId) }.exceptionOrNull() as? X0xApiException)?.status == 404
                    }
                    enrolHub(a, hub, homeId, hubId)

                    // Round two, under the new keys.
                    leaveOneItemAndComeBack(a, b, listOf(hub), aId, homeId, "Rice")
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
}
