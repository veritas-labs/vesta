package uk.veritaslabs.vesta.blobs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * The desktop [PhotoEncoder], on Skia (already in Compose Desktop). It reads the dimensions and EXIF orientation from the codec before decoding,
 * so an oversized image is refused without being decoded, then draws the image upright and scaled onto a white raster surface and encodes that
 * as WebP. No WebP encoder dependency is needed (ADR-023, Spike 3).
 */
class SkiaPhotoEncoder : PhotoEncoder {

    override suspend fun encode(source: PhotoSource): PhotoEncodeResult = withContext(Dispatchers.Default) {
        if (source.exceedsByteLimit()) return@withContext PhotoEncodeResult.Rejected(PhotoRejection.TooLarge)
        val bytes = source.readBytesWithinLimit() ?: return@withContext PhotoEncodeResult.Rejected(PhotoRejection.Unreadable)
        runCatching { encodeBytes(bytes) }.getOrElse { PhotoEncodeResult.Rejected(PhotoRejection.Unreadable) }
    }

    private fun encodeBytes(bytes: ByteArray): PhotoEncodeResult {
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        val rawW = codec.width
        val rawH = codec.height
        if (!PhotoLimits.withinPixelLimit(rawW, rawH)) {
            return PhotoEncodeResult.Rejected(if (rawW <= 0 || rawH <= 0) PhotoRejection.Unreadable else PhotoRejection.TooLarge)
        }
        val origin = codec.encodedOrigin.takeIf { it != EncodedOrigin.UNUSED } ?: EncodedOrigin.TOP_LEFT
        val swaps = origin.swapsAxes()
        val uprightW = if (swaps) rawH else rawW
        val uprightH = if (swaps) rawW else rawH
        val (w, h) = PhotoLimits.fit(uprightW, uprightH)

        val image = Image.makeFromBitmap(codec.readPixels())
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.apply {
            clear(0xFFFFFFFF.toInt())
            scale(w.toFloat() / uprightW, h.toFloat() / uprightH)
            concat(origin.toMatrix(uprightW, uprightH))
            drawImageRect(image, Rect.makeWH(rawW.toFloat(), rawH.toFloat()), Rect.makeWH(rawW.toFloat(), rawH.toFloat()), FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true)
        }
        val webp = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.WEBP, PhotoLimits.Quality)?.bytes
            ?: return PhotoEncodeResult.Rejected(PhotoRejection.Unreadable)

        val readable = Bitmap().apply { allocN32Pixels(w, h) }
        surface.readPixels(readable, 0, 0)
        val raw = readable.readPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), w * 4, 0, 0)
            ?: return PhotoEncodeResult.Rejected(PhotoRejection.Unreadable)
        val argb = IntArray(w * h) { i ->
            val o = i * 4
            (255 shl 24) or ((raw[o + 2].toInt() and 255) shl 16) or ((raw[o + 1].toInt() and 255) shl 8) or (raw[o].toInt() and 255)
        }
        return PhotoEncodeResult.Encoded(EncodedPhoto(webp, w, h, PhotoLimits.placeholderFor(argb, w, h)))
    }

    /** Orientations that turn the image a quarter turn, so its stored width is the upright height. */
    private fun EncodedOrigin.swapsAxes() =
        this == EncodedOrigin.LEFT_TOP || this == EncodedOrigin.RIGHT_TOP || this == EncodedOrigin.RIGHT_BOTTOM || this == EncodedOrigin.LEFT_BOTTOM
}
