package uk.veritaslabs.vesta.blobs

import kotlinx.coroutines.flow.Flow

/**
 * Where the blob service finds out which blobs a Home's documents refer to, so it needs to know nothing about the
 * documents themselves (docs/plans/kits-extraction-implementation-plan.md, WP16): the app says which document holds
 * the references (Vesta: the cookbook, see `CookbookBlobReferences`).
 */
interface BlobReferenceSource {

    /** The id of a Home each time its references may have changed because of an edit made on this device. */
    val localChanges: Flow<String>

    /**
     * Every blob [homeId]'s documents refer to, so a missing one can be wanted from peers and a held one kept. Null when
     * this device cannot read them (no such Home, or its document is not open yet): that is not the same as "no
     * references", and garbage collection must not treat it as such.
     */
    suspend fun references(homeId: String): Set<BlobReference>?

    /**
     * Calls [onChange] each time [homeId]'s references may have changed, including by a change that arrives from another
     * device, until cancelled. Returns at once if there is nothing to watch.
     */
    suspend fun watch(homeId: String, onChange: suspend () -> Unit)
}
