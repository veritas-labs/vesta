package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.BarcodeCameraView
import uk.veritaslabs.vesta.ui.components.BarcodeKind
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.barcode_scanner_hint
import vesta.shared.generated.resources.barcode_scanner_title
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.qr_scanner_hint
import vesta.shared.generated.resources.qr_scanner_title

internal object BarcodeScannerTestTags {
    const val BackButton = "barcode_scanner_back_button"
}

/**
 * Full-screen barcode scanner: a back button over [camera] and a hint. [onScanned] gets the first barcode the
 * camera reports; [onBack] leaves without a result. [camera] is the platform preview, a parameter so previews
 * and tests can stand in for real hardware. [kind] is what to look for, which also picks the title and hint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BarcodeKind = BarcodeKind.Product,
    camera: @Composable (onBarcode: (String) -> Unit, modifier: Modifier) -> Unit = { onBarcode, cameraModifier ->
        BarcodeCameraView(onBarcode, cameraModifier, kind)
    },
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        TopAppBar(
            title = { Text(
                    stringResource(
                        if (kind == BarcodeKind.QrCode) Res.string.qr_scanner_title else Res.string.barcode_scanner_title,
                    ),
                ) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(BarcodeScannerTestTags.BackButton)) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_left),
                        contentDescription = stringResource(Res.string.common_back),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
        ) {
            camera(onScanned, Modifier.fillMaxSize())
            BarcodeScannerReticle(Modifier.fillMaxSize())
        }
        Text(
            text = stringResource(
                if (kind == BarcodeKind.QrCode) Res.string.qr_scanner_hint else Res.string.barcode_scanner_hint,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

/** The reticle's width and height as fractions of the camera view; scanners that crop to it use the same values. */
internal const val ReticleWidthFraction = 0.8f
internal const val ReticleHeightFraction = 0.4f

/** Corner brackets and a centre line marking where to hold the barcode. */
@Composable
internal fun BarcodeScannerReticle(modifier: Modifier = Modifier) {
    val bracketColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.error
    Canvas(modifier) {
        val reticle = Size(size.width * ReticleWidthFraction, size.height * ReticleHeightFraction)
        val topLeft = Offset((size.width - reticle.width) / 2, (size.height - reticle.height) / 2)
        val arm = minOf(reticle.width, reticle.height) * 0.2f
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        val right = topLeft.x + reticle.width
        val bottom = topLeft.y + reticle.height
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(bracketColor, Offset(x, y), Offset(x + dx * arm, y), stroke.width, stroke.cap)
            drawLine(bracketColor, Offset(x, y), Offset(x, y + dy * arm), stroke.width, stroke.cap)
        }
        corner(topLeft.x, topLeft.y, 1f, 1f)
        corner(right, topLeft.y, -1f, 1f)
        corner(topLeft.x, bottom, 1f, -1f)
        corner(right, bottom, -1f, -1f)
        val midY = topLeft.y + reticle.height / 2
        drawLine(lineColor, Offset(topLeft.x + arm / 2, midY), Offset(right - arm / 2, midY), 2.dp.toPx())
    }
}

@Composable
private fun BarcodeScannerPreviewContent() {
    BarcodeScannerScreen(
        onScanned = {},
        onBack = {},
        camera = { _, cameraModifier ->
            Box(cameraModifier.background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text("Camera preview", color = MaterialTheme.colorScheme.onBackground)
            }
        },
    )
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun BarcodeScannerScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { BarcodeScannerPreviewContent() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun BarcodeScannerScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { BarcodeScannerPreviewContent() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun BarcodeScannerScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { BarcodeScannerPreviewContent() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun BarcodeScannerScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { BarcodeScannerPreviewContent() }
}
