package uk.veritaslabs.kits.blobs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * The [Blobs] the screens get in the running app: adds through [ingest], reads from [repository]'s store, and announces arrivals that the transfer
 * service reports through [notifyArrived]. [waitingForWifi] is the transfer service's flag of the same name.
 */
internal class RepositoryBlobs(
    private val ingest: BlobIngest,
    private val repository: BlobRepository,
    override val waitingForWifi: Flow<Boolean> = flowOf(false),
    private val fetcher: PhotoFetcher = HttpPhotoFetcher(),
) : Blobs {

    private val arrived = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override val arrivals: Flow<String> = arrived

    override suspend fun addPhoto(source: PhotoSource): PhotoAddResult =
        when (val result = ingest.addPhoto(source)) {
            is BlobIngestResult.Added -> PhotoAddResult.Added(result.reference)
            is BlobIngestResult.Rejected -> PhotoAddResult.Rejected(result.reason)
        }

    override suspend fun addPhotoFromUrl(url: String): PhotoAddResult =
        when (val fetched = fetcher.fetch(url)) {
            is PhotoFetchResult.Fetched -> addPhoto(PhotoSource.Bytes(fetched.bytes))
            is PhotoFetchResult.Failed -> PhotoAddResult.Rejected(fetched.reason)
        }

    override suspend fun load(hash: String): ByteArray? =
        withContext(Dispatchers.IO) { repository.file(hash)?.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() } }

    /** A blob has just been stored by a transfer; screens waiting on [hash] load it. */
    fun notifyArrived(hash: String) {
        arrived.tryEmit(hash)
    }
}
