package uk.veritaslabs.vesta.homejoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why the last invitation didn't work, in the terms the invite screen explains. */
enum class HomeInviteError { InvalidLink, Expired, AlreadyAMember, NotAnAdmin, NoResponse, Failed }

/** [uk.veritaslabs.vesta.ui.screens.HomeInviteScreen]'s state. */
sealed interface HomeInviteUiState {
    data object Idle : HomeInviteUiState
    data object Inviting : HomeInviteUiState
    data object Invited : HomeInviteUiState
    data class Failed(val error: HomeInviteError) : HomeInviteUiState
}

/**
 * Backs the invite screen: reads the join link the person pasted, and has [joining] invite them to [homeId]
 * (docs/adr/ADR-015-joining-a-home.md). Without [joining] (previews) an invitation can't be sent.
 */
class HomeInviteViewModel(
    private val homeId: String,
    private val joining: HomeJoining?,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeInviteUiState>(HomeInviteUiState.Idle)
    val uiState: StateFlow<HomeInviteUiState> = _uiState.asStateFlow()

    fun onInvite(raw: String) {
        val link = JoinLink.decode(raw)
        when {
            link == null -> _uiState.value = HomeInviteUiState.Failed(HomeInviteError.InvalidLink)
            link.isExpired(nowMillis()) -> _uiState.value = HomeInviteUiState.Failed(HomeInviteError.Expired)
            joining == null -> _uiState.value = HomeInviteUiState.Failed(HomeInviteError.Failed)
            else -> {
                _uiState.value = HomeInviteUiState.Inviting
                viewModelScope.launch {
                    joining.invite(homeId, link)
                        .onSuccess { _uiState.value = HomeInviteUiState.Invited }
                        .onFailure { _uiState.value = HomeInviteUiState.Failed(it.toInviteError()) }
                }
            }
        }
    }

    /** Back to the empty form, e.g. after a failure or to invite someone else. */
    fun onReset() {
        _uiState.value = HomeInviteUiState.Idle
    }
}

private fun Throwable.toInviteError(): HomeInviteError = when ((this as? InviteException)?.reason) {
    InviteFailure.NotAnAdmin -> HomeInviteError.NotAnAdmin
    InviteFailure.LinkExpired -> HomeInviteError.Expired
    InviteFailure.AlreadyAMember -> HomeInviteError.AlreadyAMember
    InviteFailure.NoResponse -> HomeInviteError.NoResponse
    InviteFailure.NoSuchHome, null -> HomeInviteError.Failed
}
