package uk.veritaslabs.vesta.homegroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Home's hub as the screen shows it: its agent id, and whether it is in the Home's group right now. */
data class HubItem(val agentId: String, val inGroup: Boolean)

/**
 * [uk.veritaslabs.vesta.ui.screens.HubScreen]'s state. With a [hub] recorded the screen shows it and lets it be removed.
 * Without one it shows the enrolment [commands] to run on the always-on machine and, as that machine joins the group, the
 * [candidates] to choose from. [unavailable] means neither could be had: not an admin, or the Home's group isn't ready yet.
 * [failed] says the last choice or removal didn't go through.
 */
data class HubUiState(
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val hub: HubItem? = null,
    val commands: String? = null,
    val candidates: List<String> = emptyList(),
    val failed: Boolean = false,
)

/**
 * Backs the always-on hub screen (docs/adr/ADR-020-syncing-without-overlapping-online-time.md). It reads the hub's state when
 * opened, and again every [pollInterval] while the screen runs [poll], since the admin is typically waiting for the machine to
 * join. The enrolment commands, which carry a fresh group invite, are made once and kept. Without [hubs] (previews) the hub is
 * unavailable.
 */
class HubViewModel(
    private val homeId: String,
    private val hubs: HomeHub?,
    private val pollInterval: Duration = 3.seconds,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    /** Re-reads the hub every [pollInterval] for as long as it is collected; the screen runs it while it is on show. Never returns normally. */
    suspend fun poll(): Nothing {
        while (true) {
            delay(pollInterval)
            refresh()
        }
    }

    /** Chooses [agentId], one of the candidates, as the hub. */
    fun onUseAsHub(agentId: String) {
        viewModelScope.launch {
            val result = hubs?.let { runCatchingNotCancelled { it.useAsHub(homeId, agentId) } }
            _uiState.update { it.copy(failed = result != HubResult.Done) }
            refresh()
        }
    }

    /** Removes the hub from the Home's group and the roster. */
    fun onRemove() {
        viewModelScope.launch {
            val result = hubs?.let { runCatchingNotCancelled { it.removeHub(homeId) } }
            _uiState.update { it.copy(failed = result != HubResult.Done) }
            refresh()
        }
    }

    fun onDismissFailure() {
        _uiState.update { it.copy(failed = false) }
    }

    private suspend fun refresh() {
        val service = hubs ?: return _uiState.update { it.copy(loading = false, unavailable = true) }
        val state = runCatchingNotCancelled { service.state(homeId) } ?: return _uiState.update { it.copy(loading = false) }
        val hub = state.agentId
        if (hub != null) {
            _uiState.update { it.copy(loading = false, unavailable = false, hub = HubItem(hub, state.inGroup), commands = null, candidates = emptyList()) }
            return
        }
        val commands = _uiState.value.commands ?: runCatchingNotCancelled { service.enrolment(homeId) }?.commands
        val candidates = if (commands == null) emptyList() else runCatchingNotCancelled { service.candidates(homeId) }.orEmpty()
        _uiState.update { it.copy(loading = false, unavailable = commands == null, hub = null, commands = commands, candidates = candidates) }
    }

    private suspend fun <T> runCatchingNotCancelled(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
