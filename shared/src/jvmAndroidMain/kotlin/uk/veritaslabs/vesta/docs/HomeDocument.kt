package uk.veritaslabs.vesta.docs

import org.automerge.SyncState

/**
 * What a Home-scoped Automerge document gives the sync engine, the mailbox and the acknowledgement check, and nothing
 * more. The engine knows documents only through this, so adding a document type means registering one
 * [HomeDocumentType], not editing the engine.
 *
 * The method names are the ones the per-feature document sources already have, so they can adopt the interface without
 * a logic change.
 */
interface HomeDocument {
    /** The next sync message for the peer behind [state], or null when there is nothing more to say. */
    suspend fun generateSync(state: SyncState): ByteArray?

    /** Applies a sync [message] from the peer behind [state]. */
    suspend fun receiveSync(state: SyncState, message: ByteArray)

    /** True when the peer behind [state] is known to have everything this device has. */
    suspend fun isInSync(state: SyncState): Boolean

    /** The document's current heads, encoded, to be saved as a mailbox position. */
    suspend fun heads(): ByteArray

    /** The changes made since the [saved] heads, encoded. */
    suspend fun changesSince(saved: ByteArray): ByteArray

    /** Applies [changes] produced by [changesSince]. */
    suspend fun applyChanges(changes: ByteArray)
}
