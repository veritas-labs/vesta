@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

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
import uk.veritaslabs.kits.spaces.HubResult
import uk.veritaslabs.vesta.ui.screens.HomeShape

/**
 * Drives four real x0xd daemons through a Home with several hubs (docs/plans/ADR-020-several-hubs-plan.md, slice 6): two "phones"
 * running the real [DeviceDataSyncService], two plain daemons as hubs, and then phone A's own daemon as a third hub. Skipped unless
 * `VESTA_X0X_HUBS_NAMES` names four x0x instances (comma separated: phone A, phone B, hub 1, hub 2). Like [HubDaemonIntegrationTest]
 * it starts and stops them itself, so use names nothing else uses, never a daemon in use; delete `~/.local/share/x0x-<name>` and
 * `~/.x0x-<name>` to start clean. Run with:
 * `VESTA_X0X_HUBS_NAMES=vhubs-a,vhubs-b,vhubs-h1,vhubs-h2 ./gradlew :shared:jvmTest --tests '*SeveralHubsDaemonIntegrationTest'`
 * (it takes about ten minutes).
 *
 * The scenario: both hubs are enrolled and chosen. B is away while A edits, and A is gone before B is back: B gets the change from
 * the hubs alone. Hub 1 is removed (the group is rekeyed) and the same is repeated with hub 2 alone. Then A's own agent is made a
 * hub, as a desktop's would be; A's Vesta is closed but its daemon stays up; B writes; A's daemon is restarted with nothing
 * reopening the store, and still holds B's entries.
 */
class SeveralHubsDaemonIntegrationTest {

    @Test
    fun severalHubsServeTheMailboxAndAnOwnDeviceHubKeepsItAcrossARestart() {
        val names = System.getenv("VESTA_X0X_HUBS_NAMES")?.split(',')?.map { it.trim() }?.takeIf { it.size == 4 } ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val a = Phone(Daemon(names[0]), scope)
        val b = Phone(Daemon(names[1]), scope)
        val hub1 = Daemon(names[2])
        val hub2 = Daemon(names[3])
        try {
            runBlocking {
                withTimeout(40.minutes) {
                    // Arrange: A made the Home and admitted B; both phones are open.
                    listOf(hub1, hub2, a.daemon, b.daemon).forEach { it.start() }
                    val aId = a.agentId()
                    val bId = b.agentId()
                    val hub1Id = hub1.client.agent().agentId!!
                    val hub2Id = hub2.client.agent().agentId!!
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

                    // Act: both hubs are enrolled with the commands the app prints, and chosen.
                    enrolHub(a, hub1, homeId, hub1Id)
                    enrolHub(a, hub2, homeId, hub2Id)
                    until("both hubs to be admins of the group") {
                        listOf(hub1 to hub1Id, hub2 to hub2Id).all { (hub, id) -> hub.client.getGroupMembers(groupId).first { it.agentId == id }.role == "admin" }
                    }
                    assertEquals(setOf(hub1Id, hub2Id), a.sync.hubs.hubs(homeId).map { it.agentId }.toSet())

                    // Round one: B is away while A edits, and A is gone before B is back; both hubs hold A's entries.
                    leaveOneItemAndComeBack(a, b, listOf(hub1, hub2), aId, homeId, "Flour")

                    // Act: hub 1 is removed, which changes the group's keys; hub 2 is the only hub left.
                    a.open()
                    until("A to be in the group again") { a.sync.hubs.hubs(homeId).size == 2 }
                    assertEquals(HubResult.Done, a.sync.hubs.removeHub(homeId, hub1Id))
                    assertTrue(a.daemon.client.getGroupMembers(groupId).none { it.agentId == hub1Id && it.state == "active" })
                    assertEquals(listOf(hub2Id), a.sync.hubs.hubs(homeId).map { it.agentId })

                    // Round two, under the new keys, through hub 2 alone.
                    leaveOneItemAndComeBack(a, b, listOf(hub2), aId, homeId, "Rice")
                    assertEquals(listOf("Flour", "Rice"), b.titles(homeId))

                    // Act: A's own agent becomes a hub (an admin's own device), and its Vesta is closed with its daemon left running.
                    a.open()
                    until("A to be in the group again") { a.group(homeId)?.storeId != null && a.sync.hubs.hubs(homeId).isNotEmpty() }
                    assertEquals(listOf(aId), a.sync.hubs.ownDeviceCandidates(homeId))
                    assertEquals(HubResult.Done, a.sync.hubs.useAsHub(homeId, aId))
                    assertTrue(a.sync.hubs.hubs(homeId).first { it.agentId == aId }.isDevice)
                    a.closeApp()

                    // B writes; A's daemon, with no Vesta on it, replicates the entries and keeps them across a restart.
                    b.add(homeId, "Oats")
                    until("B to deposit Oats") { b.deposited(homeId) }
                    val store = a.group(homeId)!!.storeId!!
                    until("A's daemon to hold B's entries with no Vesta running") { a.daemon.client.listStoreKeys(store).any { it.key.startsWith("$bId/") } }
                    a.daemon.restart()
                    until("A's daemon to hold B's entries after the restart, with nothing reopening the store", seconds = 300) {
                        a.daemon.client.listStoreKeys(store).any { it.key.startsWith("$bId/") }
                    }
                }
            }
        } finally {
            scope.cancel()
            runBlocking {
                listOf(a.daemon, b.daemon, hub1, hub2).forEach { runCatching { it.stop() } }
            }
        }
    }
}
