package uk.veritaslabs.kits.spaces


/** A Space as stored: [kind] is the `Home.shape` column's value (the app's own word for what sort of Space it is). */
data class StoredSpace(val id: String, val name: String, val kind: String, val createdAt: Long)

/**
 * Told by SpaceRepository about every change the user makes to their Spaces, so they can be copied to the user's
 * other devices (docs/adr/ADR-013-device-data-sync.md). Not called for changes that arrive from another device.
 */
interface SpaceChangeListener {
    /** A Space was created or edited. */
    suspend fun saved(space: StoredSpace)

    /** The Space with [id] was deleted. */
    suspend fun deleted(id: String)
}
