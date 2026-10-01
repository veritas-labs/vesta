package uk.veritaslabs.vesta.devicesync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicepairing.PairingLink
import uk.veritaslabs.vesta.ui.screens.SyncedDevice
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

class DeviceSyncServiceTest {

    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 1_000L

    /** One of the user's devices: its own x0x node, database and service, wired so messages reach the service. */
    private inner class Device(val agentId: String, val name: String) {
        val node = network.node(agentId)
        val database: VestaDatabase = openInMemoryDatabase()
        val devices = SyncedDeviceRepository(database) { clock++ }
        val service = DeviceSyncService(
            client = node,
            database = database,
            devices = devices,
            deviceName = { name },
            nowMillis = { clock },
            pollInterval = 1.milliseconds,
            joinTimeout = 500.milliseconds,
            ownViewTimeout = 20.milliseconds,
            resendInterval = 5.milliseconds,
            removeRetryDelay = 1.milliseconds,
        )

        init {
            node.onDirect = { message -> scope.launch { service.handle(message) } }
        }

        /** Shows a pairing link, as the new device does. */
        suspend fun showPairingLink(): PairingLink {
            val link = PairingLink(agentId = agentId, expiresAt = clock + 60_000, deviceName = name)
            database.devicePairingLinkQueries.insert(link.agentId, link.agentId, clock, link.expiresAt)
            return link
        }

        suspend fun group() = database.deviceSyncGroupQueries.select().awaitAsOneOrNull()
        suspend fun list() = devices.devices.first()
    }

    private fun <T> test(block: suspend () -> T) {
        try {
            runBlocking { withTimeout(10_000) { block() } }
        } finally {
            scope.cancel()
        }
    }

    /** Pairs [new] with [owner] and returns once both sides know about each other. */
    private suspend fun pair(owner: Device, new: Device) {
        val link = new.showPairingLink()
        assertTrue(owner.service.pairWith(link).isSuccess)
        // The joiner finishes on its own coroutine; wait for it to record the group.
        while (new.group() == null) kotlinx.coroutines.delay(1)
    }

    @Test
    fun pairingAddsTheNewDeviceToTheOwnersGroupAndList() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")

        // Act
        pair(owner, phone)

