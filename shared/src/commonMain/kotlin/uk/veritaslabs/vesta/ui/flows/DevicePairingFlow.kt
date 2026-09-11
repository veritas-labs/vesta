package uk.veritaslabs.vesta.ui.flows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import uk.veritaslabs.vesta.ui.screens.DevicePairingGeneratedScreen
import uk.veritaslabs.vesta.ui.screens.DevicePairingPasteScreen
import uk.veritaslabs.vesta.ui.screens.DevicePairingScreen
import uk.veritaslabs.vesta.ui.theme.VestaTheme

private sealed interface DevicePairingFlowStep {
    data object Picker : DevicePairingFlowStep
    data object Generated : DevicePairingFlowStep
    data object Paste : DevicePairingFlowStep
}

/**
 * Self-contained click-through router across [DevicePairingScreen] and its
 * two follow-on screens, for manual testing/preview only — this app has no
 * real navigation framework yet, and deciding how device pairing is reached
 * from the rest of the app is ADR-003's still-undesigned onboarding entry
 * point ("S1c" in `docs/adr/ADR-003-device-sync.md`). Not wired into
 * `uk.veritaslabs.vesta.App`.
 *
 * Lives in its own `ui/flows` package, separate from `ui/screens`, so that
 * package keeps meaning "presentation-only leaf composable".
 */
@Composable
fun DevicePairingFlow(modifier: Modifier = Modifier) {
    var step by remember { mutableStateOf<DevicePairingFlowStep>(DevicePairingFlowStep.Picker) }

    // Placeholder pairing link — pending real wiring to
    // uk.veritaslabs.vesta.devicepairing.DevicePairing (jvmMain-only, all
    // TODO() stubs today; not exposed to commonMain).
    val placeholderPairingLink = "https://vesta.example/pair#TODO-placeholder"

    when (step) {
        DevicePairingFlowStep.Picker -> DevicePairingScreen(
            modifier = modifier,
            onGeneratePairingLink = { step = DevicePairingFlowStep.Generated },
            onPastePairingLink = { step = DevicePairingFlowStep.Paste },
        )
        DevicePairingFlowStep.Generated -> DevicePairingGeneratedScreen(
            pairingLink = placeholderPairingLink,
            modifier = modifier,
            onCancel = { step = DevicePairingFlowStep.Picker },
        )
        DevicePairingFlowStep.Paste -> DevicePairingPasteScreen(
            modifier = modifier,
            onCancel = { step = DevicePairingFlowStep.Picker },
        )
    }
}

@Preview
@Composable
private fun DevicePairingFlowPreview() {
    VestaTheme { DevicePairingFlow() }
}
