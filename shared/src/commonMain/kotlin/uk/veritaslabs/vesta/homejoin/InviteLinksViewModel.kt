package uk.veritaslabs.vesta.homejoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DayMillis = 24 * 60 * 60 * 1000L

/** One invite link of a Home as the links screen lists it. */
data class InviteLinkItem(val id: String, val daysLeft: Int, val uses: Int, val maxUses: Int)

/** [uk.veritaslabs.vesta.ui.screens.InviteLinksScreen]'s state: the active links, and the one just made, to share. */
data class InviteLinksUiState(
    val links: List<InviteLinkItem> = emptyList(),
    val created: InviteLink? = null,
    val failed: Boolean = false,
)

/**
 * Backs the invite links screen: an admin makes, shares and revokes the invite links of [homeId]
 * (docs/adr/ADR-018-invite-links.md). Without [joining] (previews) there are none and none can be made.
 */
class InviteLinksViewModel(
    private val homeId: String,
    private val joining: HomeJoining?,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteLinksUiState())
    val uiState: StateFlow<InviteLinksUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onCreate() {
        viewModelScope.launch {
            val link = joining?.createInviteLink(homeId)
            _uiState.update { it.copy(created = link, failed = link == null) }
            refresh()
        }
    }

    fun onRevoke(id: String) {
        viewModelScope.launch {
            joining?.revokeInviteLink(homeId, id)
            refresh()
        }
    }

    /** Back from the just-made link to the list. */
    fun onDismissCreated() {
        _uiState.update { it.copy(created = null) }
    }

    private fun refresh() {
        viewModelScope.launch {
            val now = nowMillis()
            val links = joining?.inviteLinks(homeId).orEmpty().map {
                InviteLinkItem(it.id, daysLeft = ((it.expiresAt - now) / DayMillis).toInt().coerceAtLeast(0), it.uses, it.maxUses)
            }
            _uiState.update { it.copy(links = links) }
        }
    }
}
