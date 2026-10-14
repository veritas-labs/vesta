package uk.veritaslabs.vesta.automerge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.vesta.mailbox.changesSince
import uk.veritaslabs.vesta.mailbox.encodeHeads
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDone
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.tasks.TaskSnapshot
import uk.veritaslabs.vesta.tasks.TaskSource

/**
 * A [TaskSource] backed by a tasks Automerge [document] (ADR-031): a Home's ([TaskList.Home]) or the user's own
 * ([TaskList.Private]). Same threading contract as [CalendarDocumentSource]: route every read or write of [document]
 * through this class.
 */
class TaskDocumentSource(
    private val document: Document,
    override val list: TaskList,
    private val persist: suspend (ByteArray) -> Unit = {},
    private val onLocalChange: () -> Unit = {},
) : TaskSource {

    private val lock = Mutex()
    private val state = MutableStateFlow(document.readTasks(list))

    override val snapshot: Flow<TaskSnapshot> = state

    override suspend fun save(task: Task) = edit { putTask(document, task) }

    override suspend fun delete(id: String) = edit { deleteTask(document, id) }

    override suspend fun setDone(record: TaskDone) = edit { putTaskDone(document, record) }

    override suspend fun clearDone(taskId: String, occurrence: String) = edit { deleteTaskDone(document, taskId, occurrence) }

    private suspend fun edit(change: () -> Boolean) {
        val changed = lock.withLock {
            change().also { if (it) publishAndPersist() }
        }
        if (changed) onLocalChange()
    }

    /** The next Automerge sync message for the peer whose progress is [state], or null when it is up to date. */
    suspend fun generateSync(state: SyncState): ByteArray? = lock.withLock {
        document.generateSyncMessage(state).orElse(null)
    }

    /** Applies a sync message from a peer, then republishes and persists the tasks if they changed. */
    suspend fun receiveSync(state: SyncState, message: ByteArray) = applyIncoming { receiveSyncMessage(state, message) }

    /** The document's current heads, saved as [encodeHeads] writes them (for the mailbox, ADR-020). */
    suspend fun heads(): ByteArray = lock.withLock { encodeHeads(document.heads) }

    /** The changes made since the heads [saved] by [heads] (everything, for none), for another device's [applyChanges]. */
    suspend fun changesSince(saved: ByteArray): ByteArray = lock.withLock { document.changesSince(saved) }

    /** Applies changes from [changesSince], as [receiveSync] does a sync message. Applying one again changes nothing. */
    suspend fun applyChanges(changes: ByteArray) = applyIncoming { applyEncodedChanges(changes) }

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
        state.value = document.readTasks(list)
        persist(document.save())
    }
}