        // Assert
        assertEquals(listOf(SyncedDevice("Phone", "agent-b")), owner.list())
        assertEquals("admin", network.groups.getValue(owner.group()!!).members.getValue("agent-b").role)
    }

    @Test
    fun theNewDeviceJoinsTheGroupAndLearnsTheOwnersName() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val joinedName = scope.launch { phone.service.joined.first() }

        // Act
        pair(owner, phone)
        joinedName.join()

        // Assert
        assertEquals(owner.group(), phone.group())
        assertEquals(listOf(SyncedDevice("Laptop", "agent-a")), phone.list())
    }

    @Test
    fun theNewDeviceKeepsTheGroupEvenWhileItsOwnViewOfTheRosterLags() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        network.lagging += "agent-b"

        // Act
        pair(owner, phone)
        phone.service.reconcile()

        // Assert
        assertEquals(owner.group(), phone.group())
        assertEquals(listOf(SyncedDevice("Laptop", "agent-a")), phone.list())
    }

    @Test
    fun anInviteIsIgnoredWhenNoPairingLinkIsShowing() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val link = PairingLink(agentId = "agent-b", expiresAt = clock + 60_000, deviceName = "Phone")

        // Act
        val result = owner.service.pairWith(link)

        // Assert
        assertTrue(result.isFailure)
        assertNull(phone.group())
        assertEquals(emptyList(), owner.list())
    }

    @Test
    fun aMessageThatIsNotVerifiedIsIgnored() = test {
        // Arrange
        val phone = Device("agent-b", "Phone")
        phone.showPairingLink()
        val groupId = network.node("agent-a").createGroup("DeviceSync")
        val invite = DeviceSyncMessage.encode(DeviceSyncMessage.Invite("invite:$groupId", "Laptop"))

        // Act
        phone.service.handle(
            X0xDirectMessage(sender = "agent-a", payload = kotlin.io.encoding.Base64.Default.encode(invite), verified = false),
        )

        // Assert
        assertNull(phone.group())
    }

    @Test
    fun pairingFailsWhenTheNewDeviceNeverJoins() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val link = phone.showPairingLink()
        network.offline += "agent-b"

        // Act
        val result = owner.service.pairWith(link)

        // Assert
        assertTrue(result.isFailure)
        assertEquals(emptyList(), owner.list())
    }

    @Test
    fun pairingReportsEveryStepAsDoneInOrder() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val link = phone.showPairingLink()
        val events = mutableListOf<Pair<PairingStep, PairingStepStatus>>()

        // Act
        val result = owner.service.pairWith(link) { step, status -> events += step to status }

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(PairingStep.entries, events.filter { it.second == PairingStepStatus.Done }.map { it.first })
        assertTrue(events.none { it.second == PairingStepStatus.Failed })
    }

    @Test
    fun pairingReportsAwaitingTheJoinAsFailedWhenTheNewDeviceNeverJoins() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val link = phone.showPairingLink()
        network.offline += "agent-b"
        val events = mutableListOf<Pair<PairingStep, PairingStepStatus>>()

        // Act
        val result = owner.service.pairWith(link) { step, status -> events += step to status }

        // Assert
        assertTrue(result.isFailure)
        assertEquals(PairingStep.AwaitJoin to PairingStepStatus.Failed, events.last())
        assertTrue(events.none { it.first == PairingStep.GrantAdmin })
    }

    @Test
    fun reconcileAddsMembersTheOwnerDidNotPairAndGreetsThem() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        pair(owner, phone)
        val tablet = Device("agent-c", "Tablet")
        pair(phone, tablet)

        // Act
        owner.service.reconcile()
        while (owner.list().none { it.name == "Tablet" }) kotlinx.coroutines.delay(1) // the tablet's greeting back

        // Assert
        assertEquals(setOf("agent-b", "agent-c"), owner.list().map { it.agentId }.toSet())
        assertEquals("Tablet", owner.list().first { it.agentId == "agent-c" }.name)
    }

    @Test
    fun removingADeviceTakesItOutOfTheGroupAndTheList() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val tablet = Device("agent-c", "Tablet")
        pair(owner, phone)
        pair(owner, tablet)

        // Act
        val result = owner.service.removeDevice("agent-b")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(listOf("agent-c"), owner.list().map { it.agentId })
        assertEquals(setOf("agent-a", "agent-c"), network.groups.getValue(owner.group()!!).members.keys)
    }

    @Test
    fun aRemovedDeviceForgetsTheGroupAndItsList() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        pair(owner, phone)
        owner.service.removeDevice("agent-b")

        // Act
        phone.service.reconcile()

        // Assert
        assertNull(phone.group())
        assertEquals(emptyList(), phone.list())
    }

    @Test
    fun removingTheGroupCreatorMovesTheOtherDevicesToANewGroup() = test {
        // Arrange
        val creator = Device("agent-a", "Lost phone")
        val laptop = Device("agent-b", "Laptop")
        val tablet = Device("agent-c", "Tablet")
        pair(creator, laptop)
        pair(creator, tablet)
        val oldGroup = laptop.group()!!

        // Act
        val result = laptop.service.removeDevice("agent-a")
        while (tablet.group() == oldGroup) kotlinx.coroutines.delay(1)

        // Assert
        assertTrue(result.isSuccess)
        val newGroup = laptop.group()!!
        assertEquals(newGroup, tablet.group())
        assertTrue(newGroup != oldGroup)
        assertEquals(setOf("agent-b", "agent-c"), network.groups.getValue(newGroup).members.keys)
        assertEquals("admin", network.groups.getValue(newGroup).members.getValue("agent-c").role)
        assertEquals(listOf("agent-c"), laptop.list().map { it.agentId })
        assertEquals(listOf("agent-b"), tablet.list().map { it.agentId })
    }

    @Test
    fun aDeviceThatIsOfflineWhenTheGroupIsReplacedIsDroppedFromTheList() = test {
        // Arrange
        val creator = Device("agent-a", "Lost phone")
        val laptop = Device("agent-b", "Laptop")
        val tablet = Device("agent-c", "Tablet")
        pair(creator, laptop)
        pair(creator, tablet)
        network.offline += "agent-c"

        // Act
        val result = laptop.service.removeDevice("agent-a")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), laptop.list())
        assertEquals(setOf("agent-b"), network.groups.getValue(laptop.group()!!).members.keys)
    }
}
