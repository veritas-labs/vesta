package uk.veritaslabs.kits.blobs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoLimitsTest {

    @Test
    fun `fit shrinks the long edge to the limit and keeps the shape`() {
        // Arrange / Act
        val landscape = PhotoLimits.fit(4000, 3000)
        val portrait = PhotoLimits.fit(3000, 4000)
        val panorama = PhotoLimits.fit(16000, 100)

        // Assert
        assertEquals(1600 to 1200, landscape)
        assertEquals(1200 to 1600, portrait)
        assertEquals(1600 to 10, panorama)
    }

    @Test
    fun `fit never enlarges and never reaches zero`() {
        // Arrange / Act
        val small = PhotoLimits.fit(400, 300)
        val sliver = PhotoLimits.fit(100_000, 1)

        // Assert
        assertEquals(400 to 300, small)
        assertEquals(1600 to 1, sliver)
    }

    @Test
    fun `the pixel limit is fifty megapixels`() {
        // Arrange / Act / Assert
        assertTrue(PhotoLimits.withinPixelLimit(8000, 6250))
        assertFalse(PhotoLimits.withinPixelLimit(8000, 6251))
        assertFalse(PhotoLimits.withinPixelLimit(0, 100))
        assertFalse(PhotoLimits.withinPixelLimit(60_000, 60_000))
    }

    @Test
    fun `boxDownscale averages each cell`() {
        // Arrange
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val src = intArrayOf(black, white, black, white)

        // Act
        val one = PhotoLimits.boxDownscale(src, 2, 2, 1, 1)
        val same = PhotoLimits.boxDownscale(src, 2, 2, 2, 2)

        // Assert
        assertEquals(0xFF7F7F7F.toInt(), one.single())
        assertEquals(src.toList(), same.toList())
    }

    @Test
    fun `placeholderFor makes a placeholder from a large image`() {
        // Arrange
        val w = 640
        val h = 480
        val pixels = IntArray(w * h) { if (it % w < w / 2) 0xFFF0F0F0.toInt() else 0xFF101010.toInt() }

        // Act
        val blur = PhotoLimits.placeholderFor(pixels, w, h)

        // Assert
        assertTrue(blur.startsWith("t:"))
        assertTrue(Placeholder.aspectRatio(blur)!! > 1.0)
    }
}
