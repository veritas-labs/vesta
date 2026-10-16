package uk.veritaslabs.kits.blobs

import kotlinx.coroutines.flow.Flow

/**
 * Where the blob service finds out which blobs a Space's documents refer to, so it needs to know nothing about the
 * documents themselves: the app says which document holds
 * the references.
 */
interface BlobReferenceSource {

    /** The id of a Space each time its references may have changed because of an edit made on this device. */
    val localChanges: Flow<String>

    /**
     * Every blob [spaceId]'s documents refer to, so a missing one can be wanted from peers and a held one kept. Null when
     * this device cannot read them (no such Space, or its document is not open yet): that is not the same as "no
     * references", and garbage collection must not treat it as such.
     */
    suspend fun references(spaceId: String): Set<BlobReference>?

    /**
     * Calls [onChange] each time [spaceId]'s references may have changed, including by a change that arrives from another
     * device, until cancelled. Returns at once if there is nothing to watch.
     */
    suspend fun watch(spaceId: String, onChange: suspend () -> Unit)
}
