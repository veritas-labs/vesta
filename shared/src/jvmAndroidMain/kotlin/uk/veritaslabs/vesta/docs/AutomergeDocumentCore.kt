package uk.veritaslabs.vesta.docs

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import org.automerge.SyncState

/**
 * The lock-edit-persist machinery every Home document source repeats: it owns the Automerge [document], the [Mutex]
 * that serialises every read and write of it, [persist] and [onLocalChange], and implements [HomeDocument].
 *
 * [publish] recomputes the source's state flow(s) from [document]; it runs under the lock, immediately before
 * [persist], whenever the document changed. Same threading contract as the sources: route every read or write of
 * [document] through this class.
 */
internal class AutomergeDocumentCore(
    private val document: Document,
    private val publish: () -> Unit,
    private val persist: suspend (ByteArray) -> Unit = {},
    private val onLocalChange: () -> Unit = {},
) : HomeDocument {

    private val lock = Mutex()

    /** Runs [change] on the document, republishes and persists it, then (outside the lock) tells [onLocalChange]. */
    suspend fun <R> edit(change: Document.() -> R): R {
        val result = lock.withLock {
            document.change().also { publishAndPersist() }
        }
        onLocalChange()
        return result
    }

    /** As [edit], but republishes, persists and calls [onLocalChange] only when [change] returns true. */
    suspend fun editIfChanged(change: Document.() -> Boolean): Boolean {
        val changed = lock.withLock {
            document.change().also { if (it) publishAndPersist() }
        }
        if (changed) onLocalChange()
        return changed
    }

    override suspend fun generateSync(state: SyncState): ByteArray? = lock.withLock {
        document.generateSyncMessage(state).orElse(null)
    }

    override suspend fun receiveSync(state: SyncState, message: ByteArray) = applyIncoming { receiveSyncMessage(state, message) }

    override suspend fun isInSync(state: SyncState): Boolean = lock.withLock { state.isInSync(document) }

    override suspend fun heads(): ByteArray = lock.withLock { encodeHeads(document.heads) }

    override suspend fun changesSince(saved: ByteArray): ByteArray = lock.withLock { document.changesSince(saved) }

    /** Applies changes from [changesSince], as [receiveSync] does a sync message. Applying one again changes nothing. */
    override suspend fun applyChanges(changes: ByteArray) = applyIncoming { applyEncodedChanges(changes) }

    private suspend fun applyIncoming(apply: Document.() -> Unit) {
        lock.withLock {
            val before = document.heads
            document.apply()
            if (!document.heads.contentEquals(before)) publishAndPersist()
        }
    }

    /** Republishes and persists after [document] changed outside the edits above. */
    suspend fun refresh() = lock.withLock { publishAndPersist() }

    private suspend fun publishAndPersist() {
        publish()
        persist(document.save())
    }
}
