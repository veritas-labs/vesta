package uk.veritaslabs.vesta.blobs

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

sealed interface PhotoAddResult {
    class Added(val reference: BlobReference) : PhotoAddResult

    class Rejected(val reason: PhotoRejection) : PhotoAddResult
}

/**
 * What the screens need from the blob machinery (ADR-023), so they never see the store, the encoder or the transfer service:
 * add a picked photo, read the bytes of a photo this device holds, and learn when one arrives from another device.
 */
interface Blobs {
    /** Encodes and stores a picked photo; the reference to put in the document comes back. */
    suspend fun addPhoto(source: PhotoSource): PhotoAddResult

    /** The bytes of the blob [hash] if this device holds it, otherwise null (its placeholder is shown instead). */
    suspend fun load(hash: String): ByteArray?

    /** The hash of each blob that has just been stored on this device by a transfer from another one. */
    val arrivals: Flow<String>

    /**
     * True while photos are wanted but x0x may not use the mobile connection this device is on (Settings > Network), so a photo that is not here yet
     * is waiting for Wi-Fi rather than for another device.
     */
    val waitingForWifi: Flow<Boolean> get() = flowOf(false)
}

/** The [Blobs] of the running app; null where there is none (previews, iOS), in which case photos can't be added and show their placeholder. */
val LocalBlobs = staticCompositionLocalOf<Blobs?> { null }
