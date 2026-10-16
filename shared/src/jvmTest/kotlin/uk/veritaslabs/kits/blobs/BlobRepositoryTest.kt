package uk.veritaslabs.kits.blobs

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase

class BlobRepositoryTest {

    private val root = Files.createTempDirectory("blobrepo").toFile().also { it.deleteOnExit() }
    private val store = BlobStore(root)
    private val database = openInMemoryDatabase()
    private val repository = BlobRepository(store, database)

    private fun hashOf(vararg bytes: Byte) = BlobStore.sha256Hex(bytes)

    @Test
    fun `put records the blob and its mime`() = runTest {
        // Arrange
        val bytes = byteArrayOf(1, 2, 3)

        // Act
        val info = repository.put(bytes, "image/webp", now = 100)

        // Assert
        assertTrue(repository.has(info.hash))
        assertEquals("image/webp", repository.mimeOf(info.hash))
    }

    @Test
    fun `want records a missing hash once and keeps its first time and counters`() = runTest {
        // Arrange
        val hash = hashOf(1)
        repository.want(hash, "home-1", 10, now = 100)
        repository.recordAsk(hash, now = 150)

        // Act
        repository.want(hash, "home-1", 10, now = 200)

        // Assert
        assertEquals(listOf(BlobWant(hash, "home-1", 10, firstWantedAt = 100, lastAskedAt = 150, attempts = 1)), repository.wants())
    }

    @Test
    fun `want ignores a hash already held`() = runTest {
        // Arrange
        val info = repository.put(byteArrayOf(1), "image/webp", now = 1)

        // Act
        repository.want(info.hash, "home-1", 1, now = 2)

        // Assert
        assertTrue(repository.wants().isEmpty())
    }

    @Test
    fun `markHave and a successful import clear the want`() = runTest {
        // Arrange
        val bytes = byteArrayOf(7, 7)
        val hash = BlobStore.sha256Hex(bytes)
        val other = hashOf(9)
        repository.want(hash, "home-1", 2, now = 1)
        repository.want(other, "home-1", 2, now = 1)
        val src = File.createTempFile("recv", ".webp").also { it.writeBytes(bytes) }

        // Act
        val imported = repository.importFile(src.path, hash, 2, "image/webp", now = 5)
        repository.markHave(other)

        // Assert
        assertIs<BlobImport.Imported>(imported)
        assertTrue(repository.has(hash))
        assertEquals("image/webp", repository.mimeOf(hash))
        assertTrue(repository.wants().isEmpty())
    }

    @Test
    fun `a mismatched import keeps the want and holds nothing`() = runTest {
        // Arrange
        val hash = hashOf(1)
        repository.want(hash, "home-1", 1, now = 1)
        val src = File.createTempFile("recv", ".webp").also { it.writeBytes(byteArrayOf(2)) }

        // Act
        val result = repository.importFile(src.path, hash, 1, "image/webp", now = 5)

        // Assert
        assertIs<BlobImport.Mismatch>(result)
        assertFalse(repository.has(hash))
        assertNull(repository.mimeOf(hash))
        assertEquals(listOf(hash), repository.wants().map { it.hash })
    }

    @Test
    fun `resetBackoff makes every want askable again`() = runTest {
        // Arrange
        val hash = hashOf(1)
        repository.want(hash, "home-1", 1, now = 1)
        repository.recordAsk(hash, now = 50)
        repository.recordAsk(hash, now = 90)

        // Act
        repository.resetBackoff()

        // Assert
        val want = repository.wants().single()
        assertEquals(0L, want.lastAskedAt)
        assertEquals(0L, want.attempts)
    }

    @Test
    fun `touch moves the last referenced time and unreferencedSince finds the stale ones`() = runTest {
        // Arrange
        val old = repository.put(byteArrayOf(1), "image/webp", now = 100)
        val kept = repository.put(byteArrayOf(2), "image/webp", now = 100)

        // Act
        repository.touch(kept.hash, now = 900)

        // Assert
        assertEquals(listOf(old.hash), repository.unreferencedSince(cutoff = 500))
        assertTrue(repository.unreferencedSince(cutoff = 50).isEmpty())
    }

    @Test
    fun `delete removes the file and the row`() = runTest {
        // Arrange
        val info = repository.put(byteArrayOf(1), "image/webp", now = 1)

        // Act
        repository.delete(info.hash)

        // Assert
        assertFalse(repository.has(info.hash))
        assertNull(repository.mimeOf(info.hash))
    }

    @Test
    fun `reconcile adopts an orphan file, drops a row without a file, clears a satisfied want and sweeps temp files`() = runTest {
        // Arrange
        val orphan = store.put(byteArrayOf(1))
        val lost = repository.put(byteArrayOf(2), "image/webp", now = 1)
        store.delete(lost.hash)
        val held = store.put(byteArrayOf(3))
        repository.want(hash = hashOf(4), spaceId = "home-1", size = 1, now = 1)
        database.blobQueries.insertWantIfAbsent(held.hash, "home-1", 1, 1)
        File(root, "tmp/stray.part").writeBytes(byteArrayOf(0))

        // Act
        repository.reconcile(now = 777)

        // Assert
        assertEquals("application/octet-stream", repository.mimeOf(orphan.hash))
        assertNull(repository.mimeOf(lost.hash))
        assertEquals(listOf(hashOf(4)), repository.wants().map { it.hash })
        assertTrue(File(root, "tmp").listFiles()!!.isEmpty())
    }
}
