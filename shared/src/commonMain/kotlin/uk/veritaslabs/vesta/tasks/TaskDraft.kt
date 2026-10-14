package uk.veritaslabs.vesta.tasks

import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat

/**
 * What the task form edits (ADR-031): a [Task] with the interval as the text in its field, so a half-typed or empty one doesn't
 * lose the cursor. Clearing the date clears the time and the repeat with it (neither means anything without a day); a private
 * task has no assignees.
 */
data class TaskDraft(
    val title: String = "",
    val description: String = "",
    val due: CalendarDate? = null,
    val timeMinute: Int? = null,
    val repeat: EventRepeat = EventRepeat.None,
    val everyText: String = "1",
    val assignees: Set<String> = emptySet(),
    val list: TaskList = TaskList.Home,
) {
    /** The interval typed, or null when it is not a whole number from 1 to [MaxTaskInterval]. */
    val every: Int? get() = everyText.trim().toIntOrNull()?.takeIf { it in 1..MaxTaskInterval }

    /** Whether the form can be saved: a title, and for a repeat a first date and a sensible interval. */
    fun isValid(): Boolean = title.isNotBlank() && (repeat == EventRepeat.None || (due != null && every != null))

    fun withDue(date: CalendarDate?): TaskDraft =
        if (date == null) copy(due = null, timeMinute = null, repeat = EventRepeat.None, everyText = "1") else copy(due = date)

    /** Chooses [repeat]; a repeat needs a first day, so it falls back to [fallbackDue] when there is none yet. */
    fun withRepeat(repeat: EventRepeat, fallbackDue: CalendarDate): TaskDraft =
        copy(repeat = repeat, due = due ?: fallbackDue.takeIf { repeat != EventRepeat.None }, everyText = if (repeat == EventRepeat.None) "1" else everyText)

    fun withList(list: TaskList): TaskDraft = copy(list = list, assignees = if (list == TaskList.Private) emptySet() else assignees)

    /** The task this draft makes: [id], and [createdMillis] and [createdBy] kept from the task being edited or set now for a new one. */
    fun toTask(id: String, createdMillis: Long, createdBy: String): Task = Task(
        id = id,
        list = list,
        title = title.trim(),
        description = description.trim(),
        due = due,
        timeMinute = timeMinute.takeIf { due != null },
        repeat = if (due == null) EventRepeat.None else repeat,
        every = if (repeat == EventRepeat.None) 1 else every ?: 1,
        assignees = if (list == TaskList.Private) emptySet() else assignees,
        createdMillis = createdMillis,
        createdBy = createdBy,
    )

    companion object {
        /** A new task due on [date] (or at no particular time, when null) on [list]. */
        fun on(date: CalendarDate?, list: TaskList = TaskList.Home) = TaskDraft(due = date, list = list)

        fun of(task: Task) = TaskDraft(
            title = task.title,
            description = task.description,
            due = task.due,
            timeMinute = task.timeMinute,
            repeat = task.repeat,
            everyText = task.every.toString(),
            assignees = task.assignees,
            list = task.list,
        )
    }
}
