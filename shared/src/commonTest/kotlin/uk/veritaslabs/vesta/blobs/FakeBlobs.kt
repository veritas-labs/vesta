package uk.veritaslabs.vesta.blobs

import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import uk.veritaslabs.vesta.blobs.MediaKind

/** A 1x1 PNG, a real image any platform's decoder reads, standing in for a stored photo. */
val TinyImageBytes: ByteArray = Base64.Default.decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
)

fun testBlobReference(hash: String = "a".repeat(64), blur: String = Placeholder.encode(IntArray(12) { 0xFF336699.toInt() }, 4, 3)) =
    BlobReference(hash, TinyImageBytes.size.toLong(), "image/webp", MediaKind.Photo, 4, 3, blur)

/** In-memory [Blobs]: [held] are the photos on this device, [addResult] is what adding a photo answers. */
class FakeBlobs(
    val held: MutableMap<String, ByteArray> = mutableMapOf(),
    var addResult: PhotoAddResult = PhotoAddResult.Added(testBlobReference("b".repeat(64))),
) : Blobs {
    private val arrived = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val added = mutableListOf<PhotoSource>()

    override val arrivals: Flow<String> = arrived

    /** Whether the photos not held are waiting for Wi-Fi. */
    val wifi = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val waitingForWifi: Flow<Boolean> = wifi

    override suspend fun addPhoto(source: PhotoSource): PhotoAddResult {
        added += source
        return addResult
    }

    val addedUrls = mutableListOf<String>()

    override suspend fun addPhotoFromUrl(url: String): PhotoAddResult {
        addedUrls += url
        return addResult
    }

    override suspend fun load(hash: String): ByteArray? = held[hash]

    /** The photo [hash] has just arrived from another device. */
    fun arrive(hash: String, bytes: ByteArray = TinyImageBytes) {
        held[hash] = bytes
        arrived.tryEmit(hash)
    }
}
