package uk.veritaslabs.vesta.automerge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.automerge.Document
import uk.veritaslabs.vesta.docs.AutomergeDocumentCore
import uk.veritaslabs.vesta.docs.HomeDocument
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
class TaskDocumentSource private constructor(
    private val core: AutomergeDocumentCore,
    override val list: TaskList,
    override val snapshot: Flow<TaskSnapshot>,
) : TaskSource, HomeDocument by core {

    constructor(
        document: Document,
        list: TaskList,
        persist: suspend (ByteArray) -> Unit = {},
        onLocalChange: () -> Unit = {},
    ) : this(MutableStateFlow(document.readTasks(list)), document, list, persist, onLocalChange)

    private constructor(
        state: MutableStateFlow<TaskSnapshot>,
        document: Document,
        list: TaskList,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
    ) : this(
        AutomergeDocumentCore(document, { state.value = document.readTasks(list) }, persist, onLocalChange),
        list,
        state,
    )

    override suspend fun save(task: Task) = edit { putTask(this, task) }

    override suspend fun delete(id: String) = edit { deleteTask(this, id) }

    override suspend fun setDone(record: TaskDone) = edit { putTaskDone(this, record) }

    override suspend fun clearDone(taskId: String, occurrence: String) = edit { deleteTaskDone(this, taskId, occurrence) }

    /** Publishes and persists only when [change] reports it changed something, as the tasks' edits always did. */
    private suspend fun edit(change: Document.() -> Boolean) {
        core.editIfChanged(change)
    }

    /** Republishes and persists after the document changed outside the edits above. */
    suspend fun refresh() = core.refresh()
}
