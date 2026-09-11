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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.SecondaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.device_pairing_cancel
import vesta.shared.generated.resources.device_pairing_paste_body
import vesta.shared.generated.resources.device_pairing_paste_field_label
import vesta.shared.generated.resources.device_pairing_paste_field_placeholder
import vesta.shared.generated.resources.device_pairing_paste_submit
import vesta.shared.generated.resources.device_pairing_paste_title

private val DevicePairingPasteTabletBreakpoint = 600.dp

/**
 * Text-entry counterpart to [DevicePairingGeneratedScreen] — pairs this
 * device using a link generated on another one (see
 * `docs/adr/ADR-003-device-sync.md`). Reached from [DevicePairingScreen]'s
 * "paste" action.
 */
@Composable
fun DevicePairingPasteScreen(
    modifier: Modifier = Modifier,
    onSubmitPairingLink: (String) -> Unit = {},
    onCancel: () -> Unit = {},
) {
    var pastedLink by remember { mutableStateOf("") }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= DevicePairingPasteTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(Res.string.device_pairing_paste_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(Res.string.device_pairing_paste_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
                )
                OutlinedTextField(
                    value = pastedLink,
                    onValueChange = { pastedLink = it },
                    label = { Text(stringResource(Res.string.device_pairing_paste_field_label)) },
                    placeholder = { Text(stringResource(Res.string.device_pairing_paste_field_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    label = stringResource(Res.string.device_pairing_paste_submit),
                    enabled = pastedLink.isNotBlank(),
                    onClick = { onSubmitPairingLink(pastedLink) },
                    modifier = Modifier.padding(top = 16.dp),
                )
                SecondaryButton(
                    label = stringResource(Res.string.device_pairing_cancel),
                    onClick = onCancel,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingPasteScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { DevicePairingPasteScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingPasteScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { DevicePairingPasteScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingPasteScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { DevicePairingPasteScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingPasteScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { DevicePairingPasteScreen() }
}
