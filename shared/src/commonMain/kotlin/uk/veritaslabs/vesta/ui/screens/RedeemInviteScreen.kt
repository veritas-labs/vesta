package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import uk.veritaslabs.vesta.homejoin.RedeemInviteError
import uk.veritaslabs.vesta.homejoin.RedeemInviteUiState
import uk.veritaslabs.vesta.homejoin.RedeemInviteViewModel
import uk.veritaslabs.vesta.ui.components.BarcodeCameraView
import uk.veritaslabs.vesta.ui.components.BarcodeKind
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.isBarcodeScanningSupported
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_scan
import vesta.shared.generated.resources.redeem_invite_body
import vesta.shared.generated.resources.redeem_invite_error_already_in_home
import vesta.shared.generated.resources.redeem_invite_error_expired
import vesta.shared.generated.resources.redeem_invite_error_failed
import vesta.shared.generated.resources.redeem_invite_error_invalid
import vesta.shared.generated.resources.redeem_invite_field_label
import vesta.shared.generated.resources.redeem_invite_progress
import vesta.shared.generated.resources.redeem_invite_scan
import vesta.shared.generated.resources.redeem_invite_submit
import vesta.shared.generated.resources.redeem_invite_title

private val RedeemInviteTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object RedeemInviteTestTags {
    const val LinkField = "redeem_invite_link_field"
    const val ScanButton = "redeem_invite_scan_button"
    const val SubmitButton = "redeem_invite_submit_button"
    const val BackButton = "redeem_invite_back_button"
    const val Progress = "redeem_invite_progress"
    const val Error = "redeem_invite_error"
}

/** The [RedeemInviteViewModel]-backed screen: ask to join the Home whose invite link is pasted or scanned. */
@Composable
fun RedeemInviteScreen(viewModel: RedeemInviteViewModel, onBack: () -> Unit, onStarted: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RedeemInviteScreen(state = state, onRedeem = viewModel::onRedeem, onBack = onBack, onStarted = onStarted, modifier = modifier)
}

/**
 * Where someone joining pastes (or scans, when [scanSupported]) the invite link an admin sent them
 * (docs/adr/ADR-018-invite-links.md); [onRedeem] asks the admins it names to let them in. [state] says whether that is
 * under way or why it failed, and once the request is on its way [onStarted] is called, since all that is left is to wait
 * for approval. [qrCamera] is the scanner's preview, a parameter so tests can stand in for real hardware.
 */
@Composable
fun RedeemInviteScreen(
    state: RedeemInviteUiState,
    modifier: Modifier = Modifier,
    onRedeem: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onStarted: () -> Unit = {},
    scanSupported: Boolean = isBarcodeScanningSupported,
    qrCamera: @Composable (onBarcode: (String) -> Unit, modifier: Modifier) -> Unit = { onBarcode, cameraModifier ->
        BarcodeCameraView(onBarcode, cameraModifier, BarcodeKind.QrCode)
    },
) {
    var scanning by remember { mutableStateOf(false) }
    var pastedLink by remember { mutableStateOf("") }
    val redeeming = state is RedeemInviteUiState.Redeeming
    if (state is RedeemInviteUiState.Started) {
        LaunchedEffect(state) { onStarted() }
    }

    if (scanning) {
        BarcodeScannerScreen(
            onScanned = {
                scanning = false
                pastedLink = it
                onRedeem(it)
            },
            onBack = { scanning = false },
            modifier = modifier,
            kind = BarcodeKind.QrCode,
            camera = qrCamera,
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= RedeemInviteTabletBreakpoint) 24.dp else 16.dp

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().testTag(RedeemInviteTestTags.BackButton),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_left),
                contentDescription = stringResource(Res.string.common_back),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(contentPadding).padding(top = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.redeem_invite_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.redeem_invite_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pastedLink,
                    onValueChange = { pastedLink = it },
                    label = { Text(stringResource(Res.string.redeem_invite_field_label)) },
                    singleLine = true,
                    enabled = !redeeming,
                    isError = state is RedeemInviteUiState.Failed,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f).testTag(RedeemInviteTestTags.LinkField),
                )
                if (scanSupported) {
                    Spacer(Modifier.width(12.dp))
                    IconButton(
                        onClick = { scanning = true },
                        enabled = !redeeming,
                        modifier = Modifier.size(44.dp).testTag(RedeemInviteTestTags.ScanButton),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_scan),
                            contentDescription = stringResource(Res.string.redeem_invite_scan),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
            when (state) {
                is RedeemInviteUiState.Failed -> Text(
                    text = stringResource(state.error.message()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp).testTag(RedeemInviteTestTags.Error),
                )
                RedeemInviteUiState.Redeeming -> Text(
                    text = stringResource(Res.string.redeem_invite_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp).testTag(RedeemInviteTestTags.Progress),
                )
                RedeemInviteUiState.Idle, RedeemInviteUiState.Started -> Unit
            }
            PrimaryButton(
                label = stringResource(Res.string.redeem_invite_submit),
                enabled = pastedLink.isNotBlank() && !redeeming,
                onClick = { onRedeem(pastedLink) },
                modifier = Modifier.padding(top = 16.dp).testTag(RedeemInviteTestTags.SubmitButton),
            )
        }
    }
}

private fun RedeemInviteError.message(): StringResource = when (this) {
    RedeemInviteError.InvalidLink -> Res.string.redeem_invite_error_invalid
    RedeemInviteError.Expired -> Res.string.redeem_invite_error_expired
    RedeemInviteError.AlreadyInHome -> Res.string.redeem_invite_error_already_in_home
    RedeemInviteError.Failed -> Res.string.redeem_invite_error_failed
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun RedeemInviteScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { RedeemInviteScreen(RedeemInviteUiState.Idle) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun RedeemInviteScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { RedeemInviteScreen(RedeemInviteUiState.Failed(RedeemInviteError.Expired)) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun RedeemInviteScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { RedeemInviteScreen(RedeemInviteUiState.Redeeming) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun RedeemInviteScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { RedeemInviteScreen(RedeemInviteUiState.Idle) }
}
