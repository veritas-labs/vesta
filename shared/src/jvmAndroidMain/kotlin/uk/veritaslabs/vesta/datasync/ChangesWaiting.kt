package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import java.util.concurrent.ConcurrentHashMap
import uk.veritaslabs.vesta.db.VestaDatabase

/** Where a Home's documents could go from this device: [shared] for the Home's own documents (members' devices and hubs), [own] for its private ones (the user's own paired devices). */
internal data class SyncTargets(val shared: Boolean, val own: Boolean)

/**
 * Whether a Home has changes that no other device has acknowledged (docs/adr/ADR-033-sync-activity.md, "Unsent-change tracking").
 * A document is covered when some peer's sync state for it is in sync (the peer replied with its heads), when its current heads
 * are the ones last deposited in the Home's mailbox (a deposit holds it for whoever is away), or when it has never been edited.
 * The Home has changes waiting when a document is not covered and there is somewhere ([SyncTargets]) for it to go; with nowhere,
 * the sync verdict is "only you" and the question does not arise.
 *
 * The answer is worked out only at moments when the service's sync states are whole, never in the middle of clearing them
 * (the service calls [update] at the end of a round and after each message it applies), and read from a cache by [of].
 * [sharedDocumentIds] are the ids the mailbox carries, [privateDocumentIds] the ones only the user's own devices get; [heads] reads a document's current heads, null for one this device
 * doesn't have.
 */
internal class ChangesWaiting(
    private val database: VestaDatabase,
    private val sharedDocumentIds: (homeId: String) -> List<String>,
    private val privateDocumentIds: (homeId: String) -> List<String>,
    private val targets: suspend (homeId: String) -> SyncTargets,
    private val heads: suspend (homeId: String, documentId: String) -> ByteArray?,
) {
    private val cache = ConcurrentHashMap<String, Boolean>()

    /**
     * Whether [homeId] has changes waiting, as of the last [update]. Before the first one (a fresh start has no sync states) it is
     * worked out once from the heads alone: waiting when any document differs from what the mailbox was last given.
     */
    suspend fun of(homeId: String): Boolean = cache[homeId] ?: evaluate(homeId, emptySet()).also { cache.putIfAbsent(homeId, it) }

    /** Works out [homeId]'s answer afresh; [acknowledged] are the ids of its documents that some peer's sync state reports in sync. */
    suspend fun update(homeId: String, acknowledged: Set<String>) {
        cache[homeId] = evaluate(homeId, acknowledged)
    }

    /** Forgets [homeId], for when it is deleted or left. */
    fun forget(homeId: String) {
        cache.remove(homeId)
    }

    private suspend fun evaluate(homeId: String, acknowledged: Set<String>): Boolean {
        val reachable = targets(homeId)
        val shared = if (reachable.shared) sharedDocumentIds(homeId) else emptyList()
        val own = if (reachable.own) privateDocumentIds(homeId) else emptyList()
        return shared.any { !isCovered(homeId, it, it in acknowledged, mailbox = true) } ||
            own.any { !isCovered(homeId, it, it in acknowledged, mailbox = false) }
    }

    private suspend fun isCovered(homeId: String, documentId: String, acknowledged: Boolean, mailbox: Boolean): Boolean {
        if (acknowledged) return true
        val current = heads(homeId, documentId)
        if (current == null || current.isEmpty()) return true // no such document, or never edited
        if (!mailbox) return false
        val deposited = database.mailboxQueries.selectHeads(homeId, documentId).awaitAsOneOrNull()
        return deposited != null && deposited.contentEquals(current)
    }
}
