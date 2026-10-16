package uk.veritaslabs.kits.docs

import uk.veritaslabs.kits.KitsInternalApi

import org.automerge.SyncState

/** An Automerge document the hub can sync with a peer, whoever owns and persists it. */
@KitsInternalApi
public interface SyncableDocument {
    /** The next sync message for the peer whose progress is [state], or null when it is up to date. */
    suspend fun generate(state: SyncState): ByteArray?

    /** Applies a sync message from that peer. */
    suspend fun receive(state: SyncState, message: ByteArray)
}
