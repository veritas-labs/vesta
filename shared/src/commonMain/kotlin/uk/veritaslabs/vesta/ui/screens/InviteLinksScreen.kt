package uk.veritaslabs.vesta.ui.screens

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
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.device_pairing_back
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.invite_links_active_title
import vesta.shared.generated.resources.invite_links_body
import vesta.shared.generated.resources.invite_links_create
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
    const val CreateButton = "invite_links_create_button"
    const val Error = "invite_links_error"
    const val None = "invite_links_none"
    const val Row = "invite_links_row"
    const val RevokeButton = "invite_links_revoke_button"
}

/** The [InviteLinksViewModel]-backed screen: make, share and revoke the Home's invite links. */
@Composable
fun InviteLinksScreen(viewModel: InviteLinksViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InviteLinksScreen(
        state = state,
        onCreate = viewModel::onCreate,
        onRevoke = viewModel::onRevoke,
        onDismissCreated = viewModel::onDismissCreated,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * An admin's invite links for a Home (docs/adr/ADR-018-invite-links.md): a button that makes one ([onCreate]), the
 * links that can still be used with a way to revoke each ([onRevoke]), and, once one has been made
 * ([InviteLinksUiState.created]), that link as a QR code and text to send ([onDismissCreated] returns to the list).
 */
@Composable
fun InviteLinksScreen(
    state: InviteLinksUiState,
    modifier: Modifier = Modifier,
    onCreate: () -> Unit = {},
    onRevoke: (String) -> Unit = {},
    onDismissCreated: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    state.created?.let { link ->
        DevicePairingGeneratedScreen(
            pairingLink = link.encode(),
            modifier = modifier,
            title = stringResource(Res.string.invite_links_created_title),
            body = stringResource(Res.string.invite_links_created_body),
            onCancel = onDismissCreated,
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= InviteLinksTabletBreakpoint) 24.dp else 16.dp

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().testTag(InviteLinksTestTags.BackButton),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_left),
                contentDescription = stringResource(Res.string.device_pairing_back),
            )
        }

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
            PrimaryButton(
                label = stringResource(Res.string.invite_links_create),
                onClick = onCreate,
                modifier = Modifier.testTag(InviteLinksTestTags.CreateButton),
            )
            if (state.failed) {
                Text(
                    text = stringResource(Res.string.invite_links_create_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp).testTag(InviteLinksTestTags.Error),
                )
            }
            Text(
                text = stringResource(Res.string.invite_links_active_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            if (state.links.isEmpty()) {
                Text(
                    text = stringResource(Res.string.invite_links_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(InviteLinksTestTags.None),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.links, key = { it.id }) { link -> InviteLinkRow(link, onRevoke = { onRevoke(link.id) }) }
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
    VestaTheme(darkTheme = false) { InviteLinksScreen(InviteLinksUiState(links = PreviewLinks)) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun InviteLinksScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { InviteLinksScreen(InviteLinksUiState(failed = true)) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun InviteLinksScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { InviteLinksScreen(InviteLinksUiState(links = PreviewLinks)) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun InviteLinksScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { InviteLinksScreen(InviteLinksUiState(created = PreviewCreated)) }
}
