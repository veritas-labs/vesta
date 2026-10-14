package uk.veritaslabs.vesta.tasks

import kotlinx.coroutines.flow.Flow

/** Everything one tasks document holds: its tasks and the records of what was done. Read together so a screen never shows one without the other. */
data class TaskSnapshot(val tasks: List<Task> = emptyList(), val done: List<TaskDone> = emptyList())

/** Where the tasks screens get one list's tasks from, and save them to; implemented over a tasks Automerge document (ADR-031). */
interface TaskSource {
    /** Which list this is. */
    val list: TaskList

    val snapshot: Flow<TaskSnapshot>

    /** Adds [task], or replaces the one with its id. */
    suspend fun save(task: Task)

    /** Removes the task with [id]. Its done records stay (and are ignored). No-op if there is none. */
    suspend fun delete(id: String)

    /** Records [record], or replaces the record of that task's occurrence. */
    suspend fun setDone(record: TaskDone)

    /** Forgets that [occurrence] of [taskId] was done. No-op if it wasn't. */
    suspend fun clearDone(taskId: String, occurrence: String)
}
