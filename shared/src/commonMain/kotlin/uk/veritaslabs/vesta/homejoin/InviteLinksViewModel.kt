package uk.veritaslabs.vesta.homejoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import uk.veritaslabs.kits.spaces.InviteLink
import uk.veritaslabs.kits.spaces.SpaceJoining
private const val DayMillis = 24 * 60 * 60 * 1000L

/** One invite link of a Home as the manage-links list shows it. */
data class InviteLinkItem(val id: String, val daysLeft: Int, val uses: Int, val maxUses: Int)

/**
 * [uk.veritaslabs.vesta.ui.screens.InviteLinksScreen]'s state: the [created] link to show (null while it is being made),
 * [failed] if none could be, and, while [managing], the [links] that can still be used.
 */
data class InviteLinksUiState(
    val created: InviteLink? = null,
    val failed: Boolean = false,
    val managing: Boolean = false,
    val links: List<InviteLinkItem> = emptyList(),
)

/**
 * Backs the invite screen (docs/adr/ADR-018-invite-links.md, ADR-019): opening it makes a link for [homeId] to show the
 * person joining, and from there an admin can manage the Home's links, revoking any. Without [joining] (previews) no
 * link can be made.
 */
class InviteLinksViewModel(
    private val homeId: String,
    private val joining: SpaceJoining?,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteLinksUiState())
    val uiState: StateFlow<InviteLinksUiState> = _uiState.asStateFlow()

    // Whether a link was revoked while managing, which may have been the one on show.
    private var revokedAny = false

    init {
        create()
    }

    fun onManage() {
        revokedAny = false
        _uiState.update { it.copy(managing = true) }
        refresh()
    }

    fun onRevoke(id: String) {
        viewModelScope.launch {
            joining?.revokeInviteLink(homeId, id)
            revokedAny = true
            refresh()
        }
    }

    /** Back from the manage list to the link. A new link is made if one was revoked, as it may have been the one shown. */
    fun onStopManaging() {
        _uiState.update { it.copy(managing = false) }
        if (revokedAny) {
            _uiState.update { it.copy(created = null) }
            create()
        }
    }

    private fun create() {
        viewModelScope.launch {
            val link = joining?.createInviteLink(homeId)
            _uiState.update { it.copy(created = link, failed = link == null) }
        }
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
