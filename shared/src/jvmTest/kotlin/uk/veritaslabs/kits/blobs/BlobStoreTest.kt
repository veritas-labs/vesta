package uk.veritaslabs.kits.blobs

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlobStoreTest {

    private val root = Files.createTempDirectory("blobstore").toFile().also { it.deleteOnExit() }
    private val store = BlobStore(root)
    private val scratch = Files.createTempDirectory("blobscratch").toFile().also { it.deleteOnExit() }

    private fun source(bytes: ByteArray) = File.createTempFile("src", ".webp", scratch).also { it.writeBytes(bytes) }

    private fun allFiles(dir: File = root): List<File> = dir.walkTopDown().filter { it.isFile }.toList()

    @Test
    fun `put stores the bytes under their sha256 and reads back`() {
        // Arrange
        val bytes = "hello".encodeToByteArray()

        // Act
        val info = store.put(bytes)

        // Assert
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", info.hash)
        assertEquals(5L, info.size)
        assertTrue(store.has(info.hash))
        assertContentEquals(bytes, store.file(info.hash)!!.readBytes())
        assertEquals("2c", store.file(info.hash)!!.parentFile.name)
    }

    @Test
    fun `putting the same bytes twice is idempotent and leaves no temp file`() {
        // Arrange
        val bytes = byteArrayOf(1, 2, 3)

        // Act
        val first = store.put(bytes)
        val second = store.put(bytes)

        // Assert
        assertEquals(first, second)
        assertEquals(listOf(first.hash), store.all().map { it.hash })
        assertEquals(1, allFiles().size)
    }

    @Test
    fun `importFile moves a verified file into the store and removes the source`() {
        // Arrange
        val bytes = ByteArray(200_000) { (it * 7).toByte() }
        val hash = BlobStore.sha256Hex(bytes)
        val src = source(bytes)

        // Act
        val result = store.importFile(src.path, hash, bytes.size.toLong())

        // Assert
        assertEquals(BlobImport.Imported(BlobInfo(hash, bytes.size.toLong()), alreadyHeld = false), result)
        assertContentEquals(bytes, store.file(hash)!!.readBytes())
        assertFalse(src.exists())
    }

    @Test
    fun `importFile with the wrong hash discards the source and stores nothing`() {
        // Arrange
        val src = source(byteArrayOf(1, 2, 3))
        val wanted = BlobStore.sha256Hex(byteArrayOf(9))

        // Act
        val result = store.importFile(src.path, wanted, 3)

        // Assert
        assertIs<BlobImport.Mismatch>(result)
        assertFalse(store.has(wanted))
        assertFalse(src.exists())
        assertTrue(allFiles().isEmpty())
    }

    @Test
    fun `importFile with the wrong size is a mismatch even when the hash matches`() {
        // Arrange
        val bytes = byteArrayOf(1, 2, 3)
        val src = source(bytes)

        // Act
        val result = store.importFile(src.path, BlobStore.sha256Hex(bytes), 99)

        // Assert
        assertIs<BlobImport.Mismatch>(result)
        assertTrue(allFiles().isEmpty())
    }

    @Test
    fun `importFile of a missing file reports it and a malformed hash is a mismatch`() {
        // Arrange
        val src = source(byteArrayOf(1))

        // Act
        val missing = store.importFile(File(scratch, "nope").path, BlobStore.sha256Hex(byteArrayOf(1)), 1)
        val malformed = store.importFile(src.path, "../../etc/passwd", 1)

        // Assert
        assertEquals(BlobImport.SourceMissing, missing)
        assertIs<BlobImport.Mismatch>(malformed)
        assertTrue(allFiles().isEmpty())
    }

    @Test
    fun `importing a blob already held keeps one copy and removes the source`() {
        // Arrange
        val bytes = byteArrayOf(5, 5, 5)
        val info = store.put(bytes)
        val src = source(bytes)

        // Act
        val result = store.importFile(src.path, info.hash, info.size)

        // Assert
        assertEquals(BlobImport.Imported(info, alreadyHeld = true), result)
        assertFalse(src.exists())
        assertEquals(1, allFiles().size)
    }

    @Test
    fun `a crash between the temp write and the rename leaves nothing visible and the sweep clears it`() {
        // Arrange
        val hash = BlobStore.sha256Hex(byteArrayOf(4))
        File(root, "tmp/import123.part").writeBytes(byteArrayOf(4))

        // Act
        val visibleBefore = store.has(hash) || store.all().isNotEmpty()
        store.sweepTemporaryFiles()

        // Assert
        assertFalse(visibleBefore)
        assertTrue(allFiles().isEmpty())
    }

    @Test
    fun `a name that is not a sha256 never maps to a path`() {
        // Arrange
        val names = listOf("../x", "ABCDEF", "", "a".repeat(63), "g".repeat(64))

        // Act
        val files = names.map { store.file(it) }

        // Assert
        assertTrue(files.all { it == null })
        assertNull(store.file("A".repeat(64)))
    }

    @Test
    fun `delete removes the blob`() {
        // Arrange
        val info = store.put(byteArrayOf(8))

        // Act
        store.delete(info.hash)

        // Assert
        assertFalse(store.has(info.hash))
    }
}
