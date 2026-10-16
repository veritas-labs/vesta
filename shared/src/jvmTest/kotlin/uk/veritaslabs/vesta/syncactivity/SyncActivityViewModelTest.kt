package uk.veritaslabs.vesta.syncactivity

import uk.veritaslabs.kits.sync.SyncEventKind
import uk.veritaslabs.kits.sync.SyncModule
import uk.veritaslabs.kits.sync.SyncEventFilter
import uk.veritaslabs.kits.sync.SyncEventItem
import uk.veritaslabs.kits.sync.PeerActivity
import uk.veritaslabs.kits.sync.SyncSnapshot
import uk.veritaslabs.kits.sync.SyncVerdict
import uk.veritaslabs.kits.sync.SyncActivityRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private class FakeSyncActivityRepository(var log: List<SyncEventItem> = emptyList()) : SyncActivityRepository {
    override val available = true
    val snapshots = MutableSharedFlow<SyncSnapshot>(replay = 1)
    var syncNowCalls = 0
    var gate: CompletableDeferred<Unit>? = null
    val requests = mutableListOf<List<Any?>>()

    override fun snapshot(homeId: String): Flow<SyncSnapshot> = snapshots

    override suspend fun events(homeId: String, filter: SyncEventFilter, peerAgent: String?, beforeLastAt: Long?, limit: Int): List<SyncEventItem> {
        requests += listOf(filter, peerAgent, beforeLastAt, limit)
        gate?.await()
        return log.filter { item ->
            when (filter) {
                SyncEventFilter.All -> true
                SyncEventFilter.Received -> item.kind == SyncEventKind.Received
                SyncEventFilter.Sent -> item.kind == SyncEventKind.Sent
                SyncEventFilter.Problems -> item.kind == SyncEventKind.Failed
            }
        }
            .filter { peerAgent == null || it.peerAgent == peerAgent }
            .filter { beforeLastAt == null || it.lastAt < beforeLastAt }
            .sortedByDescending { it.lastAt }
            .take(limit)
    }

    override fun syncNow() {
        syncNowCalls++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyncActivityViewModelTest {

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun useMainDispatcher(scheduler: TestCoroutineScheduler) = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    private fun snapshot(roundInProgress: Boolean = false, lastRoundAt: Long? = 100L) =
        SyncSnapshot(takenAt = 0L, hasOtherDevices = true, roundInProgress = roundInProgress, lastRoundAt = lastRoundAt)

    private fun event(id: Long, kind: SyncEventKind = SyncEventKind.Received, peer: String? = "agent-a") =
        SyncEventItem(id = id, at = id, lastAt = id, kind = kind, module = SyncModule(VestaSyncModules.Tasks), peerAgent = peer)

    @Test
    fun itIsLoadingUntilTheFirstSnapshotAndLogHaveArrived() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository(listOf(event(1)))
        repository.gate = CompletableDeferred()

        // Act
        val viewModel = SyncActivityViewModel("home-1", repository)
        val before = viewModel.state.value
        repository.snapshots.emit(snapshot())
        val afterSnapshot = viewModel.state.value
        repository.gate?.complete(Unit)
        val after = viewModel.state.value

        // Assert
        assertTrue(before.isLoading)
        assertTrue(afterSnapshot.isLoading)
        assertFalse(after.isLoading)
        assertEquals(listOf(event(1)), after.events)
    }

    @Test
    fun theStateCarriesTheVerdictAndPeers() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository()
        val peer = PeerActivity(agentId = "agent-a", name = "Sam's phone")
        repository.snapshots.emit(snapshot().copy(peers = listOf(peer)))

        // Act
        val viewModel = SyncActivityViewModel("home-1", repository, nowMillis = { 0L })

        // Assert
        val state = viewModel.state.value
        assertEquals(listOf(peer), state.peers)
        assertEquals(SyncVerdict.WaitingForOthers, state.verdict)
        assertTrue(state.events.isEmpty())
    }

    @Test
    fun theFilterNarrowsTheLog() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository(listOf(event(1, SyncEventKind.Received), event(2, SyncEventKind.Sent), event(3, SyncEventKind.Failed)))
        repository.snapshots.emit(snapshot())
        val viewModel = SyncActivityViewModel("home-1", repository)

        // Act
        viewModel.setFilter(SyncEventFilter.Problems)

        // Assert
        assertEquals(SyncEventFilter.Problems, viewModel.state.value.filter)
        assertEquals(listOf(3L), viewModel.state.value.events.map { it.id })
    }

    @Test
    fun selectingADeviceNarrowsTheLogAndSelectingItAgainClearsIt() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository(listOf(event(1, peer = "agent-a"), event(2, peer = "agent-b"), event(3, peer = "agent-a")))
        repository.snapshots.emit(snapshot())
        val viewModel = SyncActivityViewModel("home-1", repository)

        // Act
        viewModel.selectDevice("agent-a")
        val selected = viewModel.state.value
        viewModel.selectDevice("agent-a")
        val cleared = viewModel.state.value

        // Assert
        assertEquals("agent-a", selected.selectedDevice)
        assertEquals(listOf(3L, 1L), selected.events.map { it.id })
        assertNull(cleared.selectedDevice)
        assertEquals(listOf(3L, 2L, 1L), cleared.events.map { it.id })
    }

    @Test
    fun theLogLoadsInPagesOfFiftyAndOlderOnesAreAppended() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository((1L..120L).map { event(it) })
        repository.snapshots.emit(snapshot())
        val viewModel = SyncActivityViewModel("home-1", repository)
        val first = viewModel.state.value

        // Act
        viewModel.loadOlder()
        val second = viewModel.state.value
        viewModel.loadOlder()
        val third = viewModel.state.value

        // Assert
        assertEquals(50, first.events.size)
        assertTrue(first.canLoadOlder)
        assertEquals(100, second.events.size)
        assertTrue(second.canLoadOlder)
        assertEquals(120, third.events.size)
        assertFalse(third.canLoadOlder)
        assertEquals((120L downTo 1L).toList(), third.events.map { it.id })
        assertEquals(listOf(SyncEventFilter.All, null, 71L, 50), repository.requests[1])
    }

    @Test
    fun aShortLogCannotLoadOlder() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository((1L..3L).map { event(it) })
        repository.snapshots.emit(snapshot())

        // Act
        val viewModel = SyncActivityViewModel("home-1", repository)

        // Assert
        assertFalse(viewModel.state.value.canLoadOlder)
    }

    @Test
    fun aFinishedRoundRefreshesTheLog() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository(listOf(event(1)))
        repository.snapshots.emit(snapshot(lastRoundAt = 100L))
        val viewModel = SyncActivityViewModel("home-1", repository)

        // Act
        repository.log = listOf(event(1), event(2))
        repository.snapshots.emit(snapshot(roundInProgress = true, lastRoundAt = 100L))
        repository.snapshots.emit(snapshot(roundInProgress = false, lastRoundAt = 200L))

        // Assert
        assertEquals(listOf(2L, 1L), viewModel.state.value.events.map { it.id })
    }

    @Test
    fun syncNowCallsTheRepositoryOnceAndShowsProgressUntilTheRoundEnds() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository()
        repository.snapshots.emit(snapshot(lastRoundAt = 100L))
        val viewModel = SyncActivityViewModel("home-1", repository)

        // Act
        viewModel.syncNow()
        val pressed = viewModel.state.value
        repository.snapshots.emit(snapshot(roundInProgress = true, lastRoundAt = 100L))
        val running = viewModel.state.value
        repository.snapshots.emit(snapshot(roundInProgress = false, lastRoundAt = 200L))
        val finished = viewModel.state.value

        // Assert
        assertEquals(1, repository.syncNowCalls)
        assertTrue(pressed.syncing)
        assertTrue(running.syncing)
        assertFalse(finished.syncing)
    }

    @Test
    fun syncNowStopsShowingProgressAfterTheTimeoutWhenNoRoundRuns() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository()
        repository.snapshots.emit(snapshot(lastRoundAt = 100L))
        val viewModel = SyncActivityViewModel("home-1", repository, syncTimeout = 10.seconds)

        // Act
        viewModel.syncNow()
        advanceTimeBy(9.seconds)
        val beforeTimeout = viewModel.state.value.syncing
        advanceTimeBy(2.seconds)
        val afterTimeout = viewModel.state.value.syncing

        // Assert
        assertTrue(beforeTimeout)
        assertFalse(afterTimeout)
    }

    @Test
    fun aSnapshotBeforeTheRoundStartsDoesNotEndSyncNow() = runTest {
        // Arrange
        useMainDispatcher(testScheduler)
        val repository = FakeSyncActivityRepository()
        repository.snapshots.emit(snapshot(lastRoundAt = 100L))
        val viewModel = SyncActivityViewModel("home-1", repository)

        // Act
        viewModel.syncNow()
        repository.snapshots.emit(snapshot(roundInProgress = false, lastRoundAt = 100L))

        // Assert
        assertTrue(viewModel.state.value.syncing)
    }
}
