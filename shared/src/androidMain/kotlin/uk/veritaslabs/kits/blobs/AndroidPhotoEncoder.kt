package uk.veritaslabs.kits.blobs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Android [PhotoEncoder]. Never decodes a photo at full size: it reads the bounds first (and refuses over [PhotoLimits.MaxInputPixels]),
 * then decodes with the largest `inSampleSize` that keeps the long edge at or above [PhotoLimits.LongEdge], and finishes the scale and the EXIF
 * rotation in one draw onto a white bitmap. Re-encoding from pixels is what drops the metadata.
 */
class AndroidPhotoEncoder : PhotoEncoder {

    override suspend fun encode(source: PhotoSource): PhotoEncodeResult = withContext(Dispatchers.Default) {
        if (source.exceedsByteLimit()) return@withContext PhotoEncodeResult.Rejected(PhotoRejection.TooLarge)
        val bytes = source.readBytesWithinLimit() ?: return@withContext PhotoEncodeResult.Rejected(PhotoRejection.Unreadable)
        runCatching { encodeBytes(bytes) }.getOrElse { PhotoEncodeResult.Rejected(PhotoRejection.Unreadable) }
    }

    private fun encodeBytes(bytes: ByteArray): PhotoEncodeResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0) return PhotoEncodeResult.Rejected(PhotoRejection.Unreadable)
        if (!PhotoLimits.withinPixelLimit(rawW, rawH)) return PhotoEncodeResult.Rejected(PhotoRejection.TooLarge)

        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        var sample = 1
        while (max(rawW, rawH) / (sample * 2) >= PhotoLimits.LongEdge) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return PhotoEncodeResult.Rejected(PhotoRejection.Unreadable)

        try {
            val matrix = uprightMatrix(orientation, decoded.width, decoded.height)
            val upright = RectF(0f, 0f, decoded.width.toFloat(), decoded.height.toFloat()).also { matrix.mapRect(it) }
            // Scale from the original dimensions, not the sampled ones, so the output size does not depend on the sample size.
            val swaps = orientation in QuarterTurns
            val (w, h) = PhotoLimits.fit(if (swaps) rawH else rawW, if (swaps) rawW else rawH)
            matrix.postScale(w / upright.width(), h / upright.height())

            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                Canvas(out).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(decoded, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                }
                val webp = ByteArrayOutputStream().also {
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    if (!out.compress(format, PhotoLimits.Quality, it)) return PhotoEncodeResult.Rejected(PhotoRejection.Unreadable)
                }.toByteArray()
                val argb = IntArray(w * h).also { out.getPixels(it, 0, w, 0, 0, w, h) }
                return PhotoEncodeResult.Encoded(EncodedPhoto(webp, w, h, PhotoLimits.placeholderFor(argb, w, h)))
            } finally {
                out.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    private companion object {
        val QuarterTurns = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )

        /** The matrix that turns an image stored with EXIF [orientation] upright, moved so the result starts at the origin. */
        fun uprightMatrix(orientation: Int, w: Int, h: Int): Matrix {
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.setRotate(180f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
            }
            val bounds = RectF(0f, 0f, w.toFloat(), h.toFloat()).also { m.mapRect(it) }
            m.postTranslate(-bounds.left, -bounds.top)
            return m
        }
    }
}
