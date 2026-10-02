package uk.veritaslabs.vesta.ui.screens

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.HomeNavigationBar
import uk.veritaslabs.vesta.ui.components.HomeTab
import uk.veritaslabs.vesta.ui.components.ProfileAvatar
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_user_plus
import vesta.shared.generated.resources.members_invite
import vesta.shared.generated.resources.members_leave_cancel
import vesta.shared.generated.resources.members_remove
import vesta.shared.generated.resources.members_remove_confirm_body
import vesta.shared.generated.resources.members_remove_confirm_title
import vesta.shared.generated.resources.members_manage_make_admin
import vesta.shared.generated.resources.members_manage_make_admin_body
import vesta.shared.generated.resources.members_manage_remove_admin
import vesta.shared.generated.resources.members_manage_remove_admin_body
import vesta.shared.generated.resources.members_role_admin
import vesta.shared.generated.resources.members_role_member
import vesta.shared.generated.resources.members_title
import vesta.shared.generated.resources.members_you

private val MembersTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MembersTestTags {
    const val InviteButton = "members_invite_button"
    const val Row = "members_row"
    const val ConfirmRole = "members_confirm_role"
    const val RemoveMember = "members_remove_member"
    const val ConfirmRemove = "members_confirm_remove"
}

/** The role a member has in a Home; [storageValue] is the `HomeMember.role` column's value. */
enum class MemberRole(val storageValue: String) {
    Admin("admin"),
    Member("member");

    companion object {
        /** The role stored as [value]; anything unrecognised is an ordinary [Member]. */
        fun fromStorageValue(value: String): MemberRole = entries.firstOrNull { it.storageValue == value } ?: Member
    }
}

/** One person in a Home. A person is one row however many devices they have; [isYou] marks the current user. */
data class MemberListItem(
    val memberId: String,
    val name: String,
    val role: MemberRole,
    val isYou: Boolean = false,
)

/**
 * The Members tab of a Home: the [members] (name and role badge, the current user marked "You") followed by an
 * Invite row ([onInviteClick]) shaped like a member's, for bringing someone into the Home, and a dialog on each
 * other member's row, for an admin, to make them an admin or an ordinary member ([onSetRole]) or remove them from
 * the Home ([onRemoveMember], after confirming). Leaving the Home is in the dashboard's overflow menu. The bottom
 * nav bar has Home ([onHomeClick]) and Members (selected).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    modifier: Modifier = Modifier,
    members: List<MemberListItem> = emptyList(),
    onInviteClick: () -> Unit = {},
    onSetRole: (MemberListItem, MemberRole) -> Unit = { _, _ -> },
    onRemoveMember: (MemberListItem) -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
    var managing by remember { mutableStateOf<MemberListItem?>(null) }
    var removing by remember { mutableStateOf<MemberListItem?>(null) }
    val canManage = members.any { it.isYou && it.role == MemberRole.Admin }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= MembersTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.members_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding, vertical = 4.dp)) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(members, key = { it.memberId }) { member ->
                        MemberRow(member, onClick = if (canManage && !member.isYou) ({ managing = member }) else null)
                    }
                    item(key = "invite") { InviteRow(onClick = onInviteClick) }
                }
            }
            HomeNavigationBar(selected = HomeTab.Members, onHomeClick = onHomeClick)
        }
    }

    managing?.let { member ->
        val newRole = if (member.role == MemberRole.Admin) MemberRole.Member else MemberRole.Admin
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(member.name) },
            text = {
                Text(
                    stringResource(
                        if (newRole == MemberRole.Admin) Res.string.members_manage_make_admin_body else Res.string.members_manage_remove_admin_body,
                        member.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        managing = null
                        onSetRole(member, newRole)
                    },
                    modifier = Modifier.testTag(MembersTestTags.ConfirmRole),
                ) {
                    Text(
                        stringResource(
                            if (newRole == MemberRole.Admin) Res.string.members_manage_make_admin else Res.string.members_manage_remove_admin,
                        ),
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            managing = null
                            removing = member
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag(MembersTestTags.RemoveMember),
                    ) { Text(stringResource(Res.string.members_remove)) }
                    TextButton(onClick = { managing = null }) { Text(stringResource(Res.string.members_leave_cancel)) }
                }
            },
        )
    }
    removing?.let { member ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.members_remove_confirm_title, member.name)) },
            text = { Text(stringResource(Res.string.members_remove_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        onRemoveMember(member)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag(MembersTestTags.ConfirmRemove),
                ) { Text(stringResource(Res.string.members_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(Res.string.members_leave_cancel)) } },
        )
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(MembersTestTags.InviteButton),
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
private fun MemberRow(member: MemberListItem, onClick: (() -> Unit)?) {
    val rowModifier = Modifier.fillMaxWidth().testTag(MembersTestTags.Row)
    // Styled like the Home picker's AvatarButton: a bordered, rounded card with no fill.
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = if (onClick != null) rowModifier.clickable(onClick = onClick) else rowModifier,
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

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MembersScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MembersScreen(members = PreviewMembers) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MembersScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MembersScreen(members = PreviewMembers) }
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
