package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.ProfileAvatar
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.member_detail_agent_last_seen
import vesta.shared.generated.resources.member_detail_agent_last_synced
import vesta.shared.generated.resources.member_detail_agent_machine
import vesta.shared.generated.resources.member_detail_agents_empty
import vesta.shared.generated.resources.member_detail_agents_title
import vesta.shared.generated.resources.member_detail_back
import vesta.shared.generated.resources.member_detail_manage
import vesta.shared.generated.resources.members_leave_cancel
import vesta.shared.generated.resources.members_manage_make_admin
import vesta.shared.generated.resources.members_manage_make_admin_body
import vesta.shared.generated.resources.members_manage_remove_admin
import vesta.shared.generated.resources.members_manage_remove_admin_body
import vesta.shared.generated.resources.members_remove
import vesta.shared.generated.resources.members_remove_confirm_body
import vesta.shared.generated.resources.members_remove_confirm_title
import vesta.shared.generated.resources.members_role_admin
import vesta.shared.generated.resources.members_role_member
import vesta.shared.generated.resources.members_you

private val MemberDetailTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MemberDetailTestTags {
    const val BackButton = "member_detail_back_button"
    const val Manage = "member_detail_manage"
    const val ConfirmRole = "member_detail_confirm_role"
    const val RemoveMember = "member_detail_remove_member"
    const val ConfirmRemove = "member_detail_confirm_remove"
    const val AgentsEmpty = "member_detail_agents_empty"

    fun agent(agentId: String) = "member_detail_agent_$agentId"
}

/**
 * One x0x agent of a member (one per device). [machineId], [lastSeenMsAgo] (x0x last saw it) and [lastSyncedMsAgo] (Vesta last
 * synced with it) are null while unknown.
 */
data class MemberAgent(
    val agentId: String,
    val machineId: String? = null,
    val lastSeenMsAgo: Long? = null,
    val lastSyncedMsAgo: Long? = null,
)

/**
 * One member of a Home: their avatar, name and role in the app bar, and the x0x [agents] (one per device) that act for them. An admin looking at
 * someone else also gets a Manage button, which opens a dialog to make them an admin or an ordinary member
 * ([onSetRole]) or remove them from the Home ([onRemoveMember], after confirming). The back arrow calls [onBack].
 * UI only: the caller owns [member] and [agents], and `canManage` says whether the viewer is an admin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    member: MemberListItem,
    agents: List<MemberAgent>,
    modifier: Modifier = Modifier,
    canManage: Boolean = false,
    onBack: () -> Unit = {},
    onSetRole: (MemberListItem, MemberRole) -> Unit = { _, _ -> },
    onRemoveMember: (MemberListItem) -> Unit = {},
) {
    var managing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= MemberDetailTabletBreakpoint) 24.dp else 16.dp

        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical),
            ),
        ) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileAvatar(name = member.name, size = 40.dp)
                        Column {
                            Text(
                                text = if (member.isYou) "${member.name} (${stringResource(Res.string.members_you)})" else member.name,
                                style = MaterialTheme.typography.titleMedium,
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
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(MemberDetailTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.member_detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canManage && !member.isYou) {
                    item(key = "manage") {
                        OutlinedButton(
                            onClick = { managing = true },
                            modifier = Modifier.fillMaxWidth().testTag(MemberDetailTestTags.Manage),
                        ) { Text(stringResource(Res.string.member_detail_manage)) }
                    }
                }
                item(key = "agents-title") {
                    Text(
                        text = stringResource(Res.string.member_detail_agents_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (agents.isEmpty()) {
                    item(key = "agents-empty") {
                        Text(
                            text = stringResource(Res.string.member_detail_agents_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(MemberDetailTestTags.AgentsEmpty),
                        )
                    }
                }
                items(agents, key = { "agent-${it.agentId}" }) { agent -> AgentRow(agent) }
            }
        }
    }

    if (managing) {
        val newRole = if (member.role == MemberRole.Admin) MemberRole.Member else MemberRole.Admin
        AlertDialog(
            onDismissRequest = { managing = false },
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
                        managing = false
                        onSetRole(member, newRole)
                    },
                    modifier = Modifier.testTag(MemberDetailTestTags.ConfirmRole),
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
                            managing = false
                            removing = true
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag(MemberDetailTestTags.RemoveMember),
                    ) { Text(stringResource(Res.string.members_remove)) }
                    TextButton(onClick = { managing = false }) { Text(stringResource(Res.string.members_leave_cancel)) }
                }
            },
        )
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(Res.string.members_remove_confirm_title, member.name)) },
            text = { Text(stringResource(Res.string.members_remove_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = false
                        onRemoveMember(member)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag(MemberDetailTestTags.ConfirmRemove),
                ) { Text(stringResource(Res.string.members_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(Res.string.members_leave_cancel)) } },
        )
    }
}

@Composable
private fun AgentRow(agent: MemberAgent) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag(MemberDetailTestTags.agent(agent.agentId)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = shortAgentId(agent.agentId),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            agent.machineId?.let { InfoLine(stringResource(Res.string.member_detail_agent_machine), shortId(it), monospace = true) }
            agent.lastSeenMsAgo?.let { InfoLine(stringResource(Res.string.member_detail_agent_last_seen), agoText(it)) }
            InfoLine(stringResource(Res.string.member_detail_agent_last_synced), agoText(agent.lastSyncedMsAgo))
        }
    }
}

private val PreviewMember = MemberListItem("2", "Sam", MemberRole.Member)
private val PreviewAgents = listOf(
    MemberAgent(
        "9f2c41d07a3be58c1d6e0f4a8b7c2d3e5f60718293a4b5c6d7e8f90a1b2c3d4e",
        machineId = "11d167d8442575e837eb45fc4859155ffaccc149a50bf6cf22268853175636c3",
        lastSeenMsAgo = 42_000,
        lastSyncedMsAgo = 3 * 60_000,
    ),
    MemberAgent("1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MemberDetailScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MemberDetailScreen(PreviewMember, PreviewAgents, canManage = true) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MemberDetailScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MemberDetailScreen(PreviewMember, PreviewAgents, canManage = true) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MemberDetailScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { MemberDetailScreen(PreviewMember, PreviewAgents) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MemberDetailScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MemberDetailScreen(PreviewMember, emptyList()) }
}
