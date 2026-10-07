package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.devicepairing.PairingLink
import uk.veritaslabs.vesta.ui.screens.HomeShape

/**
 * Drives three real x0xd daemons through unpairing a device (docs/plans/unpaired-device-gap-plan.md, slice 4): phone A and A's tablet,
 * paired for real, and phone B, a second person, all in one Home's group. A removes the tablet as Settings does; the tablet must leave the
 * group (a real rekey), be unable to read what B writes next, and be refused when it asks for an invite again. Skipped unless
 * `VESTA_X0X_UNPAIR_NAMES` names three x0x instances (comma separated: phone A, A's tablet, phone B). Like [HubDaemonIntegrationTest] it starts
 * and stops them itself, so use names nothing else uses, never a daemon in use; delete `~/.local/share/x0x-<name>` and `~/.x0x-<name>` to start
 * clean. Run with:
 * `VESTA_X0X_UNPAIR_NAMES=vunp-a,vunp-t,vunp-b ./gradlew :shared:jvmTest --tests '*UnpairDaemonIntegrationTest'` (a few minutes).
 */
class UnpairDaemonIntegrationTest {

    @Test
    fun anUnpairedTabletIsTakenOutOfTheGroupAndCannotReadWhatIsWrittenNext() {
        val names = System.getenv("VESTA_X0X_UNPAIR_NAMES")?.split(',')?.map { it.trim() }?.takeIf { it.size == 3 } ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val a = Phone(Daemon(names[0]), scope, withPairing = true)
        val tablet = Phone(Daemon(names[1]), scope, withPairing = true)
        val b = Phone(Daemon(names[2]), scope)
        try {
            runBlocking {
                withTimeout(30.minutes) {
                    // Arrange: the tablet is paired with A for real, A made the Home and admitted B; all three are open
                    listOf(a, tablet, b).forEach { it.daemon.start() }
                    val aId = a.agentId()
                    val tabletId = tablet.agentId()
                    val bId = b.agentId()
                    a.open()
                    tablet.open()
                    val now = System.currentTimeMillis()
                    val link = PairingLink(agentId = tabletId, expiresAt = now + 10.minutes.inWholeMilliseconds, deviceName = "Tablet")
                    tablet.database.devicePairingLinkQueries.insert(link.agentId, link.agentId, now, link.expiresAt)
                    assertTrue(a.deviceSync.pairWith(link).isSuccess)
                    until("the tablet to know A") { tablet.devices.agentIds().contains(aId) }
                    val homeId = a.homes.create("Maple Street", HomeShape.Family)
                    b.joinedHome(homeId)
                    a.sync.admit(homeId, b.profiles.current().id, b.profiles.current().displayName, "member", bId)
                    b.sync.admit(homeId, a.profiles.current().id, a.profiles.current().displayName, "admin", aId)
                    b.open()
                    until("the tablet to have the Home and A's profile", seconds = 360) {
                        tablet.database.homeQueries.selectById(homeId).awaitAsOneOrNull() != null &&
                            tablet.profiles.current().id == a.profiles.current().id
                    }
                    until("all three to be in the Home's group with the mailbox open", seconds = 360) {
                        a.group(homeId)?.storeId != null && tablet.group(homeId)?.storeId != null && b.group(homeId)?.storeId != null
                    }
                    val groupId = a.group(homeId)!!.groupId
                    val store = tablet.group(homeId)!!.storeId!!
                    assertTrue(a.daemon.client.getGroupMembers(groupId).any { it.agentId == tabletId && it.state == "active" })

                    // Act: A removes the tablet, as Settings does
                    assertTrue(a.deviceSync.removeDevice(tabletId).isSuccess)

                    // Assert: it leaves the Home's group
                    until("the tablet to be out of the Home's group", seconds = 360) {
                        a.daemon.client.getGroupMembers(groupId).none { it.agentId == tabletId && it.state == "active" }
                    }
                    assertTrue(a.daemon.client.getGroupMembers(groupId).any { it.agentId == bId && it.state == "active" })

                    // B writes after it left: the tablet's daemon can no longer read the mailbox, and its Pantry never gets the item
                    b.add(homeId, "Flour")
                    until("B to deposit Flour") { b.deposited(homeId) }
                    until("the tablet's daemon to refuse the store", seconds = 360) { runCatching { tablet.daemon.client.listStoreKeys(store) }.isFailure }
                    until("A to have Flour") { "Flour" in a.titles(homeId) }

                    // And it is refused when it asks for an invite again (it keeps asking while it runs)
                    delay(60.seconds)
                    assertFalse("Flour" in tablet.titles(homeId))
                    assertTrue(a.daemon.client.getGroupMembers(groupId).none { it.agentId == tabletId && it.state == "active" })
                    assertEquals(listOf(), a.devices.agentIds().filter { it == tabletId })
                }
            }
        } finally {
            scope.cancel()
            runBlocking {
                listOf(a.daemon, tablet.daemon, b.daemon).forEach { runCatching { it.stop() } }
            }
        }
    }
}
