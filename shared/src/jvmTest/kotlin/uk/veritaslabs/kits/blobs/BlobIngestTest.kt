package uk.veritaslabs.kits.blobs

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.kits.blobs.MediaKind

class BlobIngestTest {

    private val store = BlobStore(Files.createTempDirectory("ingest").toFile().also { it.deleteOnExit() })
    private val repository = BlobRepository(store, openInMemoryDatabase())

    private class FakeEncoder(var result: PhotoEncodeResult) : PhotoEncoder {
        var calls = 0
        override suspend fun encode(source: PhotoSource): PhotoEncodeResult {
            calls++
            return result
        }
    }

    private val webp = "RIFF....WEBPfake".encodeToByteArray()

    @Test
    fun `adding a photo stores the WebP once and returns its reference`() = runTest {
        // Arrange
        val ingest = BlobIngest(FakeEncoder(PhotoEncodeResult.Encoded(EncodedPhoto(webp, 800, 600, "t:abc"))), repository) { 1234L }

        // Act
        val result = ingest.addPhoto(PhotoSource.Bytes(byteArrayOf(1)))

        // Assert
        assertIs<BlobIngestResult.Added>(result)
        val ref = result.reference
        assertEquals(BlobReference(BlobStore.sha256Hex(webp), webp.size.toLong(), "image/webp", MediaKind.Photo, 800, 600, "t:abc"), ref)
        assertContentEquals(webp, repository.file(ref.hash)!!.readBytes())
        assertEquals("image/webp", repository.mimeOf(ref.hash))
    }

    @Test
    fun `adding the same photo twice gives one stored copy and one hash`() = runTest {
        // Arrange
        val ingest = BlobIngest(FakeEncoder(PhotoEncodeResult.Encoded(EncodedPhoto(webp, 8, 6, "t:abc"))), repository) { 1L }

        // Act
        val first = (ingest.addPhoto(PhotoSource.Bytes(byteArrayOf(1))) as BlobIngestResult.Added).reference
        val second = (ingest.addPhoto(PhotoSource.Bytes(byteArrayOf(1))) as BlobIngestResult.Added).reference

        // Assert
        assertEquals(first, second)
        assertEquals(1, store.all().size)
    }

    @Test
    fun `a rejected photo stores nothing and passes the reason on`() = runTest {
        // Arrange
        val ingest = BlobIngest(FakeEncoder(PhotoEncodeResult.Rejected(PhotoRejection.TooLarge)), repository) { 1L }

        // Act
        val result = ingest.addPhoto(PhotoSource.Bytes(byteArrayOf(1)))

        // Assert
        assertIs<BlobIngestResult.Rejected>(result)
        assertEquals(PhotoRejection.TooLarge, result.reason)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `a real desktop encode feeds the store end to end`() = runTest {
        // Arrange
        val image = java.awt.image.BufferedImage(120, 80, java.awt.image.BufferedImage.TYPE_INT_RGB).apply { for (x in 0 until 120) for (y in 0 until 80) setRGB(x, y, x * 2) }
        val jpeg = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "jpg", it) }.toByteArray()
        val ingest = BlobIngest(SkiaPhotoEncoder(), repository) { 1L }

        // Act
        val result = ingest.addPhoto(PhotoSource.Bytes(jpeg))

        // Assert
        assertIs<BlobIngestResult.Added>(result)
        val ref = result.reference
        assertEquals(BlobStore.sha256Hex(repository.file(ref.hash)!!.readBytes()), ref.hash)
        assertEquals(120 to 80, ref.w to ref.h)
    }
}
