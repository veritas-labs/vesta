@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.kits.docs.ChangesWaiting

import uk.veritaslabs.vesta.syncactivity.VestaSyncModules
import uk.veritaslabs.vesta.getDeviceName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.kits.x0x.testing.FakeX0xNetwork
import uk.veritaslabs.kits.sync.ProblemReason
import uk.veritaslabs.kits.sync.SyncEventFilter
import uk.veritaslabs.kits.sync.SyncEventKind
import uk.veritaslabs.kits.sync.SyncModule
import uk.veritaslabs.kits.sync.SyncSnapshot
import uk.veritaslabs.kits.sync.SyncVerdict
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.kits.x0x.X0xAgent
import uk.veritaslabs.kits.x0x.X0xClient

import uk.veritaslabs.kits.spaces.SpaceRosters
class SyncActivityServiceTest {

    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val minute = 60_000L

    private suspend fun eventually(check: suspend () -> Boolean) {
        withTimeout(10.seconds) { while (!check()) delay(20.milliseconds) }
    }

    private suspend fun ActivityDevice.snapshot(homeId: String): SyncSnapshot = sync.syncActivity.snapshot(homeId).first()

    private suspend fun ActivityDevice.verdict(homeId: String) = SyncVerdict.of(snapshot(homeId), System.currentTimeMillis())

    /** Waits until a round has finished and the verdict is [expected]. */
    private suspend fun ActivityDevice.eventuallyVerdict(homeId: String, expected: (SyncVerdict) -> Boolean) = eventually {
        val snapshot = snapshot(homeId)
        snapshot.lastRoundAt != null && expected(SyncVerdict.of(snapshot, System.currentTimeMillis()))
    }

    @Test
    fun theSnapshotNamesTheHomesDevicesWithPresenceAndLastSyncedTimes() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a")
        val b = ActivityDevice(network, scope, "agent-b")
        a.pairedWith(b)
        b.pairedWith(a)
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        eventually { a.snapshot(homeId).peers.any { it.agentId == "agent-b" && it.online && it.lastSyncedMsAgo != null } }
        val snapshot = a.snapshot(homeId)

