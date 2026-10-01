package uk.veritaslabs.vesta.home

/** A Home as stored: [shape] is the `Home.shape` column's value. */
data class StoredHome(val id: String, val name: String, val shape: String, val createdAt: Long)

/**
 * Told by [HomeRepository] about every change the user makes to their Homes, so they can be copied to the user's
 * other devices (docs/adr/ADR-013-device-data-sync.md). Not called for changes that arrive from another device.
 */
interface HomeChangeListener {
    /** A Home was created or edited. */
    suspend fun saved(home: StoredHome)

    /** The Home with [id] was deleted. */
    suspend fun deleted(id: String)
}
