package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.sarxos.webcam.Webcam
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.screens.ReticleHeightFraction
import uk.veritaslabs.vesta.ui.screens.ReticleWidthFraction
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.barcode_scanner_no_camera

actual val isBarcodeScanningSupported: Boolean = true

/**
 * Retail product codes only. They carry a check digit, so stripes (a window blind) rarely decode; Code 128/39/ITF
 * have no such check and read blinds as barcodes, so they're left out.
 */
private val ProductBarcodeFormats = listOf(
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.UPC_A,
    BarcodeFormat.UPC_E,
)

private fun BarcodeKind.formats(): List<BarcodeFormat> = when (this) {
    BarcodeKind.Product -> ProductBarcodeFormats
    BarcodeKind.QrCode -> listOf(BarcodeFormat.QR_CODE)
}

/** A barcode must decode to the same value in this many consecutive frames before it's reported. */
private const val ConfirmationFrames = 3

private val PreferredResolution = Dimension(1280, 720)

/** Opens [webcam] at [PreferredResolution] if the camera allows it, else at its default size. */
private fun openWebcam(webcam: Webcam): Boolean {
    runCatching {
        webcam.setCustomViewSizes(PreferredResolution)
        webcam.viewSize = PreferredResolution
        if (webcam.open()) return true
    }
    runCatching { webcam.close() }
    runCatching {
        webcam.setCustomViewSizes()
        webcam.viewSize = webcam.viewSizes.maxBy { it.width * it.height }
    }
    return runCatching { webcam.open() }.getOrDefault(false)
}

/** One webcam frame, plus the barcode in it if there was one. */
private class ScannedFrame(val image: ImageBitmap, val barcode: String?)

@Composable
actual fun BarcodeCameraView(onBarcode: (String) -> Unit, modifier: Modifier, kind: BarcodeKind) {
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var noCamera by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val currentViewSize by rememberUpdatedState(viewSize)

    LaunchedEffect(Unit) {
        val webcam = withContext(Dispatchers.IO) {
            Webcam.getDefault()?.takeIf { openWebcam(it) }
        }
        if (webcam == null) {
            noCamera = true
            return@LaunchedEffect
        }
        try {
            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to kind.formats(),
                        DecodeHintType.TRY_HARDER to true,
                    ),
                )
            }
            var candidate: String? = null
            var streak = 0
            while (isActive) {
                // webcam.image blocks until the next frame, so it and the decode stay off the UI thread.
                val scanned = withContext(Dispatchers.IO) {
                    webcam.image?.let { ScannedFrame(it.toComposeImageBitmap(), reader.decodeOrNull(it, reticleRegion(it, currentViewSize))) }
                } ?: break
                frame = scanned.image
                streak = if (scanned.barcode != null && scanned.barcode == candidate) streak + 1 else 1
                candidate = scanned.barcode
                if (candidate != null && streak >= ConfirmationFrames) {
                    currentOnBarcode(candidate)
                    break
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { webcam.close() }
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background).onSizeChanged { viewSize = it },
        contentAlignment = Alignment.Center,
    ) {
        if (noCamera) {
            Text(
                text = stringResource(Res.string.barcode_scanner_no_camera),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            frame?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }
}

/**
 * The part of [image] under the scanner reticle when the frame fills a view of [view] size, cropped to fit.
 * Decoding only that part keeps stray stripes elsewhere in the shot from reading as a barcode.
 */
private fun reticleRegion(image: BufferedImage, view: IntSize): Rectangle {
    val whole = Rectangle(0, 0, image.width, image.height)
    if (view.width == 0 || view.height == 0) return whole
    val scale = maxOf(view.width.toFloat() / image.width, view.height.toFloat() / image.height)
    val offsetX = (view.width - image.width * scale) / 2
    val offsetY = (view.height - image.height * scale) / 2
    val reticleWidth = view.width * ReticleWidthFraction
    val reticleHeight = view.height * ReticleHeightFraction
    val left = ((view.width - reticleWidth) / 2 - offsetX) / scale
    val top = ((view.height - reticleHeight) / 2 - offsetY) / scale
    return Rectangle(left.toInt(), top.toInt(), (reticleWidth / scale).toInt(), (reticleHeight / scale).toInt())
        .intersection(whole)
}

private fun MultiFormatReader.decodeOrNull(image: BufferedImage, region: Rectangle): String? {
    val pixels = image.getRGB(region.x, region.y, region.width, region.height, null, 0, region.width)
    val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(region.width, region.height, pixels)))
    return try {
        decodeWithState(bitmap).text
    } catch (_: NotFoundException) {
        null
    } finally {
        reset()
    }
}
