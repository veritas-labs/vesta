package uk.veritaslabs.vesta.location

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmInflaterTest {

    private fun deflate(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    @Test
    fun roundTripsThroughJavaUtilZip() {
        // Arrange
        val original = ByteArray(300_000) { (it * 31 % 251).toByte() }

        // Act
        val inflated = JvmInflater().inflate(deflate(original))

        // Assert
        assertContentEquals(original, inflated)
    }

    @Test
    fun rejectsTruncatedAndGarbageInput() {
        // Arrange
        val truncated = deflate(ByteArray(10_000) { it.toByte() }).let { it.copyOf(it.size / 2) }
        val garbage = byteArrayOf(1, 2, 3, 4, 5)

        // Act
        val first = assertFailsWith<IllegalArgumentException> { JvmInflater().inflate(truncated) }
        val second = assertFailsWith<IllegalArgumentException> { JvmInflater().inflate(garbage) }

        // Assert
        assertEquals(true, first.message?.isNotBlank())
        assertEquals(true, second.message?.isNotBlank())
    }

    @Test
    fun decodesAZlibCompressedPack() {
        // Arrange
        val names = deflate(TinyPack.namesChunk())
        val adjacency = deflate(TinyPack.adjacencyChunk())

        // Act
        val pack = LocalityPack.parse(names, adjacency, JvmInflater())

        // Assert
        assertEquals(TinyPack.specs.size, pack.size)
        assertEquals("Köln", pack.name(6))
    }
}
