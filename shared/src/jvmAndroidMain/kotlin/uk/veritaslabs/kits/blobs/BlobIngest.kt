package uk.veritaslabs.kits.blobs

internal sealed interface BlobIngestResult {
    class Added(val reference: BlobReference) : BlobIngestResult

    class Rejected(val reason: PhotoRejection) : BlobIngestResult
}

/**
 * Adds a photo a member picked: encode it ([PhotoEncoder]), store the WebP once ([BlobRepository], which also notes it as just referenced),
 * and hand back the reference to write into the document. The same photo picked twice gives the same hash and stores one copy.
 */
internal class BlobIngest(
    private val encoder: PhotoEncoder,
    private val repository: BlobRepository,
    private val now: () -> Long,
) {
    suspend fun addPhoto(source: PhotoSource): BlobIngestResult =
        when (val result = encoder.encode(source)) {
            is PhotoEncodeResult.Rejected -> BlobIngestResult.Rejected(result.reason)
            is PhotoEncodeResult.Encoded -> {
                val photo = result.photo
                val info = repository.put(photo.bytes, PhotoLimits.MimeType, now())
                BlobIngestResult.Added(BlobReference(info.hash, info.size, PhotoLimits.MimeType, PhotoLimits.kind, photo.w, photo.h, photo.blur))
            }
        }
}
