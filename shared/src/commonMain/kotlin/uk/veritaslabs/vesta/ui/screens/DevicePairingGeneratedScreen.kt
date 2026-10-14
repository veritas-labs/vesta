package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.options.QrLogo
import io.github.alexzhirkevich.qrose.options.QrLogoPadding
import io.github.alexzhirkevich.qrose.options.QrLogoShape
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.device_pairing_generated_body
import vesta.shared.generated.resources.device_pairing_generated_copy_link
import vesta.shared.generated.resources.device_pairing_generated_title
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.img_app_logo

private val DevicePairingGeneratedTabletBreakpoint = 600.dp
private val DevicePairingGeneratedQrSize = 180.dp
private val DevicePairingGeneratedQrSizeTablet = 240.dp

/** Stable hooks for UI tests to target, independent of the buttons' displayed copy. */
internal object DevicePairingGeneratedTestTags {
    const val CopyLinkButton = "device_pairing_generated_copy_link_button"
    const val BackButton = "device_pairing_generated_back_button"
}

/**
 * Shows a just-generated pairing link (see `docs/adr/ADR-003-device-sync.md`)
 * as a scannable QR code, with the raw [pairingLink] text underneath for
 * cases where scanning isn't possible — copyable to the clipboard via
 * [onCopyLink], or abandoned via the back arrow ([onCancel]). [title] and [body] default to the pairing copy; joining
 * a Home shows its own link here too (`docs/adr/ADR-018-invite-links.md`), with its own [footer] under the copy button.
 * Reached from
 * [DevicePairingScreen]'s "generate" action.
 */
@Composable
fun DevicePairingGeneratedScreen(
    pairingLink: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.device_pairing_generated_title),
    body: String = stringResource(Res.string.device_pairing_generated_body),
    onCopyLink: () -> Unit = {},
    onCancel: () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    // LocalClipboardManager.setText is deprecated in favor of the suspend
    // LocalClipboard/ClipEntry API, but stays synchronous and needs no
    // coroutine scope or platform-specific ClipEntry construction — simplest
    // fit for a plain-text copy button. ClipEntry's own constructor takes a
    // platform-native object (Transferable/ClipData/NSItemProvider), so the
    // suspend API has no common-code way to wrap a plain String — hence the
    // deliberate suppress rather than an expect/actual per target.
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= DevicePairingGeneratedTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp
        val qrSize = if (isTablet) DevicePairingGeneratedQrSizeTablet else DevicePairingGeneratedQrSize

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().testTag(DevicePairingGeneratedTestTags.BackButton),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_left),
                contentDescription = stringResource(Res.string.common_back),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(contentPadding)
                .padding(top = 40.dp), // room for the back arrow
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Box(
                    // QR scanners need a light quiet zone around the code, and the
                    // code's own light modules render transparent — so this stays
                    // literal white rather than a theme color, or the code becomes
                    // unreadable against a dark background.
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .size(qrSize)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Image(
                        painter = rememberQrCodePainter(
                            data = pairingLink,
                            logo = QrLogo(
                                painter = painterResource(Res.drawable.img_app_logo),
                                size = 0.22f,
                                padding = QrLogoPadding.Accurate(0.2f),
                                shape = QrLogoShape.roundCorners(0.3f),
                            ),
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = pairingLink,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp),
                )
                PrimaryButton(
                    label = stringResource(Res.string.device_pairing_generated_copy_link),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(pairingLink))
                        onCopyLink()
                    },
                    modifier = Modifier.padding(top = 24.dp).testTag(DevicePairingGeneratedTestTags.CopyLinkButton),
                )
                footer()
            }
        }
    }
}

private const val DevicePairingGeneratedPreviewLink = "https://vesta.example/pair#TODO-placeholder"

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingGeneratedScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { DevicePairingGeneratedScreen(pairingLink = DevicePairingGeneratedPreviewLink) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingGeneratedScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { DevicePairingGeneratedScreen(pairingLink = DevicePairingGeneratedPreviewLink) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingGeneratedScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { DevicePairingGeneratedScreen(pairingLink = DevicePairingGeneratedPreviewLink) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingGeneratedScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { DevicePairingGeneratedScreen(pairingLink = DevicePairingGeneratedPreviewLink) }
}
