package uk.veritaslabs.kits.docs

import uk.veritaslabs.kits.KitsInternalApi

import org.automerge.SyncState

/**
 * Asks a Space's document whether a peer has acknowledged all of it (ADR-033, "Unsent-change tracking"): finds the document
 * through the [registry], as the sync service does, and asks it `isInSync` for that peer's sync state. The
 * answer is true only once the peer has replied with its heads, so a message sent to a device that never answered is not one.
 */
@KitsInternalApi
public class SyncAcknowledgement(private val registry: SpaceDocumentRegistry) {
    /** True when the peer whose progress is [state] has everything of [documentId] (a document of [spaceId]) this device has; false for a document this device doesn't have. */
    suspend fun isInSync(spaceId: String, documentId: String, state: SyncState): Boolean =
        registry.open(spaceId, documentId)?.isInSync(state) ?: false
}
