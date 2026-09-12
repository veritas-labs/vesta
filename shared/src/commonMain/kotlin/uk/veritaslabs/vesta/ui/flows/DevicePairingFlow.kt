package uk.veritaslabs.vesta.ui.flows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.devicepairing.DevicePairingPasteError
import uk.veritaslabs.vesta.devicepairing.DevicePairingUiState
import uk.veritaslabs.vesta.devicepairing.DevicePairingViewModel
import uk.veritaslabs.vesta.ui.screens.DevicePairingGeneratedScreen
import uk.veritaslabs.vesta.ui.screens.DevicePairingPasteScreen
import uk.veritaslabs.vesta.ui.screens.DevicePairingRedeemedScreen
import uk.veritaslabs.vesta.ui.screens.DevicePairingScreen
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.device_pairing_paste_error_expired
import vesta.shared.generated.resources.device_pairing_paste_error_invalid

/**
 * Wires [DevicePairingScreen] and its follow-on screens to [viewModel] — this
 * app has no real navigation framework yet, and deciding how device pairing
 * is reached from the rest of the app is ADR-003's still-undesigned
 * onboarding entry point ("S1c" in `docs/adr/ADR-003-device-sync.md`). Not
 * wired into `uk.veritaslabs.vesta.App`; [viewModel] is caller-supplied
 * (e.g. `remember { DevicePairingViewModel(database) }`) rather than
 * resolved from a `ViewModelStoreOwner`, since this app has no such owner
 * set up anywhere yet either.
 *
 * Lives in its own `ui/flows` package, separate from `ui/screens`, so that
 * package keeps meaning "presentation-only leaf composable".
 */
@Composable
fun DevicePairingFlow(viewModel: DevicePairingViewModel, modifier: Modifier = Modifier) {
    when (val state = viewModel.uiState.collectAsState().value) {
        DevicePairingUiState.Picker -> DevicePairingScreen(
            modifier = modifier,
            onGeneratePairingLink = viewModel::onGeneratePairingLink,
            onPastePairingLink = viewModel::onPastePairingLink,
        )
        is DevicePairingUiState.Generated -> DevicePairingGeneratedScreen(
            pairingLink = state.link.encode(),
            modifier = modifier,
            onCancel = { viewModel.onCancelGenerated(state.link) },
        )
        is DevicePairingUiState.Paste -> if (state.redeemedLink != null) {
            DevicePairingRedeemedScreen(
                modifier = modifier,
                onDone = viewModel::onBackToPicker,
            )
        } else {
            DevicePairingPasteScreen(
                modifier = modifier,
                errorMessage = state.error?.let { pasteErrorMessage(it) },
                onSubmitPairingLink = viewModel::onSubmitPairingLink,
                onCancel = viewModel::onBackToPicker,
            )
        }
    }
}

@Composable
private fun pasteErrorMessage(error: DevicePairingPasteError): String = when (error) {
    DevicePairingPasteError.InvalidFormat -> stringResource(Res.string.device_pairing_paste_error_invalid)
    DevicePairingPasteError.Expired -> stringResource(Res.string.device_pairing_paste_error_expired)
}
