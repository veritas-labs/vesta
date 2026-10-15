package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.home.MemberListItem
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.tasks.DoneIndex
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDone
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.tasks.TaskOccurrence
import uk.veritaslabs.vesta.tasks.TaskSource
import uk.veritaslabs.vesta.tasks.overdue
import uk.veritaslabs.vesta.tasks.tasksOn

private const val StopTimeoutMillis = 5_000L

/** [TasksFlow]'s state; [isLoading] is true until both lists have emitted once. */
data class TasksUiState(
    /** The tasks of the Home's list and of the user's private one, each tagged with the list it is on. */
    val tasks: List<Task> = emptyList(),
    /** What was done, from both lists. */
    val done: List<TaskDone> = emptyList(),
    /** The Home's members, for choosing assignees and naming who did a task. */
    val members: List<MemberListItem> = emptyList(),
    val isLoading: Boolean = true,
) {
    /** The current user's ref ("m:<member id>"), or null while the members haven't loaded. */
    val me: String? get() = members.firstOrNull { it.isYou }?.let { memberRef(it.memberId) }
}

/** How many tasks need attention, for the dashboard tile: those [overdue] and those due [today] and still open. */
data class TasksSummary(val overdue: Int = 0, val today: Int = 0)

/** The tile's counts for [state] on [today]. */
fun tasksSummary(state: TasksUiState, today: CalendarDate): TasksSummary {
    val index = DoneIndex(state.done)
    return TasksSummary(
        overdue = overdue(state.tasks, index, today).size,
        today = tasksOn(state.tasks, index, today).count { !it.isDone },
    )
}

/**
 * Backs the tasks screens (ADR-031): the Home's [home] list and the user's [private] one shown together, with each edit sent to the list
 * the task is on.
 */
class TasksViewModel(
    private val home: TaskSource,
    private val private: TaskSource,
    members: Flow<List<MemberListItem>> = flowOf(emptyList()),
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    val uiState: StateFlow<TasksUiState> = combine(home.snapshot, private.snapshot, members) { shared, own, homeMembers ->
        TasksUiState(tasks = shared.tasks + own.tasks, done = shared.done + own.done, members = homeMembers, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), TasksUiState())

    private fun source(list: TaskList) = if (list == TaskList.Home) home else private

    /**
     * Saves [task] on its list. When it was [movedFrom] another list it is taken off that one first. A private task has no assignees: they
     * are the Home's members, and the Home never sees it. A new task (no [Task.createdBy] yet) is stamped with who made it and when.
     */
    fun save(task: Task, movedFrom: TaskList? = null) {
        val me = uiState.value.me.orEmpty()
        val stamped = if (task.createdBy.isEmpty() && task.createdMillis == 0L) task.copy(createdMillis = nowMillis(), createdBy = me) else task
        val toSave = if (stamped.list == TaskList.Private) stamped.copy(assignees = emptySet()) else stamped
        viewModelScope.launch {
            movedFrom?.takeIf { it != task.list }?.let { source(it).delete(task.id) }
            source(task.list).save(toSave)
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch { source(task.list).delete(task.id) }
    }

    /** Ticks [occurrence] as done by the current user, or un-ticks it if it was done. */
    fun toggle(occurrence: TaskOccurrence) {
        val task = occurrence.task
        val me = uiState.value.me.orEmpty()
        viewModelScope.launch {
            if (occurrence.isDone) {
                source(task.list).clearDone(task.id, occurrence.occurrence)
            } else {
                source(task.list).setDone(TaskDone(task.id, occurrence.occurrence, nowMillis(), me))
            }
        }
    }
}
