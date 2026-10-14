package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.MinutesPerDay
import uk.veritaslabs.vesta.tasks.MaxTaskInterval
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDone
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.tasks.TaskSnapshot
import uk.veritaslabs.vesta.tasks.isoText
import uk.veritaslabs.vesta.tasks.parseIsoDate

private const val TitleKey = "title"
private const val DescriptionKey = "description"
private const val DueKey = "due"
private const val TimeKey = "time"
private const val RepeatKey = "repeat"
private const val EveryKey = "every"
private const val AssigneesKey = "assignees"
private const val CreatedKey = "created"
private const val CreatedByKey = "createdBy"
private const val AtKey = "at"
private const val ByKey = "by"
private const val AssigneeSeparator = '\n'
private const val Separator = '|'
private const val DonePrefix = "done|"
private const val DoneParts = 3

/*
 * One map at the document root per task, under the task's id (a UUID), like the calendar (ADR-029): there is no wrapping
 * "tasks" map for two devices to create at once. Each field is its own key. Whether an occurrence was done is a separate
 * flat root key, "done|<task id>|<yyyy-MM-dd or anytime>", holding who and when (ADR-031): two devices ticking the same
 * occurrence write the same key and merge to one, and ticking different occurrences adds two. Those keys are the history.
 * Times are decimal strings, like the calendar's. A task id has no '|'.
 */

internal fun doneKey(taskId: String, occurrence: String): String = "$DonePrefix$taskId$Separator$occurrence"

/** Every task and done record in the document, the tasks tagged as being on [list]. An entry this version can't make sense of is skipped. */
fun Read.readTasks(list: TaskList): TaskSnapshot {
    val entries = mapEntries(ObjectId.ROOT).getOrNull().orEmpty()
    val tasks = entries.filterNot { it.key.startsWith(DonePrefix) }.mapNotNull { entry ->
        val id = (entry.value as? AmValue.Map)?.id ?: return@mapNotNull null
        val title = str(id, TitleKey)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val repeat = EventRepeat.entries.firstOrNull { it.name == str(id, RepeatKey) } ?: EventRepeat.None
        Task(
            id = entry.key,
            list = list,
            title = title,
            description = str(id, DescriptionKey).orEmpty(),
            due = str(id, DueKey)?.let(::parseIsoDate),
            timeMinute = str(id, TimeKey)?.toIntOrNull()?.takeIf { it in 0 until MinutesPerDay },
            repeat = repeat,
            every = (str(id, EveryKey)?.toIntOrNull() ?: 1).coerceIn(1, MaxTaskInterval),
            assignees = str(id, AssigneesKey).orEmpty().split(AssigneeSeparator).filter { it.isNotEmpty() }.toSet(),
            createdMillis = str(id, CreatedKey)?.toLongOrNull() ?: 0L,
            createdBy = str(id, CreatedByKey).orEmpty(),
        )
    }
    val done = entries.filter { it.key.startsWith(DonePrefix) }.mapNotNull { entry ->
        val id = (entry.value as? AmValue.Map)?.id ?: return@mapNotNull null
        val parts = entry.key.removePrefix(DonePrefix).split(Separator, limit = DoneParts - 1)
        if (parts.size != 2) return@mapNotNull null
        TaskDone(taskId = parts[0], occurrence = parts[1], atMillis = str(id, AtKey)?.toLongOrNull() ?: 0L, by = str(id, ByKey).orEmpty())
    }
    return TaskSnapshot(tasks, done)
}

/** Adds [task], or brings the stored one with its id up to date, field by field. Returns whether anything changed. */
fun putTask(doc: Document, task: Task): Boolean {
    val tx = doc.startTransaction()
    val existing = (tx.get(ObjectId.ROOT, task.id).getOrNull() as? AmValue.Map)?.id
    var changed = existing == null
    val entry = existing ?: tx.set(ObjectId.ROOT, task.id, ObjectType.MAP)
    fun put(key: String, value: String?) {
        val current = tx.str(entry, key)
        when {
            value == null -> if (current != null) { tx.delete(entry, key); changed = true }
            current != value -> { tx.set(entry, key, value); changed = true }
        }
    }
    put(TitleKey, task.title)
    put(DescriptionKey, task.description)
    put(DueKey, task.due?.isoText())
    put(TimeKey, task.timeMinute?.toString())
    put(RepeatKey, task.repeat.name)
    put(EveryKey, task.every.toString())
    put(AssigneesKey, task.assignees.sorted().joinToString(AssigneeSeparator.toString()))
    put(CreatedKey, task.createdMillis.toString())
    put(CreatedByKey, task.createdBy)
    if (changed) tx.commit() else tx.rollback()
    return changed
}

/** Removes the task [id]; its done records stay. Returns false, changing nothing, if there was none. */
fun deleteTask(doc: Document, id: String): Boolean {
    val tx = doc.startTransaction()
    if ((tx.get(ObjectId.ROOT, id).getOrNull() as? AmValue.Map) == null) {
        tx.rollback()
        return false
    }
    tx.delete(ObjectId.ROOT, id)
    tx.commit()
    return true
}

/** Records [record], or brings the stored record of that occurrence up to date. Returns whether anything changed. */
fun putTaskDone(doc: Document, record: TaskDone): Boolean {
    val tx = doc.startTransaction()
    val key = doneKey(record.taskId, record.occurrence)
    val existing = (tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Map)?.id
    var changed = existing == null
    val entry = existing ?: tx.set(ObjectId.ROOT, key, ObjectType.MAP)
    fun put(field: String, value: String) {
        if (tx.str(entry, field) != value) { tx.set(entry, field, value); changed = true }
    }
    put(AtKey, record.atMillis.toString())
    put(ByKey, record.by)
    if (changed) tx.commit() else tx.rollback()
    return changed
}

/** Forgets that [occurrence] of [taskId] was done. Returns false, changing nothing, if it wasn't. */
fun deleteTaskDone(doc: Document, taskId: String, occurrence: String): Boolean = deleteTask(doc, doneKey(taskId, occurrence))

private fun Read.str(obj: ObjectId, key: String): String? = (get(obj, key).getOrNull() as? AmValue.Str)?.value
