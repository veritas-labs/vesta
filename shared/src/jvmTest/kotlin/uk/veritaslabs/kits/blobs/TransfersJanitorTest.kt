package uk.veritaslabs.kits.blobs

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.kits.x0x.testing.FakeX0xNetwork

class TransfersJanitorTest {

    private val now = 10_000_000_000L
    private val directory = Files.createTempDirectory("janitor-transfers").toFile().also { it.deleteOnExit() }
    private val repository = BlobRepository(BlobStore(Files.createTempDirectory("janitor-blobs").toFile().also { it.deleteOnExit() }), openInMemoryDatabase())
    private val arrived = mutableListOf<String>()
    private val janitor = TransfersJanitor(directory, FakeX0xNetwork().node("a"), repository, 15.minutes, { now }, { arrived += it })

    private fun file(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3), ageMinutes: Long = 60) =
        File(directory, name).also { it.writeBytes(bytes); it.setLastModified(now - ageMinutes * 60_000) }

    private fun hashOf(bytes: ByteArray) = BlobStore.sha256Hex(bytes)

    @Test
    fun anOldPartFileIsDeletedAndAYoungOneIsKept() = runBlocking {
        // Arrange
        val old = file("0123456789abcdef.part")
        val young = file("fedcba9876543210.part", ageMinutes = 1)

        // Act
        val dealtWith = janitor.sweep(emptySet())

        // Assert
        assertEquals(1, dealtWith)
        assertFalse(old.exists())
        assertTrue(young.exists())
    }

    @Test
    fun aPartFileOfATransferBeingAcceptedIsKept() = runBlocking {
        // Arrange
        val part = file("abc123.part")

        // Act
        janitor.sweep(accepting = setOf("abc123"))

        // Assert
        assertTrue(part.exists())
    }

    @Test
    fun otherFilesAreLeftAlone() = runBlocking {
        // Arrange
        val other = file("notes.txt")
        val odd = file("12345678_photo.webp")

        // Act
        janitor.sweep(emptySet())

        // Assert
        assertTrue(other.exists())
        assertTrue(odd.exists())
    }

    @Test
    fun anUnclaimedFinishedPhotoThatIsStillWantedIsImported() = runBlocking {
        // Arrange
        val bytes = ByteArray(2_000) { (it * 7).toByte() }
        val hash = hashOf(bytes)
        repository.want(hash, "home-1", bytes.size.toLong(), now)
        val landed = file("deadbeef_$hash.webp", bytes)

        // Act
        janitor.sweep(emptySet())

        // Assert
        assertTrue(repository.has(hash))
        assertContentEquals(bytes, repository.file(hash)!!.readBytes())
        assertTrue(repository.wants().isEmpty())
        assertEquals(listOf(hash), arrived)
        assertFalse(landed.exists())
    }

    @Test
    fun anUnclaimedFinishedPhotoNobodyWantsIsDeleted() = runBlocking {
        // Arrange
        val bytes = ByteArray(500) { it.toByte() }
        val landed = file("deadbeef_${hashOf(bytes)}.webp", bytes)

        // Act
        janitor.sweep(emptySet())

        // Assert
        assertFalse(landed.exists())
        assertFalse(repository.has(hashOf(bytes)))
        assertTrue(arrived.isEmpty())
    }

    @Test
    fun aFinishedFileThatIsNotTheWantedPhotoIsDeletedAndTheWantKept() = runBlocking {
        // Arrange
        val bytes = ByteArray(500) { it.toByte() }
        val hash = hashOf(bytes)
        repository.want(hash, "home-1", bytes.size.toLong(), now)
        val landed = file("deadbeef_$hash.webp", ByteArray(500) { 9 })

        // Act
        janitor.sweep(emptySet())

        // Assert
        assertFalse(landed.exists())
        assertFalse(repository.has(hash))
        assertEquals(listOf(hash), repository.wants().map { it.hash })
    }

    @Test
    fun aFinishedPhotoOfATransferBeingAcceptedIsKept() = runBlocking {
        // Arrange
        val bytes = ByteArray(500) { it.toByte() }
        val landed = file("deadbeef_${hashOf(bytes)}.webp", bytes)

        // Act
        janitor.sweep(accepting = setOf("deadbeef-0000-0000-0000-000000000000"))

        // Assert
        assertTrue(landed.exists())
    }

    @Test
    fun aMissingDirectoryIsNotAnError() = runBlocking {
        // Arrange
        val gone = TransfersJanitor(File(directory, "nope"), FakeX0xNetwork().node("a"), repository)

        // Act
        val dealtWith = gone.sweep(emptySet())

        // Assert
        assertEquals(0, dealtWith)
    }
}
