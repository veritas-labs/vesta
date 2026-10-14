package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.ActionCard
import uk.veritaslabs.vesta.ui.components.ActionCardOrientation
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.device_pairing_generate_body
import vesta.shared.generated.resources.device_pairing_generate_title
import vesta.shared.generated.resources.device_pairing_paste_body
import vesta.shared.generated.resources.device_pairing_paste_title
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_clipboard_text
import vesta.shared.generated.resources.ic_link

private val DevicePairingTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of the cards' displayed copy. */
internal object DevicePairingTestTags {
    const val GenerateCard = "device_pairing_generate_card"
    const val PasteCard = "device_pairing_paste_card"
    const val BackButton = "device_pairing_back_button"
}

/**
 * Entry point for linking this device to an existing identity (see
 * `docs/adr/ADR-003-device-sync.md`) — offers generating a new pairing link
 * from this device, or redeeming one created elsewhere. [onBack], when given, adds a
 * back arrow that leaves the flow.
 */
@Composable
fun DevicePairingScreen(
    modifier: Modifier = Modifier,
    onGeneratePairingLink: () -> Unit = {},
    onPastePairingLink: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= DevicePairingTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp
        // Room for the back arrow above the cards.
        val topInset = if (onBack != null) 48.dp else 0.dp
        val generateIcon = @Composable { Icon(painterResource(Res.drawable.ic_link), contentDescription = null) }
        val pasteIcon = @Composable { Icon(painterResource(Res.drawable.ic_clipboard_text), contentDescription = null) }

        onBack?.let {
            IconButton(
                onClick = it,
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().testTag(DevicePairingTestTags.BackButton),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_left),
                    contentDescription = stringResource(Res.string.common_back),
                )
            }
        }

        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(contentPadding)
                    .padding(top = topInset),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ActionCard(
                    title = stringResource(Res.string.device_pairing_generate_title),
                    body = stringResource(Res.string.device_pairing_generate_body),
                    icon = generateIcon,
                    orientation = ActionCardOrientation.Vertical,
                    onClick = onGeneratePairingLink,
                    modifier = Modifier.weight(1f).testTag(DevicePairingTestTags.GenerateCard),
                )
                ActionCard(
                    title = stringResource(Res.string.device_pairing_paste_title),
                    body = stringResource(Res.string.device_pairing_paste_body),
                    icon = pasteIcon,
                    orientation = ActionCardOrientation.Vertical,
                    onClick = onPastePairingLink,
                    modifier = Modifier.weight(1f).testTag(DevicePairingTestTags.PasteCard),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(contentPadding)
                    .padding(top = topInset),
            ) {
                ActionCard(
                    title = stringResource(Res.string.device_pairing_generate_title),
                    body = stringResource(Res.string.device_pairing_generate_body),
                    icon = generateIcon,
                    onClick = onGeneratePairingLink,
                    modifier = Modifier.testTag(DevicePairingTestTags.GenerateCard),
                )
                Spacer(Modifier.height(12.dp))
                ActionCard(
                    title = stringResource(Res.string.device_pairing_paste_title),
                    body = stringResource(Res.string.device_pairing_paste_body),
                    icon = pasteIcon,
                    onClick = onPastePairingLink,
                    modifier = Modifier.testTag(DevicePairingTestTags.PasteCard),
                )
            }
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { DevicePairingScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { DevicePairingScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { DevicePairingScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { DevicePairingScreen() }
}
