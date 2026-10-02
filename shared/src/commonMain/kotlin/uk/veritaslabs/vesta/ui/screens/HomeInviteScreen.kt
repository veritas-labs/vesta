package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.homejoin.HomeInviteError
import uk.veritaslabs.vesta.homejoin.HomeInviteUiState
import uk.veritaslabs.vesta.homejoin.HomeInviteViewModel
import uk.veritaslabs.vesta.ui.components.BarcodeCameraView
import uk.veritaslabs.vesta.ui.components.BarcodeKind
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.isBarcodeScanningSupported
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.device_pairing_back
import vesta.shared.generated.resources.home_invite_body
import vesta.shared.generated.resources.home_invite_create_link
import vesta.shared.generated.resources.home_invite_done
import vesta.shared.generated.resources.home_invite_error_already_member
import vesta.shared.generated.resources.home_invite_error_expired
import vesta.shared.generated.resources.home_invite_error_failed
import vesta.shared.generated.resources.home_invite_error_invalid
import vesta.shared.generated.resources.home_invite_error_no_response
import vesta.shared.generated.resources.home_invite_error_not_admin
import vesta.shared.generated.resources.home_invite_field_label
import vesta.shared.generated.resources.home_invite_progress
import vesta.shared.generated.resources.home_invite_scan
import vesta.shared.generated.resources.home_invite_submit
import vesta.shared.generated.resources.home_invite_title
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_scan

private val HomeInviteTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HomeInviteTestTags {
    const val LinkField = "home_invite_link_field"
    const val ScanButton = "home_invite_scan_button"
    const val SubmitButton = "home_invite_submit_button"
    const val BackButton = "home_invite_back_button"
    const val Progress = "home_invite_progress"
    const val Done = "home_invite_done"
    const val Error = "home_invite_error"
    const val CreateLinkButton = "home_invite_create_link_button"
}

/** The [HomeInviteViewModel]-backed screen: invite the person whose join link is pasted into the Home. */
@Composable
fun HomeInviteScreen(
    viewModel: HomeInviteViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCreateInviteLink: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeInviteScreen(
        state = state,
        onInvite = viewModel::onInvite,
        onBack = onBack,
        modifier = modifier,
        onCreateInviteLink = onCreateInviteLink,
    )
}

/**
 * Invites someone to this Home (docs/adr/ADR-015-joining-a-home.md): the admin pastes the link the other person is
 * showing on their "Join a home" screen, and [onInvite] sends them the invitation. [state] says whether that is
 * under way, done or why it failed.
 *
 * Next to the field a scan button (shown when [scanSupported]) opens a full-screen QR scanner over the form; the
 * scanned link fills the field and is sent straight away. [qrCamera] is the preview the scanner shows, a parameter
 * so tests can stand in for real hardware.
 *
 * Under the form, [onCreateInviteLink] opens the screen that makes an invite link to share instead
 * (docs/adr/ADR-018-invite-links.md), for when the other person can't show their join link right now.
 */
@Composable
fun HomeInviteScreen(
    state: HomeInviteUiState,
    modifier: Modifier = Modifier,
    onInvite: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onCreateInviteLink: () -> Unit = {},
    scanSupported: Boolean = isBarcodeScanningSupported,
    qrCamera: @Composable (onBarcode: (String) -> Unit, modifier: Modifier) -> Unit = { onBarcode, cameraModifier ->
        BarcodeCameraView(onBarcode, cameraModifier, BarcodeKind.QrCode)
    },
) {
    var scanning by remember { mutableStateOf(false) }
    var pastedLink by remember { mutableStateOf("") }
    val inviting = state is HomeInviteUiState.Inviting

    if (scanning) {
        BarcodeScannerScreen(
            onScanned = {
                scanning = false
                pastedLink = it
                onInvite(it)
            },
            onBack = { scanning = false },
            modifier = modifier,
            kind = BarcodeKind.QrCode,
            camera = qrCamera,
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= HomeInviteTabletBreakpoint) 24.dp else 16.dp

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().testTag(HomeInviteTestTags.BackButton),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_left),
                contentDescription = stringResource(Res.string.device_pairing_back),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(contentPadding).padding(top = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.home_invite_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.home_invite_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pastedLink,
                    onValueChange = { pastedLink = it },
                    label = { Text(stringResource(Res.string.home_invite_field_label)) },
                    singleLine = true,
                    enabled = !inviting,
                    isError = state is HomeInviteUiState.Failed,
                    modifier = Modifier.weight(1f).testTag(HomeInviteTestTags.LinkField),
                )
                if (scanSupported) {
                    Spacer(Modifier.width(12.dp))
                    IconButton(
                        onClick = { scanning = true },
                        enabled = !inviting,
                        modifier = Modifier.size(44.dp).testTag(HomeInviteTestTags.ScanButton),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_scan),
                            contentDescription = stringResource(Res.string.home_invite_scan),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
            when (state) {
                is HomeInviteUiState.Failed -> Text(
                    text = stringResource(state.error.message()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp).testTag(HomeInviteTestTags.Error),
                )
                HomeInviteUiState.Inviting -> Text(
                    text = stringResource(Res.string.home_invite_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp).testTag(HomeInviteTestTags.Progress),
                )
                HomeInviteUiState.Invited -> Text(
                    text = stringResource(Res.string.home_invite_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp).testTag(HomeInviteTestTags.Done),
                )
                HomeInviteUiState.Idle -> Unit
            }
            PrimaryButton(
                label = stringResource(Res.string.home_invite_submit),
                enabled = pastedLink.isNotBlank() && !inviting,
                onClick = { onInvite(pastedLink) },
                modifier = Modifier.padding(top = 16.dp).testTag(HomeInviteTestTags.SubmitButton),
            )
            TextButton(
                onClick = onCreateInviteLink,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).testTag(HomeInviteTestTags.CreateLinkButton),
            ) { Text(stringResource(Res.string.home_invite_create_link)) }
        }
    }
}

private fun HomeInviteError.message(): StringResource = when (this) {
    HomeInviteError.InvalidLink -> Res.string.home_invite_error_invalid
    HomeInviteError.Expired -> Res.string.home_invite_error_expired
    HomeInviteError.AlreadyAMember -> Res.string.home_invite_error_already_member
    HomeInviteError.NotAnAdmin -> Res.string.home_invite_error_not_admin
    HomeInviteError.NoResponse -> Res.string.home_invite_error_no_response
    HomeInviteError.Failed -> Res.string.home_invite_error_failed
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeInviteScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomeInviteScreen(HomeInviteUiState.Idle) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeInviteScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomeInviteScreen(HomeInviteUiState.Failed(HomeInviteError.Expired)) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeInviteScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomeInviteScreen(HomeInviteUiState.Inviting) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeInviteScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomeInviteScreen(HomeInviteUiState.Invited) }
}
