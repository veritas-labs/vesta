package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.spaces.MemberRole
import uk.veritaslabs.kits.spaces.MemberListItem
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.kits.spaces.JoinRequest
import uk.veritaslabs.vesta.ui.components.HomeNavigationBar
import uk.veritaslabs.vesta.ui.components.HomeTab
import uk.veritaslabs.vesta.ui.components.ProfileAvatar
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_user_plus
import vesta.shared.generated.resources.members_invite
import vesta.shared.generated.resources.members_request_approve
import vesta.shared.generated.resources.members_request_approved
import vesta.shared.generated.resources.members_request_decline
import vesta.shared.generated.resources.members_request_wants_to_join
import vesta.shared.generated.resources.members_requests_title
import vesta.shared.generated.resources.members_role_admin
import vesta.shared.generated.resources.members_role_member
import vesta.shared.generated.resources.members_title
import vesta.shared.generated.resources.members_you

private val MembersTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MembersTestTags {
    const val InviteButton = "members_invite_button"
    const val Row = "members_row"
    const val RequestRow = "members_request_row"

    /** The Approve button of the request from [memberId], independent of displayed copy. */
    fun approve(memberId: String) = "members_request_approve_$memberId"

    /** The Decline button of the request from [memberId]. */
    fun decline(memberId: String) = "members_request_decline_$memberId"
}

/**
 * The Members tab of a Home: the [members] (name and role badge, the current user marked "You") followed by an
 * Invite row ([onInviteClick]) shaped like a member's, for bringing someone into the Home. Tapping a member
 * ([onMemberClick]) opens their [MemberDetailScreen]. Leaving the Home is in the dashboard's overflow menu. The bottom
 * nav bar has Home ([onHomeClick]) and Members (selected).
 *
 * For an admin, [joinRequests] (people who opened one of the Home's invite links, docs/adr/ADR-018-invite-links.md) come
 * first, each with Approve ([onApprove]) and Decline ([onDecline]); one already approved only waits for their device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    modifier: Modifier = Modifier,
    members: List<MemberListItem> = emptyList(),
    onInviteClick: () -> Unit = {},
    onMemberClick: (MemberListItem) -> Unit = {},
    onHomeClick: () -> Unit = {},
    joinRequests: List<JoinRequest> = emptyList(),
    onApprove: (JoinRequest) -> Unit = {},
    onDecline: (JoinRequest) -> Unit = {},
) {
    val canManage = members.any { it.isYou && it.role == MemberRole.Admin }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= MembersTabletBreakpoint) 24.dp else 16.dp

        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            ),
        ) {
            TopAppBar(
                title = { Text(stringResource(Res.string.members_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding, vertical = 4.dp)) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canManage && joinRequests.isNotEmpty()) {
                        item(key = "requests-title") {
                            Text(
                                text = stringResource(Res.string.members_requests_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(joinRequests, key = { "request-${it.memberId}" }) { request ->
                            RequestRow(request, onApprove = { onApprove(request) }, onDecline = { onDecline(request) })
                        }
                    }
                    items(members, key = { it.memberId }) { member ->
                        MemberRow(member, onClick = { onMemberClick(member) })
                    }
                    item(key = "invite") { InviteRow(onClick = onInviteClick) }
                }
            }
            HomeNavigationBar(
                selected = HomeTab.Members,
                onHomeClick = onHomeClick,
                membersBadge = if (canManage) joinRequests.count { !it.approved } else 0,
            )
        }
    }
}

/** Someone asking to join the Home: Approve and Decline until an admin has approved, then a note that it is under way. */
@Composable
private fun RequestRow(request: JoinRequest, onApprove: () -> Unit, onDecline: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().testTag(MembersTestTags.RequestRow),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileAvatar(name = request.name, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = request.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = stringResource(
                            if (request.approved) Res.string.members_request_approved else Res.string.members_request_wants_to_join,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!request.approved) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDecline, modifier = Modifier.testTag(MembersTestTags.decline(request.memberId))) {
                        Text(stringResource(Res.string.members_request_decline))
                    }
                    Button(onClick = onApprove, modifier = Modifier.testTag(MembersTestTags.approve(request.memberId))) {
                        Text(stringResource(Res.string.members_request_approve))
                    }
                }
            }
        }
    }
}

/** The last row of the member list: same size and shape as a [MemberRow], inviting someone into the Home. */
@Composable
private fun InviteRow(onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        // Same 1dp border as a MemberRow (in the fill colour) so the two are exactly the same size.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).testTag(MembersTestTags.InviteButton),
    ) {
        // Padded and sized like a MemberRow (12dp around a 40dp avatar) so the heights match.
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_user_plus),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(text = stringResource(Res.string.members_invite), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MemberRow(member: MemberListItem, onClick: () -> Unit) {
    // Styled like the Home picker's AvatarButton: a bordered, rounded card with no fill.
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).testTag(MembersTestTags.Row),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileAvatar(name = member.name, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (member.isYou) "${member.name} (${stringResource(Res.string.members_you)})" else member.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (member.role == MemberRole.Admin) Res.string.members_role_admin else Res.string.members_role_member,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val PreviewMembers = listOf(
    MemberListItem("1", "Mark", MemberRole.Admin, isYou = true),
    MemberListItem("2", "Sam", MemberRole.Member),
)

private val PreviewRequests = listOf(
    JoinRequest("home-1", "3", "Alex", createdAt = 1L, approved = false),
    JoinRequest("home-1", "4", "Jo", createdAt = 2L, approved = true),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MembersScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MembersScreen(members = PreviewMembers, joinRequests = PreviewRequests) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MembersScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MembersScreen(members = PreviewMembers, joinRequests = PreviewRequests) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MembersScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { MembersScreen(members = PreviewMembers) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MembersScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MembersScreen(members = PreviewMembers) }
}
