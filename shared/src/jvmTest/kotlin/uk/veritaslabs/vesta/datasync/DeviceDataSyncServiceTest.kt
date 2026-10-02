package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
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
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.upsertPantryItem
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

class DeviceDataSyncServiceTest {

    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One of the user's devices: its own x0x node and database, with a running data sync. */
    private inner class Device(val agentId: String) {
        val node = network.node(agentId)
        val database: VestaDatabase = openInMemoryDatabase()
        val devices = SyncedDeviceRepository(database)
        val pantries = PantrySources(database)
        val sync = DeviceDataSyncService(node, database, devices, pantries, interval = 50.milliseconds)
        val homes = HomeRepository(database, sync.homeChanges)

        init {
            node.onDirect = { message: X0xDirectMessage -> scope.launch { sync.handle(message) } }
        }

        fun start() {
            scope.launch { sync.run() }
        }

        suspend fun pairedWith(other: Device) = apply { devices.add(other.agentId, other.agentId) }

        suspend fun profileIds() = database.profileQueries.selectAll().awaitAsList().map { it.id }

        suspend fun memberIds(homeId: String) =
            database.homeMemberQueries.selectByHome(homeId).awaitAsList().map { it.memberId }

        suspend fun homeNames() = database.homeQueries.selectAll().awaitAsList().map { it.name }.sorted()

        suspend fun pantryTitles(homeId: String) =
            pantries.forHome(homeId)!!.contents.first().items.map { it.title }.sorted()
    }

    private suspend fun pair(): Pair<Device, Device> {
        val a = Device("agent-a")
        val b = Device("agent-b")
        a.pairedWith(b)
        b.pairedWith(a)
        return a to b
    }

    private suspend fun eventually(check: suspend () -> Boolean) {
        withTimeout(10.seconds) { while (!check()) delay(20.milliseconds) }
    }

    @Test
    fun aHomeCreatedOnOneDeviceAppearsOnTheOther() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()

        // Act
        val id = a.homes.create("Maple Street", HomeShape.Family)

        // Assert
        eventually { b.homeNames() == listOf("Maple Street") }
        assertEquals(id, b.database.homeQueries.selectAll().awaitAsList().single().id)
        scope.cancel()
    }

    @Test
    fun aHomeThatExistedBeforePairingIsSyncedOnceDevicesAreRunning() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.homes.create("Old Home", HomeShape.Flatshare)

        // Act
        a.start()
        b.start()

        // Assert
        eventually { b.homeNames() == listOf("Old Home") }
        scope.cancel()
    }

    @Test
    fun pantryItemsAddedOnEitherDeviceReachBoth() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames().isNotEmpty() }

        // Act
        a.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null) }
        eventually { b.pantryTitles(homeId) == listOf("Whole Milk") }
        b.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Meat", null, "Beef Mince", "500g", barcode = null) }

        // Assert
        eventually { a.pantryTitles(homeId) == listOf("Beef Mince", "Whole Milk") }
        assertEquals(listOf("Beef Mince", "Whole Milk"), b.pantryTitles(homeId))
        scope.cancel()
    }

    @Test
    fun renamingAndDeletingAHomeReachesTheOtherDevice() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val id = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames() == listOf("Maple Street") }

        // Act
        b.homes.update(id, "Oak Avenue", HomeShape.Family)
        eventually { a.homeNames() == listOf("Oak Avenue") }
        a.homes.delete(id)

        // Assert
        eventually { b.homeNames().isEmpty() }
        assertTrue(b.database.automergeDocumentQueries.selectByHome(id).awaitAsList().isEmpty())
        scope.cancel()
    }

    @Test
    fun aDeviceThatIsNotPairedIsIgnored() = runBlocking {
        // Arrange
        val a = Device("agent-a")
        val stranger = Device("agent-stranger")
        stranger.pairedWith(a)
        a.start()
        stranger.start()

        // Act
        stranger.homes.create("Not Yours", HomeShape.Other)
        delay(500.milliseconds)

        // Assert
        assertEquals(emptyList(), a.homeNames())
        scope.cancel()
    }

    @Test
    fun aDeviceThatWasOfflineCatchesUpWhenItReturns() = runBlocking {
        // Arrange
        val (a, b) = pair()
        network.offline += b.agentId
        a.start()
        b.start()

        // Act
        a.homes.create("While Away", HomeShape.Family)
        delay(300.milliseconds)
        network.offline -= b.agentId

        // Assert
        eventually { b.homeNames() == listOf("While Away") }
        scope.cancel()
    }

    @Test
    fun pairedDevicesSettleOnTheOlderDevicesProfileAndMoveTheirMembershipsToIt() = runBlocking {
        // Arrange
        val (a, b) = pair()
        network.offline += b.agentId // b is not reachable until it starts, so it makes its own Profile first
        a.start()
        eventually { a.profileIds().isNotEmpty() }
        val aProfile = a.profileIds().single()
        delay(20.milliseconds) // so b's Profile is unambiguously the younger
        val bHome = b.homes.create("Made On B", HomeShape.Other)
        val bProfileBefore = b.profileIds().single()

        // Act
        network.offline -= b.agentId
        b.start()

        // Assert
        eventually { b.profileIds() == listOf(aProfile) }
        eventually { b.memberIds(bHome) == listOf(aProfile) }
        assertEquals(listOf(aProfile), a.profileIds())
        assertTrue(bProfileBefore != aProfile)
        scope.cancel()
    }
}
