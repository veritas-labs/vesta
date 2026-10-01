package uk.veritaslabs.vesta.devicepairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.devicesync.DeviceSyncService

/** How long a generated pairing link stays redeemable before it must be regenerated. */
private const val PairingLinkTtlMillis = 5 * 60 * 1000L

private val HexChars = "0123456789abcdef".toCharArray()

/** [uk.veritaslabs.vesta.ui.flows.DevicePairingFlow]'s current screen, and any state that screen owns. */
sealed interface DevicePairingUiState {
    data object Picker : DevicePairingUiState
    data class Generated(val link: PairingLink) : DevicePairingUiState
    data class Paste(
        val error: DevicePairingPasteError? = null,
        val redeemedLink: PairingLink? = null,
        /** True while the pasted link's device is being added; that can take a while if it is slow to answer. */
        val isPairing: Boolean = false,
    ) : DevicePairingUiState
}

enum class DevicePairingPasteError { InvalidFormat, Expired, PairingFailed }

/**
 * Backs [uk.veritaslabs.vesta.ui.flows.DevicePairingFlow]'s three screens.
 *
 * The new device generates a [PairingLink] naming its own x0x agent id and persists it to the local-only
 * `DevicePairingLink` table, so it survives recomposition and process death until cancelled, used or expired.
 * While it is showing, [deviceSync] (running app-wide) accepts the group invite that comes back, and this view
 * model moves on to the confirmation screen once [DeviceSyncService.joined] fires. A device that already belongs
 * to the group pastes the link instead: [DeviceSyncService.pairWith] adds the new device
 * (`docs/adr/ADR-012-device-sync-over-x0x-group.md`). Without a [deviceSync] (previews, tests) a pasted link
 * just has to decode and be unexpired.
 */
class DevicePairingViewModel(
    private val database: VestaDatabase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val agentId: suspend () -> String = { randomHex(byteCount = 32) },
    private val deviceName: () -> String = { "" },
    private val deviceSync: DeviceSyncService? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DevicePairingUiState>(DevicePairingUiState.Picker)
    val uiState: StateFlow<DevicePairingUiState> = _uiState.asStateFlow()

    private var waitingForInvite: Job? = null

    fun onGeneratePairingLink() {
        viewModelScope.launch {
            val link = PairingLink(
                agentId = agentId(),
                expiresAt = nowMillis() + PairingLinkTtlMillis,
                deviceName = deviceName().takeIf { it.isNotBlank() },
            )
            database.devicePairingLinkQueries.insert(
                id = link.agentId,
                publicKeyHex = link.agentId, // the column predates agent ids and has always held the link's identifier
                createdAt = nowMillis(),
                expiresAt = link.expiresAt,
            )
            _uiState.value = DevicePairingUiState.Generated(link)
            waitingForInvite?.cancel()
            waitingForInvite = deviceSync?.let { sync ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    sync.joined.first()
                    _uiState.value = DevicePairingUiState.Paste(redeemedLink = link)
                }
            }
        }
    }

    fun onCancelGenerated(link: PairingLink) {
        waitingForInvite?.cancel()
        viewModelScope.launch {
            database.devicePairingLinkQueries.deleteById(link.agentId)
            _uiState.value = DevicePairingUiState.Picker
        }
    }

    fun onPastePairingLink() {
        _uiState.value = DevicePairingUiState.Paste()
    }

    fun onSubmitPairingLink(raw: String) {
        val link = PairingLink.decode(raw)
        when {
            link == null -> setPasteError(DevicePairingPasteError.InvalidFormat)
            link.isExpired(nowMillis()) -> setPasteError(DevicePairingPasteError.Expired)
            deviceSync != null -> {
                _uiState.update { (it as? DevicePairingUiState.Paste)?.copy(error = null, isPairing = true) ?: it }
                viewModelScope.launch {
                    deviceSync.pairWith(link)
                        .onSuccess { _uiState.update { (it as? DevicePairingUiState.Paste)?.copy(redeemedLink = link, isPairing = false) ?: it } }
                        .onFailure { setPasteError(DevicePairingPasteError.PairingFailed) }
                }
            }
            else -> viewModelScope.launch {
                // Best-effort local bookkeeping for the case where this same device generated
                // the link (this manual round trip): mark it consumed so it can't be redeemed
                // twice. A link pasted from a different, real device simply won't match here.
                database.devicePairingLinkQueries.selectActive(nowMillis()).awaitAsOneOrNull()
                    ?.takeIf { it.publicKeyHex == link.agentId }
                    ?.let { database.devicePairingLinkQueries.markConsumed(it.id) }
                _uiState.update { (it as? DevicePairingUiState.Paste)?.copy(error = null, redeemedLink = link) ?: it }
            }
        }
    }

    fun onBackToPicker() {
        waitingForInvite?.cancel()
        _uiState.value = DevicePairingUiState.Picker
    }

    private fun setPasteError(error: DevicePairingPasteError) {
        _uiState.update { (it as? DevicePairingUiState.Paste)?.copy(error = error, redeemedLink = null, isPairing = false) ?: it }
    }
}

private fun randomHex(byteCount: Int): String = buildString(byteCount * 2) {
    repeat(byteCount * 2) { append(HexChars[Random.nextInt(HexChars.size)]) }
}
