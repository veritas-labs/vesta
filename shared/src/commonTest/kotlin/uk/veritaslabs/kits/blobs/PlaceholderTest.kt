package uk.veritaslabs.kits.blobs

import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceholderTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun image(w: Int, h: Int, pixel: (Int, Int) -> Int) = IntArray(w * h) { pixel(it % w, it / w) }

    private fun hex(value: String): String =
        Base64.Default.withPadding(Base64.PaddingOption.ABSENT).decode(value.removePrefix("t:")).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private fun Int.channels() = listOf((this ushr 24) and 255, (this shr 16) and 255, (this shr 8) and 255, this and 255)

    // The reference vectors below come from the thumbhash-python 1.0.1 port of Evan Wallace's reference encoder and decoder.

    @Test
    fun `a landscape gradient encodes to the reference hash and decodes to the reference size`() {
        // Arrange
        val pixels = image(8, 6) { x, y -> argb(x * 30, y * 40, 128) }

        // Act
        val value = Placeholder.encode(pixels, 8, 6)
        val decoded = Placeholder.decode(value)!!

        // Assert
        assertEquals("1b070a35987087777078788778788887707007f788", hex(value))
        assertEquals(32 to 23, decoded.w to decoded.h)
        assertEquals(listOf(255, 0, 0, 147), decoded.argb[0].channels())
        assertEquals(listOf(255, 199, 187, 113), decoded.argb.last().channels())
    }

    @Test
    fun `a portrait image encodes to the reference hash and decodes to the reference size`() {
        // Arrange
        val pixels = image(6, 8) { x, y ->
            if (x < 3) (if (y < 4) argb(255, 40, 40) else argb(30, 60, 220)) else argb(240, 240, 60)
        }

        // Act
        val value = Placeholder.encode(pixels, 6, 8)
        val decoded = Placeholder.decode(value)!!

        // Assert
        assertEquals("645a0e8d14707a88787788788778887870bcd8f88c", hex(value))
        assertEquals(23 to 32, decoded.w to decoded.h)
        assertEquals(listOf(255, 255, 2, 60), decoded.argb[0].channels())
        assertEquals(listOf(255, 213, 255, 16), decoded.argb.last().channels())
    }

    @Test
    fun `an image with transparency encodes to the reference hash and keeps its alpha`() {
        // Arrange
        val pixels = image(5, 5) { x, _ -> argb(200, 50, 90, if (x >= 2) 255 else 40) }

        // Act
        val value = Placeholder.encode(pixels, 5, 5)
        val decoded = Placeholder.decode(value)!!

        // Assert
        // A varying term lands exactly on 8.5 here: JS Math.round (which we follow) gives 9, Python's banker's rounding 8, hence the one differing nibble from the stock port.
        assertEquals("1c298305004a9f2889778678479f89f888a750a98888888878", hex(value))
        assertEquals(32 to 32, decoded.w to decoded.h)
        assertEquals(listOf(133, 200, 51, 89), decoded.argb[0].channels())
        assertEquals(listOf(255, 200, 51, 89), decoded.argb.last().channels())
    }

    @Test
    fun `a stored value carries the scheme prefix`() {
        // Arrange
        val pixels = image(4, 4) { _, _ -> argb(10, 20, 30) }

        // Act
        val value = Placeholder.encode(pixels, 4, 4)

        // Assert
        assertTrue(value.startsWith("t:"))
        assertTrue(value.length < 60)
    }

    @Test
    fun `round trip keeps the colour of a flat image within tolerance`() {
        // Arrange
        val pixels = image(10, 10) { _, _ -> argb(200, 80, 40) }

        // Act
        val decoded = Placeholder.decode(Placeholder.encode(pixels, 10, 10))!!

        // Assert
        val centre = decoded.argb[decoded.w * (decoded.h / 2) + decoded.w / 2].channels()
        assertEquals(255, centre[0])
        listOf(200, 80, 40).forEachIndexed { i, expected -> assertTrue(abs(centre[i + 1] - expected) <= 8, "channel $i was ${centre[i + 1]}") }
    }

    @Test
    fun `round trip keeps which half of an image is light`() {
        // Arrange
        val pixels = image(20, 10) { x, _ -> if (x < 10) argb(250, 250, 250) else argb(10, 10, 10) }

        // Act
        val decoded = Placeholder.decode(Placeholder.encode(pixels, 20, 10))!!

        // Assert
        val row = decoded.h / 2
        val left = decoded.argb[row * decoded.w + 3].channels()[1]
        val right = decoded.argb[row * decoded.w + decoded.w - 4].channels()[1]
        assertTrue(left > 180 && right < 80, "left $left right $right")
    }

    @Test
    fun `the aspect ratio of the source survives`() {
        // Arrange
        val wide = Placeholder.encode(image(40, 10) { x, y -> argb(x * 6, y * 20, 90) }, 40, 10)
        val tall = Placeholder.encode(image(10, 40) { x, y -> argb(x * 20, y * 6, 90) }, 10, 40)

        // Act
        val wideRatio = Placeholder.aspectRatio(wide)!!
        val tallRatio = Placeholder.aspectRatio(tall)!!

        // Assert
        assertTrue(wideRatio > 1.0)
        assertTrue(tallRatio < 1.0)
        assertTrue(Placeholder.decode(wide)!!.let { it.w > it.h })
        assertTrue(Placeholder.decode(tall)!!.let { it.h > it.w })
    }

    @Test
    fun `malformed values decode to null and never throw`() {
        // Arrange
        val cases = listOf(
            "", "t:", "t:!!!!", "t:AAAA", "b:LEHV6nWB2yk8pyo0adR*.7kCMdnj", "x", "t:" + "A".repeat(7),
            // header says alpha but the hash ends at five bytes
            "t:" + Base64.Default.withPadding(Base64.PaddingOption.ABSENT).encode(byteArrayOf(0, 0, 0x80.toByte(), 0, 0)),
            // claims many varying terms but carries none
            "t:" + Base64.Default.withPadding(Base64.PaddingOption.ABSENT).encode(byteArrayOf(63, 0, 0, 7, 0)),
        )

        // Act
        val results = cases.map { Placeholder.decode(it) }

        // Assert
        results.forEachIndexed { i, r -> assertNull(r, "case $i") }
        assertNull(Placeholder.aspectRatio("garbage"))
    }

    @Test
    fun `random bytes never make decode throw`() {
        // Arrange
        val random = kotlin.random.Random(7)

        // Act
        val results = (0 until 500).map {
            val bytes = random.nextBytes(random.nextInt(0, 40))
            Placeholder.decode("t:" + Base64.Default.withPadding(Base64.PaddingOption.ABSENT).encode(bytes))
        }

        // Assert
        assertTrue(results.any { it != null })
        results.filterNotNull().forEach { assertTrue(it.w in 1..32 && it.h in 1..32 && it.argb.size == it.w * it.h) }
        assertNotNull(results)
    }

    @Test
    fun `encode rejects an oversized or mismatched image`() {
        // Arrange / Act / Assert
        assertFailsWith<IllegalArgumentException> { Placeholder.encode(IntArray(101 * 10), 101, 10) }
        assertFailsWith<IllegalArgumentException> { Placeholder.encode(IntArray(5), 4, 4) }
        assertFailsWith<IllegalArgumentException> { Placeholder.encode(IntArray(0), 0, 0) }
    }
}
