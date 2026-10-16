package uk.veritaslabs.kits.blobs

import kotlin.math.max
import kotlin.math.min
import uk.veritaslabs.kits.blobs.MediaKind

/** Where a photo to add comes from: its bytes, or a file this process can read (the platform picker hands over one or the other). */
sealed interface PhotoSource {
    class Bytes(val bytes: ByteArray) : PhotoSource

    class FilePath(val path: String) : PhotoSource
}

/** A photo ready to store: WebP [bytes] of [w] x [h] pixels, upright, with no metadata, and its [blur] placeholder (see [Placeholder]). */
class EncodedPhoto(val bytes: ByteArray, val w: Int, val h: Int, val blur: String)

/** Why a photo was not taken; the picker turns it into a message. */
enum class PhotoRejection {
    /** More bytes or more pixels than [PhotoLimits] allows; decoding it could exhaust memory. */
    TooLarge,

    /** Not an image this device can decode. */
    Unreadable,

    /** A photo link that could not be fetched: not a web address, no connection, or the site did not give the file. */
    Unreachable,
}

sealed interface PhotoEncodeResult {
    class Encoded(val photo: EncodedPhoto) : PhotoEncodeResult

    class Rejected(val reason: PhotoRejection) : PhotoEncodeResult
}

/**
 * Turns a picked photo into what is stored and sent: decoded within the limits, rotated upright from its EXIF orientation, scaled to at most
 * [PhotoLimits.LongEdge] on the long edge (never up), flattened onto white, and re-encoded as lossy WebP. Re-encoding from pixels is what drops
 * the metadata (GPS position, camera, time). One implementation per platform.
 */
interface PhotoEncoder {
    suspend fun encode(source: PhotoSource): PhotoEncodeResult
}

/** The numbers ADR-023 Spike 3 chose, and the arithmetic every [PhotoEncoder] shares. */
object PhotoLimits {
    const val LongEdge = 1600
    const val Quality = 75
    const val MimeType = "image/webp"

    /** A source file larger than this is refused unread. */
    const val MaxInputBytes = 25L * 1024 * 1024

    /** A source with more pixels than this is refused before it is decoded: 50 megapixels is ~200 MB of RGBA. */
    const val MaxInputPixels = 50L * 1000 * 1000

    /** The longest edge of the image the placeholder is computed from. */
    const val PlaceholderEdge = 32

    val kind = MediaKind.Photo

    /** [w] x [h] shrunk to fit [maxEdge] on the long edge, keeping the shape; never larger than the original. */
    fun fit(w: Int, h: Int, maxEdge: Int = LongEdge): Pair<Int, Int> {
        val longest = max(w, h)
        if (longest <= maxEdge) return w to h
        val scale = maxEdge.toDouble() / longest
        return max(1, (w * scale + 0.5).toInt()) to max(1, (h * scale + 0.5).toInt())
    }

    fun withinPixelLimit(w: Int, h: Int): Boolean = w > 0 && h > 0 && w.toLong() * h <= MaxInputPixels

    /**
     * The placeholder text for an opaque `0xAARRGGBB` image: the image is box-averaged down to [PlaceholderEdge] on the long edge first, which is
     * both what the hash wants (it is slow and no better above 100 pixels) and cheap.
     */
    fun placeholderFor(argb: IntArray, w: Int, h: Int): String {
        val (tw, th) = fit(w, h, PlaceholderEdge)
        return Placeholder.encode(boxDownscale(argb, w, h, tw, th), tw, th)
    }

    /** Averages the source pixels that fall in each target cell; exact for any ratio, and fine for a placeholder. */
    internal fun boxDownscale(src: IntArray, w: Int, h: Int, tw: Int, th: Int): IntArray {
        if (tw == w && th == h) return src
        val out = IntArray(tw * th)
        for (ty in 0 until th) {
            val y0 = ty * h / th
            val y1 = max(y0 + 1, min(h, (ty + 1) * h / th))
            for (tx in 0 until tw) {
                val x0 = tx * w / tw
                val x1 = max(x0 + 1, min(w, (tx + 1) * w / tw))
                var a = 0L
                var r = 0L
                var g = 0L
                var b = 0L
                for (y in y0 until y1) for (x in x0 until x1) {
                    val p = src[y * w + x]
                    a += (p ushr 24) and 255
                    r += (p shr 16) and 255
                    g += (p shr 8) and 255
                    b += p and 255
                }
                val n = (y1 - y0) * (x1 - x0)
                out[ty * tw + tx] = ((a / n).toInt() shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            }
        }
        return out
    }
}

/** What a document stores for a blob (ADR-023 "reference shape"): everything needed to find, fetch, verify and draw a placeholder for it. */
data class BlobReference(
    val hash: String,
    val size: Long,
    val mime: String,
    val kind: MediaKind,
    val w: Int,
    val h: Int,
    val blur: String,
)
