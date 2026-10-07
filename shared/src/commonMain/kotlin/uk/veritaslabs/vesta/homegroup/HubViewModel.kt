package uk.veritaslabs.vesta.homegroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.x0x.InviteLifetime

/**
 * One of the Home's hubs as the screen shows it: its agent id, whether it is in the Home's group right now, and whether it is
 * also one of the user's devices ([isDevice]), which changes what removing it means.
 */
data class HubItem(val agentId: String, val inGroup: Boolean, val isDevice: Boolean = false)

/**
 * [uk.veritaslabs.vesta.ui.screens.HubScreen]'s state. The [hubs] recorded are shown, each removable. With none, or once the admin
 * asks to [adding] another, the screen shows the enrolment [commands] to run on the always-on machine and, as that machine
 * joins the group, the [candidates] to choose from, and (where the platform offers it) the user's own [ownDevices] that can be
 * hubs. [unavailable] means no hub is recorded and no commands could be had: not an admin, or the Home's group isn't ready yet.
 * [failed] says the last choice or removal didn't go through.
 */
data class HubUiState(
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val hubs: List<HubItem> = emptyList(),
    val adding: Boolean = false,
    val commands: String? = null,
    val candidates: List<String> = emptyList(),
    val ownDevices: List<String> = emptyList(),
    val failed: Boolean = false,
)

/**
 * Backs the always-on hub screen (docs/adr/ADR-020-syncing-without-overlapping-online-time.md). It reads the hubs when opened,
 * and again every [pollInterval] while the screen runs [poll], since the admin is typically waiting for the machine to join.
 * The enrolment commands, which carry a fresh group invite, are made once, only when they are shown, and kept. Without [hubs]
 * (previews) the hub is unavailable. [offerOwnDevice] says this platform can offer the user's own device as a hub: a desktop
 * whose x0x daemon stays up when Vesta is closed (a phone's embedded daemon does not).
 */
class HubViewModel(
    private val homeId: String,
    private val hubs: HomeHub?,
    private val pollInterval: Duration = 3.seconds,
    private val offerOwnDevice: Boolean = false,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private var commandsMadeAt = 0L

    private val _uiState = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    /** Re-reads the hubs every [pollInterval] for as long as it is collected; the screen runs it while it is on show. Never returns normally. */
    suspend fun poll(): Nothing {
        while (true) {
            delay(pollInterval)
            refresh()
        }
    }

    /** Shows the enrolment commands and candidates beside the hubs already recorded. */
    fun onAddAnother() {
        _uiState.update { it.copy(adding = true) }
        viewModelScope.launch { refresh() }
    }

    /** Chooses [agentId], one of the candidates, as the hub. */
    fun onUseAsHub(agentId: String) {
        viewModelScope.launch {
            val result = hubs?.let { runCatchingNotCancelled { it.useAsHub(homeId, agentId) } }
            _uiState.update { it.copy(failed = result != HubResult.Done, adding = it.adding && result != HubResult.Done) }
            refresh()
        }
    }

    /** Stops [agentId] being a hub; a machine that is nobody's device also leaves the Home's group. */
    fun onRemove(agentId: String) {
        viewModelScope.launch {
            val result = hubs?.let { runCatchingNotCancelled { it.removeHub(homeId, agentId) } }
            _uiState.update { it.copy(failed = result != HubResult.Done) }
            refresh()
        }
    }

    fun onDismissFailure() {
        _uiState.update { it.copy(failed = false) }
    }

    private suspend fun refresh() {
        val service = hubs ?: return _uiState.update { it.copy(loading = false, unavailable = true) }
        val recorded = runCatchingNotCancelled { service.hubs(homeId) } ?: return _uiState.update { it.copy(loading = false) }
        val items = recorded.map { HubItem(it.agentId, it.inGroup, it.isDevice) }
        // Enrolment is shown with no hub yet, or when the admin asked to add another: only then is an invite made.
        val enrolling = items.isEmpty() || _uiState.value.adding
        // The commands carry an invite that lapses (InviteLifetime), so ones made a while ago are made again.
        val cached = _uiState.value.commands?.takeIf { nowMillis() - commandsMadeAt < InviteLifetime.HubCommandsRefresh.inWholeMilliseconds }
        val commands = if (!enrolling) null else cached ?: runCatchingNotCancelled { service.enrolment(homeId) }?.commands?.also { commandsMadeAt = nowMillis() }
        val candidates = if (commands == null) emptyList() else runCatchingNotCancelled { service.candidates(homeId) }.orEmpty()
        val ownDevices = if (!enrolling || !offerOwnDevice) emptyList() else runCatchingNotCancelled { service.ownDeviceCandidates(homeId) }.orEmpty()
        _uiState.update {
            it.copy(
                loading = false,
                unavailable = items.isEmpty() && commands == null,
                hubs = items,
                adding = it.adding && items.isNotEmpty(),
                commands = commands,
                candidates = candidates,
                ownDevices = ownDevices,
            )
        }
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
