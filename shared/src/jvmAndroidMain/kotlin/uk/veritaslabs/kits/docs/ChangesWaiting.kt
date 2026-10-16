package uk.veritaslabs.kits.docs

import uk.veritaslabs.kits.KitsInternalApi

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import java.util.concurrent.ConcurrentHashMap
import uk.veritaslabs.kits.store.KitsDatabase

/** Where a Space's documents could go from this device: [shared] for the Space's own documents (members' devices and hubs), [own] for its private ones (the user's own paired devices). */
@KitsInternalApi
public data class SyncTargets(val shared: Boolean, val own: Boolean)

/**
 * Whether a Space has changes that no other device has acknowledged (docs/adr/ADR-033-sync-activity.md, "Unsent-change tracking").
 * A document is covered when some peer's sync state for it is in sync (the peer replied with its heads), when its current heads
 * are the ones last deposited in the Space's mailbox (a deposit holds it for whoever is away), or when it has never been edited.
 * The Space has changes waiting when a document is not covered and there is somewhere ([SyncTargets]) for it to go; with nowhere,
 * the sync verdict is "only you" and the question does not arise.
 *
 * The answer is worked out only at moments when the service's sync states are whole, never in the middle of clearing them
 * (the service calls [update] at the end of a round and after each message it applies), and read from a cache by [of].
 * [sharedDocumentIds] are the ids the mailbox carries, [privateDocumentIds] the ones only the user's own devices get; [heads] reads a document's current heads, null for one this device
 * doesn't have.
 */
@KitsInternalApi
public class ChangesWaiting(
    private val database: KitsDatabase,
    private val sharedDocumentIds: (spaceId: String) -> List<String>,
    private val privateDocumentIds: (spaceId: String) -> List<String>,
    private val targets: suspend (spaceId: String) -> SyncTargets,
    private val heads: suspend (spaceId: String, documentId: String) -> ByteArray?,
) {
    private val cache = ConcurrentHashMap<String, Boolean>()

    /**
     * Whether [spaceId] has changes waiting, as of the last [update]. Before the first one (a fresh start has no sync states) it is
     * worked out once from the heads alone: waiting when any document differs from what the mailbox was last given.
     */
    suspend fun of(spaceId: String): Boolean = cache[spaceId] ?: evaluate(spaceId, emptySet()).also { cache.putIfAbsent(spaceId, it) }

    /** Works out [spaceId]'s answer afresh; [acknowledged] are the ids of its documents that some peer's sync state reports in sync. */
    suspend fun update(spaceId: String, acknowledged: Set<String>) {
        cache[spaceId] = evaluate(spaceId, acknowledged)
    }

    /** Forgets [spaceId], for when it is deleted or left. */
    fun forget(spaceId: String) {
        cache.remove(spaceId)
    }

    private suspend fun evaluate(spaceId: String, acknowledged: Set<String>): Boolean {
        val reachable = targets(spaceId)
        val shared = if (reachable.shared) sharedDocumentIds(spaceId) else emptyList()
        val own = if (reachable.own) privateDocumentIds(spaceId) else emptyList()
        return shared.any { !isCovered(spaceId, it, it in acknowledged, mailbox = true) } ||
            own.any { !isCovered(spaceId, it, it in acknowledged, mailbox = false) }
    }

    private suspend fun isCovered(spaceId: String, documentId: String, acknowledged: Boolean, mailbox: Boolean): Boolean {
        if (acknowledged) return true
        val current = heads(spaceId, documentId)
        if (current == null || current.isEmpty()) return true // no such document, or never edited
        if (!mailbox) return false
        val deposited = database.mailboxQueries.selectHeads(spaceId, documentId).awaitAsOneOrNull()
        return deposited != null && deposited.contentEquals(current)
    }
}
