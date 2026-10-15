package uk.veritaslabs.vesta.devicesync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicepairing.PairingLink
import uk.veritaslabs.vesta.devicesync.SyncedDevice
import uk.veritaslabs.vesta.x0x.AbstractX0xClient

/**
 * Drives three real x0xd daemons through pairing and removal. Skipped unless `VESTA_X0X_DEVICE_DIRS` lists three
 * daemon data directories (comma separated), e.g. started with `x0x start --name vsync-a` and found under
 * `~/.local/share/x0x-vsync-a`. Run with:
 * `VESTA_X0X_DEVICE_DIRS=$HOME/.local/share/x0x-vsync-a,$HOME/.local/share/x0x-vsync-b,$HOME/.local/share/x0x-vsync-c ./gradlew :shared:jvmTest --tests '*DeviceSyncDaemonIntegrationTest'`
 */
class DeviceSyncDaemonIntegrationTest {

    private class DirClient(private val dir: File) : AbstractX0xClient() {
        override val endpoint: String get() = dir.path

        override suspend fun resolveDaemon(): Pair<String, String?>? {
            val port = dir.resolve("api.port").readText().trim().substringAfterLast(':')
            return "http://127.0.0.1:$port" to dir.resolve("api-token").readText().trim()
        }
    }

    private class Device(val name: String, dir: File) {
        val client = DirClient(dir)
        val database = openInMemoryDatabase()
        val devices = SyncedDeviceRepository(database)
        val service = DeviceSyncService(
            client, database, devices,
            deviceName = { name },
            pollInterval = 1.seconds,
            joinTimeout = 60.seconds,
            resendInterval = 5.seconds,
            reconcileInterval = 3.seconds,
            removeRetryDelay = 3.seconds,
        )

        suspend fun agentId() = client.agent().agentId!!
        suspend fun group() = database.deviceSyncGroupQueries.select().awaitAsOneOrNull()
        suspend fun list() = devices.devices.first()

        suspend fun showPairingLink(): PairingLink {
            val link = PairingLink(agentId = agentId(), expiresAt = System.currentTimeMillis() + 300_000, deviceName = name)
            database.devicePairingLinkQueries.insert(link.agentId, link.agentId, System.currentTimeMillis(), link.expiresAt)
            return link
        }
    }

    private suspend fun until(what: String, condition: suspend () -> Boolean) {
        var waited = 0
        while (!condition()) {
            check(waited++ < 90) { "timed out waiting for $what" }
            delay(1.seconds)
        }
    }

    @Test
    fun pairsDevicesAndRemovesTheCreatorByReplacingTheGroup() {
        val dirs = System.getenv("VESTA_X0X_DEVICE_DIRS")?.split(',')?.map(::File)?.takeIf { it.size == 3 } ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking {
                withTimeout(400.seconds) {
                    // Arrange
                    val a = Device("Laptop", dirs[0])
                    val b = Device("Phone", dirs[1])
                    val c = Device("Tablet", dirs[2])
                    listOf(a, b, c).forEach { scope.launch { it.service.run() } }
                    delay(2.seconds)

                    // Act: A (the first device, so the group's creator) pairs B and then C.
                    assertTrue(a.service.pairWith(b.showPairingLink()).isSuccess, "A pairs B")
                    until("B to record the group") { b.group() != null }
                    assertTrue(a.service.pairWith(c.showPairingLink()).isSuccess, "A pairs C")
                    until("C to record the group") { c.group() != null }
                    until("everyone to know everyone's name") {
                        listOf(a, b, c).all { it.list().size == 2 && it.list().none { d -> d.name == d.agentId.take(8) } }
                    }

                    // Assert: one roster everywhere.
                    assertEquals(setOf(SyncedDevice("Phone", b.agentId()), SyncedDevice("Tablet", c.agentId())), a.list().toSet())
                    assertEquals(setOf(SyncedDevice("Laptop", a.agentId()), SyncedDevice("Tablet", c.agentId())), b.list().toSet())
                    val firstGroup = a.group()
                    assertEquals(firstGroup, b.group())
                    assertEquals(firstGroup, c.group())

                    // Act: B removes A, the creator, which only works by replacing the group.
                    until("B's promotion to admin to take effect") { b.client.getGroupMembers(firstGroup!!).first { it.agentId == b.agentId() }.role == "admin" }
                    assertTrue(b.service.removeDevice(a.agentId()).isSuccess, "B removes A")
                    until("C to move to the new group") { c.group() != firstGroup }

                    // Assert: A is cut off, B and C share a new group.
                    assertEquals(b.group(), c.group())
                    until("B and C to list each other only") {
                        b.list().map { it.agentId } == listOf(c.agentId()) && c.list().map { it.agentId } == listOf(b.agentId())
                    }
                    a.service.reconcile() // A's old group still exists for it; it is simply no longer used
                    assertNull(b.client.getGroupMembers(b.group()!!).firstOrNull { it.agentId == a.agentId() })
                }
            }
        } finally {
            scope.cancel()
        }
    }
}
