package uk.veritaslabs.vesta.datasync

import org.automerge.SyncState
import uk.veritaslabs.vesta.docs.HomeDocumentRegistry

/**
 * Asks a Home's document whether a peer has acknowledged all of it (ADR-033, "Unsent-change tracking"): finds the document
 * through the [registry], as [DeviceDataSyncService] does, and asks it `isInSync` for that peer's sync state. The
 * answer is true only once the peer has replied with its heads, so a message sent to a device that never answered is not one.
 */
internal class SyncAcknowledgement(private val registry: HomeDocumentRegistry) {
    /** True when the peer whose progress is [state] has everything of [documentId] (a document of [homeId]) this device has; false for a document this device doesn't have. */
    suspend fun isInSync(homeId: String, documentId: String, state: SyncState): Boolean =
        registry.open(homeId, documentId)?.isInSync(state) ?: false
}
