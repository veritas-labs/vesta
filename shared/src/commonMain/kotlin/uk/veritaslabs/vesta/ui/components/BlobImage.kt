package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.blobs.BlobReference
import uk.veritaslabs.vesta.blobs.LocalBlobs
import uk.veritaslabs.vesta.blobs.Placeholder
import uk.veritaslabs.vesta.blobs.PlaceholderPixels
import uk.veritaslabs.vesta.ui.screens.MediaKind
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.blob_waiting_for_photo

object BlobImageTestTags {
    const val Photo = "blob_image_photo"
    const val Waiting = "blob_image_waiting"
}

/** Whether the bytes of a photo are on this device yet. */
private sealed interface PhotoState {
    data object Loading : PhotoState

    class Ready(val image: ImageBitmap) : PhotoState

    /** Not here (yet): drawn as its placeholder blur. */
    data object Missing : PhotoState
}

/**
 * A photo from the document (ADR-023): the photo itself once this device holds it, otherwise its blurred placeholder with a small "waiting for photo"
 * cue (not an error: it arrives when a device that holds it and this one are online together). It picks the photo up by itself when it arrives.
 * Fills the space it is given, cropping to fit; the caller sets the size.
 */
@Composable
fun BlobImage(reference: BlobReference, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val blobs = LocalBlobs.current
    val state by produceState<PhotoState>(PhotoState.Loading, reference.hash, blobs) {
        if (blobs == null) {
            value = PhotoState.Missing
            return@produceState
        }
        suspend fun read(): PhotoState.Ready? = blobs.load(reference.hash)?.let { bytes ->
            withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
        }?.let { PhotoState.Ready(it) }
        value = read() ?: PhotoState.Missing
        if (value is PhotoState.Missing) {
            blobs.arrivals.filter { it == reference.hash }.first()
            value = read() ?: PhotoState.Missing
        }
    }
    val described = Modifier.semantics { contentDescription?.let { this.contentDescription = it } }
    Box(modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant).then(described)) {
        when (val current = state) {
            is PhotoState.Ready -> Image(
                current.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag(BlobImageTestTags.Photo),
            )
            PhotoState.Loading -> Unit
            PhotoState.Missing -> {
                val blur = remember(reference.blur) { Placeholder.decode(reference.blur)?.toImageBitmap() }
                blur?.let {
                    Canvas(Modifier.fillMaxSize()) {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().apply { filterQuality = FilterQuality.Low }
                            // Crop like the photo will: fill the box, keeping the placeholder's shape.
                            val scale = maxOf(size.width / it.width, size.height / it.height)
                            val w = it.width * scale
                            val h = it.height * scale
                            canvas.drawImageRect(
                                it,
                                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                srcSize = IntSize(it.width, it.height),
                                dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
                                dstSize = IntSize(w.toInt() + 1, h.toInt() + 1),
                                paint = paint,
                            )
                        }
                    }
                }
                Text(
                    stringResource(Res.string.blob_waiting_for_photo),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag(BlobImageTestTags.Waiting),
                )
            }
        }
    }
}

/** The pixels as a bitmap of their own size, drawn one rectangle each; at 32 pixels on the long edge that is cheap, and scaling it up smooths it. */
internal fun PlaceholderPixels.toImageBitmap(): ImageBitmap {
    val bitmap = ImageBitmap(w, h)
    val canvas = GraphicsCanvas(bitmap)
    val paint = Paint()
    for (y in 0 until h) for (x in 0 until w) {
        paint.color = Color(argb[y * w + x])
        canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
    }
    return bitmap
}

private val PreviewReference = BlobReference(
    hash = "a".repeat(64), size = 90_000, mime = "image/webp", kind = MediaKind.Photo, w = 4, h = 3,
    blur = Placeholder.encode(IntArray(12) { if (it % 4 < 2) 0xFFE8A33D.toInt() else 0xFF3D7FE8.toInt() }, 4, 3),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun BlobImageWaitingPhoneLightPreview() {
    VestaTheme(darkTheme = false) { BlobImage(PreviewReference, Modifier.padding(16.dp).fillMaxSize()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun BlobImageWaitingPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { BlobImage(PreviewReference, Modifier.padding(16.dp).fillMaxSize()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun BlobImageWaitingTabletLightPreview() {
    VestaTheme(darkTheme = false) { BlobImage(PreviewReference, Modifier.padding(16.dp).fillMaxSize()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun BlobImageWaitingTabletDarkPreview() {
    VestaTheme(darkTheme = true) { BlobImage(PreviewReference, Modifier.padding(16.dp).fillMaxSize()) }
}
