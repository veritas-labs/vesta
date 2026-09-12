package uk.veritaslabs.vesta.devicepairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.db.VestaDatabase

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
    ) : DevicePairingUiState
}

enum class DevicePairingPasteError { InvalidFormat, Expired }

/**
 * Backs [uk.veritaslabs.vesta.ui.flows.DevicePairingFlow]'s three screens.
 *
 * Generates and validates real [PairingLink]s, and persists a generated one
 * to the local-only `DevicePairingLink` table (see that table's `.sq` file)
 * so it survives recomposition/process death until cancelled, redeemed, or
 * expired — but stops there. Per `docs/adr/ADR-003-device-sync.md` (Status:
 * Proposed), the actual cross-device mechanics — an x0x agent keypair, an
 * owner-issued certificate, a DeviceSync group invite delivered over a
 * direct message, and the redeeming device polling for active membership —
 * are not implemented; see the `TODO()` stubs in jvmMain's `DevicePairing.kt`.
 * [publicKeyHex] is therefore not a real agent key, just random bytes with
 * no cryptographic meaning — enough to exercise the pairing link's own
 * generate → encode → decode → expire → redeem round trip on a single
 * device, which is as far as this stands in for the real thing today.
 */
class DevicePairingViewModel(
    private val database: VestaDatabase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val publicKeyHex: () -> String = { randomHex(byteCount = 32) },
) : ViewModel() {

    private val _uiState = MutableStateFlow<DevicePairingUiState>(DevicePairingUiState.Picker)
    val uiState: StateFlow<DevicePairingUiState> = _uiState.asStateFlow()

    fun onGeneratePairingLink() {
        viewModelScope.launch {
            val link = PairingLink(publicKeyHex = publicKeyHex(), expiresAt = nowMillis() + PairingLinkTtlMillis)
            database.devicePairingLinkQueries.insert(
                id = link.publicKeyHex,
                publicKeyHex = link.publicKeyHex,
                createdAt = nowMillis(),
                expiresAt = link.expiresAt,
            )
            _uiState.value = DevicePairingUiState.Generated(link)
        }
    }

    fun onCancelGenerated(link: PairingLink) {
        viewModelScope.launch {
            database.devicePairingLinkQueries.deleteById(link.publicKeyHex)
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
            else -> viewModelScope.launch {
                // Best-effort local bookkeeping for the case where this same device generated
                // the link (this manual round trip): mark it consumed so it can't be redeemed
                // twice. A link pasted from a different, real device simply won't match here.
                database.devicePairingLinkQueries.selectActive(nowMillis()).awaitAsOneOrNull()
                    ?.takeIf { it.publicKeyHex == link.publicKeyHex }
                    ?.let { database.devicePairingLinkQueries.markConsumed(it.id) }
                _uiState.update { (it as? DevicePairingUiState.Paste)?.copy(error = null, redeemedLink = link) ?: it }
            }
        }
    }

    fun onBackToPicker() {
        _uiState.value = DevicePairingUiState.Picker
    }

    private fun setPasteError(error: DevicePairingPasteError) {
        _uiState.update { (it as? DevicePairingUiState.Paste)?.copy(error = error, redeemedLink = null) ?: it }
    }
}

private fun randomHex(byteCount: Int): String = buildString(byteCount * 2) {
    repeat(byteCount * 2) { append(HexChars[Random.nextInt(HexChars.size)]) }
}
