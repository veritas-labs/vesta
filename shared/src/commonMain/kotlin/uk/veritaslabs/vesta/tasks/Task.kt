package uk.veritaslabs.vesta.tasks

import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.daysInMonth
import uk.veritaslabs.vesta.calendar.latestOn
import uk.veritaslabs.vesta.calendar.recursOn

/** Which list a task is on (ADR-031): the Home's, shared with its members, or the user's own, which only their devices see. */
enum class TaskList { Home, Private }

/** The most a task can come round "every N": a guard for a damaged value, not a limit the form offers. */
const val MaxTaskInterval = 999

/**
 * One task or chore. [due] is the first (or only) day it is due, null for a task to do whenever. [timeMinute] is a due time
 * on the device's wall clock (minutes after midnight), shown only; it never notifies (ADR-032 does that). A task that
 * [repeat]s comes round every [every] days, weeks, months or years from [due] and needs one. [assignees] are Home member refs
 * (`MealPerson.ref`s); empty means anyone, and a private task has none. [list] says which document it lives in and isn't stored
 * in it. Whether it has been done is not here but in the [TaskDone]s: a repeating task is done one occurrence at a time.
 */
data class Task(
    val id: String,
    val list: TaskList,
    val title: String,
    val description: String = "",
    val due: CalendarDate? = null,
    val timeMinute: Int? = null,
    val repeat: EventRepeat = EventRepeat.None,
    val every: Int = 1,
    val assignees: Set<String> = emptySet(),
    val createdMillis: Long = 0L,
    val createdBy: String = "",
) {
    /** Whether the task is on [date]: its due day, or a day it comes round to. A task with no due date is on no day. */
    fun isDueOn(date: CalendarDate): Boolean = due?.let { repeat.recursOn(it, date, every) } == true

    /** The last day on or before [date] the task is due, or null if it isn't due by then (or at all). */
    fun latestDueOnOrBefore(date: CalendarDate): CalendarDate? = due?.let { repeat.latestOn(it, date, every) }
}

/**
 * The record that [taskId] was done on the occurrence [occurrence] (a date, or [AnytimeOccurrence] for a task with no due date):
 * the member [by] ("m:<member id>") ticked it at [atMillis]. These are also the task history (ADR-031).
 */
data class TaskDone(val taskId: String, val occurrence: String, val atMillis: Long, val by: String)

/** The occurrence of a task with no due date, which comes round once. */
const val AnytimeOccurrence = "anytime"

/** `2026-10-14`: the form an occurrence date takes in a [TaskDone] and its document key. */
fun CalendarDate.isoText(): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

/** The date [isoText] wrote, or null for anything else. */
fun parseIsoDate(text: String): CalendarDate? {
    val parts = text.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val day = parts[2].toIntOrNull()?.takeIf { it in 1..daysInMonth(year, month) } ?: return null
    return CalendarDate(year, month, day)
}



/** A task on one particular [date] (null for an anytime task), and the [done] record of that occurrence if it has one. */
data class TaskOccurrence(val task: Task, val date: CalendarDate?, val done: TaskDone?) {
    val isDone: Boolean get() = done != null
    val occurrence: String get() = date?.isoText() ?: AnytimeOccurrence
}

private fun Task.occurrenceOn(date: CalendarDate?): String = date?.isoText() ?: AnytimeOccurrence

/** The [TaskDone]s keyed by task and occurrence, for looking one up. */
class DoneIndex(done: Collection<TaskDone>) {
    private val byKey = done.associateBy { it.taskId to it.occurrence }

    operator fun get(taskId: String, occurrence: String): TaskDone? = byKey[taskId to occurrence]
}

private fun Task.occurrence(date: CalendarDate?, done: DoneIndex) = TaskOccurrence(this, date, done[id, occurrenceOn(date)])

/** What to show first: open before done, then by time of day (no time last), then by title. */
private val OccurrenceOrder: Comparator<TaskOccurrence> =
    compareBy<TaskOccurrence>({ it.isDone }, { it.task.timeMinute ?: Int.MAX_VALUE }, { it.task.title.lowercase() }, { it.task.id })

/** The tasks (and repeats of them) due on [date], open ones first. */
fun tasksOn(tasks: List<Task>, done: DoneIndex, date: CalendarDate): List<TaskOccurrence> =
    tasks.filter { it.isDueOn(date) }.map { it.occurrence(date, done) }.sortedWith(OccurrenceOrder)

/** The open tasks due on or after [from] and before [from] + [days]: for the List view's Upcoming. Open ones only, by day then [OccurrenceOrder]. */
fun upcoming(tasks: List<Task>, done: DoneIndex, from: CalendarDate, days: Int): List<TaskOccurrence> =
    (0 until days).flatMap { offset -> tasksOn(tasks, done, from.plusDays(offset)).filterNot { it.isDone } }

/**
 * The open occurrences before [today] (ADR-031): a one-off task stays overdue until done; a repeating one only for its latest
 * missed occurrence, so a chore ignored for a year is one overdue item, not hundreds. Oldest first.
 */
fun overdue(tasks: List<Task>, done: DoneIndex, today: CalendarDate): List<TaskOccurrence> =
    tasks.mapNotNull { task ->
        val date = task.latestDueOnOrBefore(today.plusDays(-1)) ?: return@mapNotNull null
        task.occurrence(date, done).takeUnless { it.isDone }
    }.sortedWith(compareBy({ it.date }, { it.task.timeMinute ?: Int.MAX_VALUE }, { it.task.title.lowercase() }, { it.task.id }))

/** The tasks with no due date: open ones, by title. A done one is history ([recentlyDone]). */
fun anytime(tasks: List<Task>, done: DoneIndex): List<TaskOccurrence> =
    tasks.filter { it.due == null }.map { it.occurrence(null, done) }.filterNot { it.isDone }
        .sortedWith(compareBy({ it.task.title.lowercase() }, { it.task.id }))

/** The tasks done in the [days] days up to [nowMillis], newest first, from the [TaskDone]s that still have a task. */
fun recentlyDone(tasks: List<Task>, done: Collection<TaskDone>, nowMillis: Long, days: Int): List<Pair<Task, TaskDone>> {
    val byId = tasks.associateBy { it.id }
    val since = nowMillis - days * 86_400_000L
    return done.filter { it.atMillis >= since }.mapNotNull { record -> byId[record.taskId]?.let { it to record } }
        .sortedWith(compareBy({ -it.second.atMillis }, { it.first.title.lowercase() }))
}

/** Whether [task] is for [ref] ("m:<member id>"): assigned to them, or to anyone. */
fun Task.isFor(ref: String): Boolean = assignees.isEmpty() || ref in assignees
