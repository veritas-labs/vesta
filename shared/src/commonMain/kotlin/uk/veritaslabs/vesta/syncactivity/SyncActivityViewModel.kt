package uk.veritaslabs.vesta.syncactivity

import uk.veritaslabs.kits.sync.SyncVerdict
import uk.veritaslabs.kits.sync.PeerActivity
import uk.veritaslabs.kits.sync.SyncEventItem
import uk.veritaslabs.kits.sync.SyncEventFilter
import uk.veritaslabs.kits.sync.SyncActivityRepository
import uk.veritaslabs.kits.sync.SyncSnapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [SyncActivityViewModel]'s state. Nothing is shown while [isLoading] (no empty-state flash). [events] are newest first;
 * [selectedDevice] is the agent id the log is narrowed to; [canLoadOlder] says another page may exist; [syncing] is true from
 * Sync now until the round ends (or the timeout); [syncAvailable] is false where there is no sync service behind the screen.
 */
data class SyncActivityUiState(
    val isLoading: Boolean = true,
    val verdict: SyncVerdict? = null,
    val peers: List<PeerActivity> = emptyList(),
    val events: List<SyncEventItem> = emptyList(),
    val filter: SyncEventFilter = SyncEventFilter.All,
    val selectedDevice: String? = null,
    val canLoadOlder: Boolean = false,
    val syncing: Boolean = false,
    val syncAvailable: Boolean = true,
)

/**
 * Backs the Sync activity screen for [homeId]. It follows the repository's snapshot for the verdict and device list and re-reads
 * the log whenever a round finishes. [syncNow] shows [SyncActivityUiState.syncing] until the round ends or [syncTimeout] passes.
 */
class SyncActivityViewModel(
    private val homeId: String,
    private val repository: SyncActivityRepository,
    private val syncTimeout: Duration = 10.seconds,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _state = MutableStateFlow(SyncActivityUiState(syncAvailable = repository.available))
    val state: StateFlow<SyncActivityUiState> = _state.asStateFlow()

    private var snapshotSeen = false
    private var eventsLoaded = false
    private var lastRoundAt: Long? = null
    private var lastRoundInProgress = false
    private var syncStartedRoundAt: Long? = null
    private var sawRoundRunning = false
    private var timeoutJob: Job? = null

    init {
        viewModelScope.launch {
            repository.snapshot(homeId).collect { onSnapshot(it) }
        }
    }

    /** Shows only the entries of [filter]. */
    fun setFilter(filter: SyncEventFilter) {
        _state.update { it.copy(filter = filter) }
        viewModelScope.launch { reload() }
    }

    /** Narrows the log to [agentId]'s entries; selecting the device already selected clears it. */
    fun selectDevice(agentId: String) {
        _state.update { it.copy(selectedDevice = if (it.selectedDevice == agentId) null else agentId) }
        viewModelScope.launch { reload() }
    }

    /** Appends the next page of older entries. */
    fun loadOlder() {
        val current = _state.value
        val before = current.events.lastOrNull()?.lastAt ?: return
        if (!current.canLoadOlder) return
        viewModelScope.launch {
            val page = read(current.filter, current.selectedDevice, before, PAGE_SIZE) ?: return@launch
            _state.update { it.copy(events = it.events + page, canLoadOlder = page.size >= PAGE_SIZE) }
        }
    }

    /** Asks for a sync round and shows [SyncActivityUiState.syncing] until it ends or [syncTimeout] passes. */
    fun syncNow() {
        syncStartedRoundAt = lastRoundAt
        sawRoundRunning = lastRoundInProgress
        _state.update { it.copy(syncing = true) }
        repository.syncNow()
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(syncTimeout)
            _state.update { it.copy(syncing = false) }
        }
    }

    private suspend fun onSnapshot(snapshot: SyncSnapshot) {
        val firstSnapshot = !snapshotSeen
        snapshotSeen = true
        val roundEnded = lastRoundInProgress && !snapshot.roundInProgress
        if (snapshot.roundInProgress) sawRoundRunning = true
        val advanced = snapshot.lastRoundAt != null && snapshot.lastRoundAt != syncStartedRoundAt
        val syncFinished = _state.value.syncing && !snapshot.roundInProgress && (sawRoundRunning || advanced)
        if (syncFinished) timeoutJob?.cancel()
        lastRoundInProgress = snapshot.roundInProgress
        val roundChanged = snapshot.lastRoundAt != lastRoundAt
        lastRoundAt = snapshot.lastRoundAt
        _state.update {
            it.copy(
                verdict = SyncVerdict.of(snapshot, nowMillis()),
                peers = snapshot.peers,
                syncing = it.syncing && !syncFinished,
                isLoading = !eventsLoaded,
            )
        }
        if (firstSnapshot || roundEnded || roundChanged) reload()
    }

    /** Re-reads the log from the top, keeping as many entries as are already shown (at least one page). */
    private suspend fun reload() {
        val current = _state.value
        val limit = maxOf(PAGE_SIZE, current.events.size)
        val page = read(current.filter, current.selectedDevice, null, limit)
        eventsLoaded = true
        _state.update {
            // A choice made while this read ran wins: its own reload is on the way.
            if (it.filter != current.filter || it.selectedDevice != current.selectedDevice) it
            else it.copy(
                events = page ?: it.events,
                canLoadOlder = page?.let { p -> p.size >= limit } ?: it.canLoadOlder,
                isLoading = !snapshotSeen,
            )
        }
    }

    private suspend fun read(filter: SyncEventFilter, device: String?, before: Long?, limit: Int): List<SyncEventItem>? =
        try {
            repository.events(homeId, filter, device, before, limit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