        // Assert
        val self = snapshot.peers.single { it.isYou }
        val other = snapshot.peers.single { it.agentId == "agent-b" }
        assertEquals("agent-a", self.agentId)
        assertTrue(self.online)
        assertTrue(other.isOwnDevice)
        assertFalse(other.isHub)
        assertNull(other.ownerName)
        assertTrue(snapshot.hasOtherDevices)
        assertEquals(other.name, snapshot.lastSyncedPeerName)
        assertTrue(snapshot.daemonReachable)
        scope.cancel()
    }

    @Test
    fun aHubIsListedAsAHubAndIsOfflineUntilPresenceSaysOtherwise() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a")
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        SpaceRosters(a.database, a.profiles, deviceName = ::getDeviceName).addHub(homeId, "agent-hub")

        // Act
        val before = a.snapshot(homeId)
        a.start()
        eventually { // presence is only heard once the service is listening for it
            network.comeOnline("agent-hub")
            a.snapshot(homeId).peers.single { it.agentId == "agent-hub" }.online
        }
        val after = a.snapshot(homeId)

        // Assert
        assertTrue(before.peers.single { it.agentId == "agent-hub" }.isHub)
        assertFalse(before.peers.single { it.agentId == "agent-hub" }.online)
        assertTrue(before.hasOtherDevices)
        assertTrue(after.peers.single { it.agentId == "agent-hub" }.online)
        scope.cancel()
    }

    @Test
    fun aHomeWithOnlyThisDeviceHasNoOtherDevices() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a")
        a.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        a.eventuallyVerdict(homeId) { it == SyncVerdict.OnlyYou }
        val snapshot = a.snapshot(homeId)

        // Assert
        assertFalse(snapshot.hasOtherDevices)
        assertEquals(listOf("agent-a"), snapshot.peers.map { it.agentId })
        scope.cancel()
    }

    @Test
    fun theSnapshotSaysARoundIsInProgressWhileItRuns() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a")
        val b = ActivityDevice(network, scope, "agent-b")
        a.pairedWith(b)
        network.hanging += "agent-b"
        network.hangFor = 400.milliseconds
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        a.start()
        eventually { a.snapshot(homeId).roundInProgress }
        val during = a.snapshot(homeId)
        eventually { !a.snapshot(homeId).roundInProgress && a.snapshot(homeId).lastRoundAt != null }
        val after = a.snapshot(homeId)

        // Assert
        assertEquals(SyncVerdict.Syncing, SyncVerdict.of(during, during.takenAt))
        assertFalse(after.roundInProgress)
        assertTrue(after.lastRoundHadFailures)
        scope.cancel()
    }

    @Test
    fun theVerdictIsUpToDateAfterAGoodRoundWithAPeer() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a")
        val b = ActivityDevice(network, scope, "agent-b")
        a.pairedWith(b)
        b.pairedWith(a)
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        a.addToPantry(homeId, "Whole Milk")
        a.eventuallyVerdict(homeId) { it is SyncVerdict.UpToDate }

        // Assert
        val verdict = a.verdict(homeId)
        assertTrue(verdict is SyncVerdict.UpToDate)
        assertEquals(a.snapshot(homeId).lastSyncedPeerName, verdict.withName)
        scope.cancel()
    }

    @Test
    fun theVerdictIsWaitingForOthersWhenTheyAreAwayAndTheChangesAreInTheMailbox() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a")
        val b = ActivityDevice(network, scope, "agent-b")
        a.pairedWith(b)
        network.hanging += "agent-b"
        network.hangFor = 20.milliseconds
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        a.addToPantry(homeId, "Whole Milk")

        // Act
        a.start()

        // Assert
        a.eventuallyVerdict(homeId) { it == SyncVerdict.WaitingForOthers }
        scope.cancel()
    }

    @Test
    fun theVerdictIsChangesWaitingWhenTheyAreAwayAndNothingIsInTheMailbox() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a", mailbox = false)
        val b = ActivityDevice(network, scope, "agent-b")
        a.pairedWith(b)
        network.hanging += "agent-b"
        network.hangFor = 20.milliseconds
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        a.addToPantry(homeId, "Whole Milk")

        // Act
        a.start()

        // Assert
        a.eventuallyVerdict(homeId) { it == SyncVerdict.ChangesWaiting }
        scope.cancel()
    }

    @Test
    fun theVerdictIsAProblemWhenTheDaemonDoesNotAnswer() = runBlocking {
        // Arrange
        val a = ActivityDevice(
            network, scope, "agent-a",
            wrapClient = { node ->
                object : X0xClient by node {
                    override suspend fun agent(): X0xAgent = error("the daemon is down")
                }
            },
        )
        a.pairedWith(ActivityDevice(network, scope, "agent-b"))
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        a.start()

        // Assert
        a.eventuallyVerdict(homeId) { it == SyncVerdict.Problem(ProblemReason.DaemonUnreachable) }
        scope.cancel()
    }

    @Test
    fun aMembershipMessageStillWaitingToBeDeliveredShowsWhoItIsForAndHowLong() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a")
        a.pairedWith(ActivityDevice(network, scope, "agent-b"))
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        val twoHoursAgo = System.currentTimeMillis() - 2.hours.inWholeMilliseconds
        a.database.pendingMessageQueries.upsert("agent-b", homeId, "removed", byteArrayOf(1), twoHoursAgo)
        a.database.pendingMessageQueries.upsert("agent-b", "another-home", "removed", byteArrayOf(1), twoHoursAgo - 1.hours.inWholeMilliseconds)

        // Act
        val snapshot = a.snapshot(homeId)

        // Assert
        val stalled = assertNotNull(snapshot.stalledMessageAgeMs)
        assertTrue(stalled in 2.hours.inWholeMilliseconds..(2.hours.inWholeMilliseconds + 60_000))
        assertEquals(stalled, snapshot.peers.single { it.agentId == "agent-b" }.retryingSinceMsAgo)
        assertEquals(SyncVerdict.Problem(ProblemReason.MessageStalled), SyncVerdict.of(snapshot, snapshot.takenAt).takeIf { snapshot.daemonReachable })
        scope.cancel()
    }

    @Test
    fun theSnapshotFlowEmitsAgainOnItsRefreshInterval() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a", activityRefresh = 50.milliseconds)
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        val snapshots = withTimeout(5.seconds) { a.sync.syncActivity.snapshot(homeId).take(3).toList() }

        // Assert
        assertEquals(3, snapshots.size)
        assertTrue(snapshots[2].takenAt >= snapshots[0].takenAt)
        scope.cancel()
    }

    @Test
    fun theSnapshotFlowEmitsAgainWhenARoundBeginsAndEnds() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a", activityRefresh = 1.hours)
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        val collecting = scope.async { a.sync.syncActivity.snapshot(homeId).transformWhile { emit(it); it.lastRoundAt == null }.toList() }
        delay(100.milliseconds)

        // Act
        a.start()
        val snapshots = withTimeout(5.seconds) { collecting.await() }

        // Assert
        assertNull(snapshots.first().lastRoundAt)
        assertTrue(snapshots.size > 1)
        assertNotNull(snapshots.last().lastRoundAt)
        scope.cancel()
    }

    @Test
    fun syncNowStartsAnotherRound() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a", interval = 1.hours)
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        a.start()
        eventually { a.snapshot(homeId).lastRoundAt != null }
        val first = a.snapshot(homeId).lastRoundAt!!
        delay(20.milliseconds)

        // Act
        a.sync.syncActivity.syncNow()

        // Assert
        eventually { (a.snapshot(homeId).lastRoundAt ?: 0L) > first }
        scope.cancel()
    }

    // The log: seeded through the recorder with a controlled clock, then read back.

    private var clock = 10_000_000L

    private suspend fun seeded(): Pair<ActivityDevice, String> {
        val a = ActivityDevice(network, scope, "agent-a", nowMillis = { clock })
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        val recorder = a.recorder
        recorder.received(homeId, VestaSyncModules.Pantry, "peer-1") // oldest
        clock += 6 * minute
        recorder.sent(homeId, VestaSyncModules.Shopping, "peer-2")
        clock += 6 * minute
        recorder.failed(homeId, VestaSyncModules.Tasks, "peer-1", "unreachable")
        clock += 6 * minute
        recorder.mailbox(homeId, incoming = true, peer = null)
        clock += 6 * minute
        recorder.membership(homeId, "peer-2", "joined")
        clock += 6 * minute
        recorder.mailbox(homeId, incoming = false, peer = null)
        clock += 6 * minute
        recorder.received(homeId, VestaSyncModules.Calendar, "peer-2") // newest
        recorder.flush()
        return a to homeId
    }

    @Test
    fun allEventsComeNewestFirstWithTheirKindsAndModules() = runBlocking {
        // Arrange
        val (a, homeId) = seeded()

        // Act
        val events = a.sync.syncActivity.events(homeId, SyncEventFilter.All, null, null, 50)

        // Assert
        assertEquals(
            listOf(
                SyncEventKind.Received to SyncModule(VestaSyncModules.Calendar),
                SyncEventKind.MailboxOut to SyncModule.Mailbox,
                SyncEventKind.Membership to SyncModule.Members,
                SyncEventKind.MailboxIn to SyncModule.Mailbox,
                SyncEventKind.Failed to SyncModule(VestaSyncModules.Tasks),
                SyncEventKind.Sent to SyncModule(VestaSyncModules.Shopping),
                SyncEventKind.Received to SyncModule(VestaSyncModules.Pantry),
            ),
            events.map { it.kind to it.module },
        )
        assertEquals("joined", events.single { it.kind == SyncEventKind.Membership }.detail)
        assertNull(events.single { it.kind == SyncEventKind.MailboxIn }.peerAgent)
        scope.cancel()
    }

    @Test
    fun aModuleThisVersionDoesNotKnowIsStillListedUnderItsId() = runBlocking {
        // Arrange
        val (a, homeId) = seeded()
        a.recorder.received(homeId, "gardening", "peer-9")
        a.recorder.flush()

        // Act
        val events = a.sync.syncActivity.events(homeId, SyncEventFilter.All, "peer-9", null, 50)

        // Assert
        assertEquals(listOf(SyncModule("gardening")), events.map { it.module })
        scope.cancel()
    }

    @Test
    fun theFiltersKeepTheirKindsOnly() = runBlocking {
        // Arrange
        val (a, homeId) = seeded()
        val repository = a.sync.syncActivity

        // Act
        val problems = repository.events(homeId, SyncEventFilter.Problems, null, null, 50)
        val received = repository.events(homeId, SyncEventFilter.Received, null, null, 50)
        val sent = repository.events(homeId, SyncEventFilter.Sent, null, null, 50)

        // Assert
        assertEquals(listOf(SyncEventKind.Failed), problems.map { it.kind })
        assertEquals(listOf(SyncEventKind.Received, SyncEventKind.MailboxIn, SyncEventKind.Received), received.map { it.kind })
        assertEquals(listOf(SyncEventKind.MailboxOut, SyncEventKind.Sent), sent.map { it.kind })
        scope.cancel()
    }

    @Test
    fun aDeviceFilterKeepsThatDevicesEntriesAndStillFillsAPage() = runBlocking {
        // Arrange
        val (a, homeId) = seeded()
        val repository = a.sync.syncActivity

        // Act
        val all = repository.events(homeId, SyncEventFilter.All, "peer-1", null, 50)
        val firstOnly = repository.events(homeId, SyncEventFilter.All, "peer-1", null, 1)
        val received = repository.events(homeId, SyncEventFilter.Received, "peer-2", null, 50)

        // Assert
        assertEquals(listOf(SyncModule(VestaSyncModules.Tasks), SyncModule(VestaSyncModules.Pantry)), all.map { it.module })
        assertEquals(listOf(SyncModule(VestaSyncModules.Tasks)), firstOnly.map { it.module })
        assertEquals(listOf(SyncModule(VestaSyncModules.Calendar)), received.map { it.module })
        scope.cancel()
    }

    @Test
    fun pagesContinueAfterTheLastEntryOfThePreviousOne() = runBlocking {
        // Arrange
        val (a, homeId) = seeded()
        val repository = a.sync.syncActivity

        // Act
        val first = repository.events(homeId, SyncEventFilter.All, null, null, 3)
        val second = repository.events(homeId, SyncEventFilter.All, null, first.last().lastAt, 3)
        val third = repository.events(homeId, SyncEventFilter.All, null, second.last().lastAt, 3)
        val received = repository.events(homeId, SyncEventFilter.Received, null, null, 2)
        val receivedMore = repository.events(homeId, SyncEventFilter.Received, null, received.last().lastAt, 2)

        // Assert
        assertEquals(listOf(3, 3, 1), listOf(first.size, second.size, third.size))
        assertEquals(7, (first + second + third).map { it.id }.toSet().size)
        assertEquals(listOf(SyncModule(VestaSyncModules.Calendar), SyncModule.Mailbox), received.map { it.module })
        assertEquals(listOf(SyncModule(VestaSyncModules.Pantry)), receivedMore.map { it.module })
        scope.cancel()
    }

    @Test
    fun anotherHomesEventsAreNotShown() = runBlocking {
        // Arrange
        val (a, _) = seeded()
        val other = a.homes.create("Second Home", HomeShape.Family.storageValue)

        // Act
        val events = a.sync.syncActivity.events(other, SyncEventFilter.All, null, null, 50)

        // Assert
        assertTrue(events.isEmpty())
        scope.cancel()
    }
}
