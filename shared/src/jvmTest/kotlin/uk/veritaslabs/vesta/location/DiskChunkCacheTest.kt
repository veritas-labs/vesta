package uk.veritaslabs.vesta.location

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DiskChunkCacheTest {

    private val dir = Files.createTempDirectory("chunk-cache").toFile().also { it.deleteOnExit() }

    @Test
    fun roundTripsBytesAndLeavesNoTempFiles() {
        // Arrange
        val nested = File(dir, "nested")
        val bytes = byteArrayOf(1, 2, 3, 0, -1)

        // Act
        DiskChunkCache(nested).put("abc123", bytes)
        val read = DiskChunkCache(nested).get("abc123")

        // Assert
        assertContentEquals(bytes, read)
        assertEquals(listOf("abc123"), nested.list().orEmpty().toList())
    }

    @Test
    fun aMissingEntryIsNull() {
        // Arrange
        val cache = DiskChunkCache(dir)

        // Act
        val read = cache.get("ffff")

        // Assert
        assertNull(read)
    }

    @Test
    fun anUnreadableEntryIsIgnored() {
        // Arrange
        val cache = DiskChunkCache(dir)
        File(dir, "dead").mkdirs() // a directory where the file should be

        // Act
        val read = cache.get("dead")

        // Assert
        assertNull(read)
    }

    @Test
    fun anAddressThatIsNotAFileNameIsRefused() {
        // Arrange
        val cache = DiskChunkCache(dir)

        // Act
        cache.put("../escape", byteArrayOf(1))

        // Assert
        assertNull(cache.get("../escape"))
        assertFalse(File(dir.parentFile, "escape").exists())
    }
}
