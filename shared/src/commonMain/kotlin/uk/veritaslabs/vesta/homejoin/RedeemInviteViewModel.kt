package uk.veritaslabs.vesta.homejoin

import uk.veritaslabs.vesta.VestaKitConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import uk.veritaslabs.kits.spaces.InviteLink
import uk.veritaslabs.kits.spaces.RedeemResult
import uk.veritaslabs.kits.spaces.SpaceJoining
/** Why opening an invite link didn't work, in the terms the screen explains. */
enum class RedeemInviteError { InvalidLink, Expired, AlreadyInHome, Failed }

/** [uk.veritaslabs.vesta.ui.screens.RedeemInviteScreen]'s state. */
sealed interface RedeemInviteUiState {
    data object Idle : RedeemInviteUiState
    data object Redeeming : RedeemInviteUiState

    /** The request is on its way; the Home appears once an admin approves it. */
    data object Started : RedeemInviteUiState
    data class Failed(val error: RedeemInviteError) : RedeemInviteUiState
}

/**
 * Backs the screen where someone joining pastes the invite link an admin sent them
 * (docs/adr/ADR-018-invite-links.md): reads the link and has [joining] ask the admins it names. Without [joining]
 * (previews) a request can't be sent.
 */
class RedeemInviteViewModel(
    private val joining: SpaceJoining?,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _uiState = MutableStateFlow<RedeemInviteUiState>(RedeemInviteUiState.Idle)
    val uiState: StateFlow<RedeemInviteUiState> = _uiState.asStateFlow()

    fun onRedeem(raw: String) {
        val link = InviteLink.decode(raw, VestaKitConfig.home.inviteScheme)
        when {
            link == null -> _uiState.value = RedeemInviteUiState.Failed(RedeemInviteError.InvalidLink)
            link.isExpired(nowMillis()) -> _uiState.value = RedeemInviteUiState.Failed(RedeemInviteError.Expired)
            joining == null -> _uiState.value = RedeemInviteUiState.Failed(RedeemInviteError.Failed)
            else -> {
                _uiState.value = RedeemInviteUiState.Redeeming
                viewModelScope.launch {
                    _uiState.value = runCatching { joining.redeemInvite(link) }.fold(
                        onSuccess = { it.toState() },
                        onFailure = { RedeemInviteUiState.Failed(RedeemInviteError.Failed) },
                    )
                }
            }
        }
    }

    /** Back to the empty form, e.g. after a failure. */
    fun onReset() {
        _uiState.value = RedeemInviteUiState.Idle
    }
}

private fun RedeemResult.toState(): RedeemInviteUiState = when (this) {
    RedeemResult.Started -> RedeemInviteUiState.Started
    RedeemResult.Expired -> RedeemInviteUiState.Failed(RedeemInviteError.Expired)
    RedeemResult.AlreadyInSpace -> RedeemInviteUiState.Failed(RedeemInviteError.AlreadyInHome)
}
