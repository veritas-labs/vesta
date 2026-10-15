package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.VestaKitConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.homejoin.InviteLink
import uk.veritaslabs.vesta.homejoin.InviteLinkItem
import uk.veritaslabs.vesta.homejoin.InviteLinksUiState
import uk.veritaslabs.vesta.homejoin.InviteLinksViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.invite_links_active_title
import vesta.shared.generated.resources.invite_links_body
import vesta.shared.generated.resources.invite_links_manage
import vesta.shared.generated.resources.invite_links_create_failed
import vesta.shared.generated.resources.invite_links_created_body
import vesta.shared.generated.resources.invite_links_created_title
import vesta.shared.generated.resources.invite_links_expires_days
import vesta.shared.generated.resources.invite_links_expires_today
import vesta.shared.generated.resources.invite_links_none
import vesta.shared.generated.resources.invite_links_revoke
import vesta.shared.generated.resources.invite_links_title

private val InviteLinksTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object InviteLinksTestTags {
    const val BackButton = "invite_links_back_button"
    const val ManageButton = "invite_links_manage_button"
    const val Progress = "invite_links_progress"
    const val Error = "invite_links_error"
    const val None = "invite_links_none"
    const val Row = "invite_links_row"
    const val RevokeButton = "invite_links_revoke_button"
}

/** The [InviteLinksViewModel]-backed screen: the invite link to show, and the Home's links to manage. */
@Composable
fun InviteLinksScreen(viewModel: InviteLinksViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InviteLinksScreen(
        state = state,
        onManage = viewModel::onManage,
        onRevoke = viewModel::onRevoke,
        onStopManaging = viewModel::onStopManaging,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Inviting someone to a Home (docs/adr/ADR-018-invite-links.md, ADR-019): the link just made ([InviteLinksUiState.created])
 * as a QR code and text to copy, with a "Manage links" button ([onManage]) under it. Managing ([InviteLinksUiState.managing])
 * lists the links that can still be used, each with a way to revoke it ([onRevoke]); [onStopManaging] goes back to the link.
 * If no link could be made ([InviteLinksUiState.failed], e.g. the user isn't an admin) the screen says so. [onBack] leaves.
 */
@Composable
fun InviteLinksScreen(
    state: InviteLinksUiState,
    modifier: Modifier = Modifier,
    onManage: () -> Unit = {},
    onRevoke: (String) -> Unit = {},
    onStopManaging: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val created = state.created
    when {
        state.managing -> ManageLinks(state.links, onRevoke = onRevoke, onBack = onStopManaging, modifier = modifier)
        created != null -> DevicePairingGeneratedScreen(
            pairingLink = created.encode(VestaKitConfig.home.inviteScheme),
            modifier = modifier,
            title = stringResource(Res.string.invite_links_created_title),
            body = stringResource(Res.string.invite_links_created_body),
            onCancel = onBack,
            footer = {
                TextButton(onClick = onManage, modifier = Modifier.padding(top = 8.dp).testTag(InviteLinksTestTags.ManageButton)) {
                    Text(stringResource(Res.string.invite_links_manage))
                }
            },
        )
        else -> Centered(onBack = onBack, modifier = modifier) {
            if (state.failed) {
                Text(
                    text = stringResource(Res.string.invite_links_create_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(InviteLinksTestTags.Error),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.testTag(InviteLinksTestTags.Progress))
            }
        }
    }
}

/** A screen with a back arrow and [content] in the middle. */
@Composable
private fun Centered(onBack: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= InviteLinksTabletBreakpoint) 24.dp else 16.dp
        BackArrow(onBack, Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
    }
}

@Composable
private fun BackArrow(onBack: () -> Unit, modifier: Modifier) {
    IconButton(onClick = onBack, modifier = modifier.safeDrawingPadding().testTag(InviteLinksTestTags.BackButton)) {
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_left),
            contentDescription = stringResource(Res.string.common_back),
        )
    }
}

/** The links of the Home that can still be used, each with a Revoke button. */
@Composable
private fun ManageLinks(links: List<InviteLinkItem>, onRevoke: (String) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= InviteLinksTabletBreakpoint) 24.dp else 16.dp
        BackArrow(onBack, Modifier.align(Alignment.TopStart))
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(contentPadding).padding(top = 40.dp)) {
            Text(
                text = stringResource(Res.string.invite_links_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.invite_links_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
            )
            Text(
                text = stringResource(Res.string.invite_links_active_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (links.isEmpty()) {
                Text(
                    text = stringResource(Res.string.invite_links_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(InviteLinksTestTags.None),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(links, key = { it.id }) { link -> InviteLinkRow(link, onRevoke = { onRevoke(link.id) }) }
                }
            }
        }
    }
}

@Composable
private fun InviteLinkRow(link: InviteLinkItem, onRevoke: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag(InviteLinksTestTags.Row),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (link.daysLeft == 0) {
                    stringResource(Res.string.invite_links_expires_today, link.uses, link.maxUses)
                } else {
                    stringResource(Res.string.invite_links_expires_days, link.daysLeft, link.uses, link.maxUses)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRevoke,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(InviteLinksTestTags.RevokeButton),
            ) { Text(stringResource(Res.string.invite_links_revoke)) }
        }
    }
}

private val PreviewLinks = listOf(InviteLinkItem("a", daysLeft = 6, uses = 0, maxUses = 1), InviteLinkItem("b", daysLeft = 0, uses = 1, maxUses = 3))
private val PreviewCreated = InviteLink("home-1", "secret", 1_000L, listOf("agent-a"), "Maple Street")

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun InviteLinksScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { InviteLinksScreen(InviteLinksUiState(created = PreviewCreated)) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun InviteLinksScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { InviteLinksScreen(InviteLinksUiState(managing = true, links = PreviewLinks)) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun InviteLinksScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { InviteLinksScreen(InviteLinksUiState(failed = true)) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun InviteLinksScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { InviteLinksScreen(InviteLinksUiState(created = PreviewCreated)) }
}
