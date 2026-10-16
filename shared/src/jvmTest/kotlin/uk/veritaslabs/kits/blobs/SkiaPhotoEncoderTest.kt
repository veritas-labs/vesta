package uk.veritaslabs.kits.blobs

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

class SkiaPhotoEncoderTest {

    private val encoder = SkiaPhotoEncoder()

    private fun jpeg(w: Int, h: Int, orientation: Int? = null, secret: String? = null, colour: (Int, Int) -> Int) = testJpeg(w, h, orientation, secret, colour)

    private fun decode(webp: ByteArray): Triple<Int, Int, (Int, Int) -> Int> {
        val image = Image.makeFromEncoded(webp)
        val bitmap = Bitmap().apply { allocN32Pixels(image.width, image.height) }
        check(image.readPixels(bitmap))
        val raw = bitmap.readPixels(ImageInfo(image.width, image.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), image.width * 4, 0, 0)!!
        return Triple(image.width, image.height) { x, y ->
            val o = (y * image.width + x) * 4
            (255 shl 24) or ((raw[o + 2].toInt() and 255) shl 16) or ((raw[o + 1].toInt() and 255) shl 8) or (raw[o].toInt() and 255)
        }
    }

    private fun red(argb: Int) = (argb shr 16) and 255
    private fun blue(argb: Int) = argb and 255

    private suspend fun encoded(bytes: ByteArray): EncodedPhoto {
        val result = encoder.encode(PhotoSource.Bytes(bytes))
        assertIs<PhotoEncodeResult.Encoded>(result)
        return result.photo
    }

    @Test
    fun `a large photo comes out as WebP no larger than the limit with its shape kept`() = runTest {
        // Arrange
        val source = jpeg(3200, 2400) { x, y -> (x / 13 % 256 shl 16) or (y / 9 % 256 shl 8) or 90 }

        // Act
        val photo = encoded(source)

        // Assert
        assertEquals(1600 to 1200, photo.w to photo.h)
        assertEquals("RIFF", photo.bytes.decodeToString(0, 4))
        assertEquals("WEBP", photo.bytes.decodeToString(8, 12))
        assertTrue(photo.bytes.size < source.size)
        val (w, h, _) = decode(photo.bytes)
        assertEquals(1600 to 1200, w to h)
        assertTrue(photo.blur.startsWith("t:"))
    }

    @Test
    fun `a small photo is not enlarged`() = runTest {
        // Arrange
        val source = jpeg(400, 300) { _, _ -> 0x3366CC }

        // Act
        val photo = encoded(source)

        // Assert
        assertEquals(400 to 300, photo.w to photo.h)
    }

    @Test
    fun `an EXIF rotated source comes out upright`() = runTest {
        // Arrange
        // Stored 80x40, left half red and right half blue, flagged "rotate 90 clockwise to display": upright it is 40x80 with red on top.
        val source = jpeg(80, 40, orientation = 6) { x, _ -> if (x < 40) 0xFF0000 else 0x0000FF }

        // Act
        val photo = encoded(source)

        // Assert
        val (w, h, pixel) = decode(photo.bytes)
        assertEquals(40 to 80, w to h)
        assertEquals(40 to 80, photo.w to photo.h)
        assertTrue(red(pixel(20, 10)) > 200 && blue(pixel(20, 10)) < 100, "top should be red, was ${pixel(20, 10).toString(16)}")
        assertTrue(blue(pixel(20, 70)) > 200 && red(pixel(20, 70)) < 100, "bottom should be blue, was ${pixel(20, 70).toString(16)}")
    }

    @Test
    fun `all eight EXIF orientations come out upright`() = runTest {
        // Arrange
        // Raw 80x40 with a different colour in each quarter: TL red, TR green, BL blue, BR yellow.
        val palette = mapOf('R' to 0xFF0000, 'G' to 0x00FF00, 'B' to 0x0000FF, 'Y' to 0xFFFF00)
        fun quarter(x: Int, y: Int) = when {
            x < 40 && y < 20 -> 'R'
            x >= 40 && y < 20 -> 'G'
            x < 40 -> 'B'
            else -> 'Y'
        }
        // Upright corners (TL, TR, BL, BR) per EXIF orientation, from the raw corners R G B Y (the EXIF spec's row-0 / column-0 table).
        val expected = mapOf(1 to "RGBY", 2 to "GRYB", 3 to "YBGR", 4 to "BYRG", 5 to "RBGY", 6 to "BRYG", 7 to "YGBR", 8 to "GYRB")

        expected.forEach { (orientation, corners) ->
            val source = jpeg(80, 40, orientation = orientation) { x, y -> palette.getValue(quarter(x, y)) }

            // Act
            val photo = encoded(source)

            // Assert
            val (w, h, pixel) = decode(photo.bytes)
            val swapped = orientation >= 5
            assertEquals(if (swapped) 40 to 80 else 80 to 40, w to h, "size for orientation $orientation")
            val points = listOf(w / 4 to h / 4, 3 * w / 4 to h / 4, w / 4 to 3 * h / 4, 3 * w / 4 to 3 * h / 4)
            val seen = points.map { (x, y) -> palette.entries.minBy { (_, c) -> dist(pixel(x, y), c) }.key }.joinToString("")
            assertEquals(corners, seen, "corners for orientation $orientation")
        }
    }

