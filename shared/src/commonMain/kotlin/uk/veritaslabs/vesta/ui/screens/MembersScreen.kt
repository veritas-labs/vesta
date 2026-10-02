package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import vesta.shared.generated.resources.members_role_admin
import vesta.shared.generated.resources.members_role_member
import vesta.shared.generated.resources.members_title
import vesta.shared.generated.resources.members_you

private val MembersTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MembersTestTags {
    const val InviteButton = "members_invite_button"
    const val Row = "members_row"
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
 * The Members tab of a Home: the [members] (name and role badge, the current user marked "You"), and an Invite
 * button ([onInviteClick]) for bringing someone into the Home. The bottom nav bar has Home ([onHomeClick]) and
 * Members (selected).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    modifier: Modifier = Modifier,
    members: List<MemberListItem> = emptyList(),
    onInviteClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
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
                Button(onClick = onInviteClick, modifier = Modifier.testTag(MembersTestTags.InviteButton)) {
                    Row {
                        Icon(
                            painter = painterResource(Res.drawable.ic_user_plus),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.members_invite))
                    }
                }
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(members, key = { it.memberId }) { member -> MemberRow(member) }
                }
            }
            HomeNavigationBar(selected = HomeTab.Members, onHomeClick = onHomeClick)
        }
    }
}

@Composable
private fun MemberRow(member: MemberListItem) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag(MembersTestTags.Row),
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
