package uk.veritaslabs.vesta.devicesync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import java.util.concurrent.CopyOnWriteArrayList
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
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.vesta.devicepairing.PairingLink
import uk.veritaslabs.vesta.devicesync.SyncedDevice
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

class DeviceSyncServiceTest {

    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 1_000L

    /** One of the user's devices: its own x0x node, database and service, wired so messages reach the service. */
    private inner class Device(val agentId: String, val name: String) {
        val node = network.node(agentId)
        val database: KitsDatabase = openInMemoryDatabase()
        val devices = SyncedDeviceRepository(database) { clock++ }

        /** What this device reported to its listener (docs/plans/unpaired-device-gap-plan.md, slice 3). */
        val unpaired = CopyOnWriteArrayList<String>()
        val paired = CopyOnWriteArrayList<String>()
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
            onDeviceUnpaired = { unpaired += it },
            onDevicePaired = { paired += it },
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

    @Test
    fun removingADeviceReportsItUnpaired() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val tablet = Device("agent-c", "Tablet")
        pair(owner, phone)
        pair(owner, tablet)

        // Act
        owner.service.removeDevice("agent-b")

        // Assert
        assertEquals(listOf("agent-b"), owner.unpaired.toList())
        assertEquals(emptyList(), tablet.unpaired.toList())
    }

    @Test
    fun removingADeviceThisOneHasNoGroupForIsStillReportedUnpaired() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        owner.devices.add("Phone", "agent-b")

        // Act
        val result = owner.service.removeDevice("agent-b")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(listOf("agent-b"), owner.unpaired.toList())
    }

    @Test
    fun pairingADeviceReportsItPairedOnBothSides() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")

        // Act
        pair(owner, phone)
        while (phone.paired.isEmpty()) kotlinx.coroutines.delay(1)

        // Assert
        assertEquals(listOf("agent-b"), owner.paired.toList())
        assertEquals(listOf("agent-a"), phone.paired.toList())
    }

    @Test
    fun aDeviceOnlySeenInTheGroupIsNotReportedPaired() = test {
        // Arrange: the owner pairs the phone and then the tablet, so the phone has not met the tablet
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val tablet = Device("agent-c", "Tablet")
        pair(owner, phone)
        pair(owner, tablet)
        while (phone.paired.isEmpty()) kotlinx.coroutines.delay(1)
        val before = phone.paired.toList()

        // Act: the phone finds the tablet by looking at the group, which is not the user pairing it
        phone.service.reconcile()

        // Assert
        assertTrue("agent-c" in phone.list().map { it.agentId })
        assertEquals(before, phone.paired.toList())
    }

    @Test
    fun aListenerThatFailsDoesNotFailThePairing() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val failing = DeviceSyncService(
            client = owner.node, database = owner.database, devices = owner.devices, deviceName = { "Laptop" }, nowMillis = { clock },
            pollInterval = 1.milliseconds, joinTimeout = 500.milliseconds, ownViewTimeout = 20.milliseconds, resendInterval = 5.milliseconds,
            onDevicePaired = { error("the listener broke") },
        )

        // Act
        val result = failing.pairWith(phone.showPairingLink())

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(listOf("agent-b"), owner.list().map { it.agentId })
    }

    @Test
    fun aGreetingFromADeviceThatWasNotKnownIsReportedPairedButARepeatIsNot() = test {
        // Arrange: the phone and the tablet are each paired with the owner but have not met
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val tablet = Device("agent-c", "Tablet")
        pair(owner, phone)
        pair(owner, tablet)

        // Act: the phone finds the tablet in the group and greets it, twice
        phone.service.reconcile()
        while (tablet.paired.none { it == "agent-b" }) kotlinx.coroutines.delay(1)
        phone.service.reconcile()
        kotlinx.coroutines.delay(50)

        // Assert
        assertEquals(1, tablet.paired.count { it == "agent-b" })
    }

    @Test
    fun aPairingInviteIsMadeForTheNewDeviceAndLapsesInFifteenMinutes() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")

        // Act
        pair(owner, phone)

        // Assert
        val invite = network.minted.single { it.mintedBy == "agent-a" }
        assertEquals("agent-b", invite.intendedJoiner)
        assertEquals(900L, invite.expirySecs)
    }

    @Test
    fun movingTheDevicesToANewGroupMakesOneInviteForEachOfThem() = test {
        // Arrange
        val owner = Device("agent-a", "Laptop")
        val phone = Device("agent-b", "Phone")
        val tablet = Device("agent-c", "Tablet")
        pair(owner, phone)
        pair(owner, tablet)
        val before = network.minted.size

        // Act: the phone and the tablet are moved when the group's creator is removed by a device that is not the creator
        phone.service.removeDevice("agent-a")

        // Assert
        val rotation = network.minted.drop(before).filter { it.mintedBy == "agent-b" }
        assertEquals(setOf("agent-c"), rotation.mapNotNull { it.intendedJoiner }.toSet())
        assertTrue(rotation.all { it.expirySecs == 900L })
    }
}