    private fun dist(a: Int, b: Int) = listOf(16, 8, 0).sumOf { val d = ((a shr it) and 255) - ((b shr it) and 255); d * d }

    @Test
    fun `the rotation also applies when the photo is scaled down`() = runTest {
        // Arrange
        val source = jpeg(4000, 2000, orientation = 6) { x, _ -> if (x < 2000) 0xFF0000 else 0x0000FF }

        // Act
        val photo = encoded(source)

        // Assert
        val (w, h, pixel) = decode(photo.bytes)
        assertEquals(800 to 1600, w to h)
        assertTrue(red(pixel(400, 200)) > 200, "top should be red")
        assertTrue(blue(pixel(400, 1400)) > 200, "bottom should be blue")
    }

    @Test
    fun `metadata such as a GPS position does not survive`() = runTest {
        // Arrange
        val secret = "GPSLatitude=51.5007N-GPSLongitude=0.1246W"
        val source = jpeg(200, 100, orientation = 1, secret = secret) { _, _ -> 0x808080 }
        check(source.decodeToString().contains(secret))

        // Act
        val photo = encoded(source)

        // Assert
        val text = photo.bytes.decodeToString()
        assertFalse(text.contains("GPSLatitude"))
        assertFalse(text.contains("EXIF"))
        assertFalse(text.contains("Exif"))
    }

    @Test
    fun `the same photo gives the same bytes and placeholder`() = runTest {
        // Arrange
        val source = jpeg(500, 300) { x, y -> (x % 256 shl 16) or (y % 256 shl 8) }

        // Act
        val first = encoded(source)
        val second = encoded(source)

        // Assert
        assertContentEquals(first.bytes, second.bytes)
        assertEquals(first.blur, second.blur)
    }

    @Test
    fun `a file path source works`() = runTest {
        // Arrange
        val file = File.createTempFile("pick", ".jpg").apply { deleteOnExit(); writeBytes(jpeg(300, 200) { _, _ -> 0x112233 }) }

        // Act
        val result = encoder.encode(PhotoSource.FilePath(file.path))

        // Assert
        assertIs<PhotoEncodeResult.Encoded>(result)
        assertEquals(300 to 200, result.photo.w to result.photo.h)
    }

    @Test
    fun `a source over the byte limit is refused unread`() = runTest {
        // Arrange
        val tooBig = ByteArray((PhotoLimits.MaxInputBytes + 1).toInt())

        // Act
        val result = encoder.encode(PhotoSource.Bytes(tooBig))

        // Assert
        assertIs<PhotoEncodeResult.Rejected>(result)
        assertEquals(PhotoRejection.TooLarge, result.reason)
    }

    @Test
    fun `an image over the pixel limit is refused before decoding`() = runTest {
        // Arrange
        // A small JPEG whose frame header is patched to claim 8000 x 7000 (56 MP), so nothing that size is ever allocated.
        val source = jpeg(64, 64) { _, _ -> 0x777777 }
        val sof = (0 until source.size - 8).first { source[it] == 0xFF.toByte() && source[it + 1] == 0xC0.toByte() }
        source[sof + 5] = (7000 shr 8).toByte()
        source[sof + 6] = (7000 and 255).toByte()
        source[sof + 7] = (8000 shr 8).toByte()
        source[sof + 8] = (8000 and 255).toByte()

        // Act
        val result = encoder.encode(PhotoSource.Bytes(source))

        // Assert
        assertIs<PhotoEncodeResult.Rejected>(result)
        assertEquals(PhotoRejection.TooLarge, result.reason)
    }

    @Test
    fun `bytes that are not an image and a missing file are unreadable`() = runTest {
        // Arrange
        val missing = PhotoSource.FilePath(File.createTempFile("gone", ".jpg").also { it.delete() }.path)

        // Act
        val garbage = encoder.encode(PhotoSource.Bytes("not a picture".encodeToByteArray()))
        val empty = encoder.encode(PhotoSource.Bytes(ByteArray(0)))
        val absent = encoder.encode(missing)

        // Assert
        listOf(garbage, empty, absent).forEach {
            assertIs<PhotoEncodeResult.Rejected>(it)
            assertEquals(PhotoRejection.Unreadable, it.reason)
        }
    }
}
