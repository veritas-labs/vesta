package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.device_pairing_done
import vesta.shared.generated.resources.device_pairing_redeemed_body
import vesta.shared.generated.resources.device_pairing_redeemed_title

private val DevicePairingRedeemedTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of the button's displayed copy. */
internal object DevicePairingRedeemedTestTags {
    const val DoneButton = "device_pairing_redeemed_done_button"
}

/**
 * Confirms a pasted pairing link (see [DevicePairingPasteScreen]) decoded
 * and was within its expiry — the local-only extent of what a pairing link
 * round trip proves today (`docs/adr/ADR-003-device-sync.md`). Reached from
 * [DevicePairingPasteScreen] once [onDone] there succeeds.
 */
@Composable
fun DevicePairingRedeemedScreen(
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= DevicePairingRedeemedTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(Res.string.device_pairing_redeemed_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.device_pairing_redeemed_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                PrimaryButton(
                    label = stringResource(Res.string.device_pairing_done),
                    onClick = onDone,
                    modifier = Modifier.padding(top = 24.dp).testTag(DevicePairingRedeemedTestTags.DoneButton),
                )
            }
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingRedeemedScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { DevicePairingRedeemedScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingRedeemedScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { DevicePairingRedeemedScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingRedeemedScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { DevicePairingRedeemedScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingRedeemedScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { DevicePairingRedeemedScreen() }
}
