package uk.veritaslabs.vesta.ui.flows

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import uk.veritaslabs.vesta.ui.components.LocalUndo
import org.jetbrains.compose.resources.stringResource
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.task_deleted
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.LocalCalendarSettings
import uk.veritaslabs.vesta.calendar.today
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.ui.screens.TaskEditScreen
import uk.veritaslabs.vesta.ui.screens.TasksScreen
import uk.veritaslabs.vesta.ui.screens.TasksViewModel

/** What the task form is open on: [task] to edit it, or null to add one due on [date] (null for no particular day). */
private data class TaskTarget(val task: Task?, val date: CalendarDate?)

/**
 * The tasks (ADR-031) and the task form over them. The plus buttons open the form for a new task and tapping a task's Edit opens it for that
 * task; saving or cancelling returns to the tasks on the view and week they were on, as the form is drawn over them rather than replacing
 * them. [homeName] names the Home list in the form.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun TasksFlow(
    viewModel: TasksViewModel,
    homeName: String,
    modifier: Modifier = Modifier,
    today: CalendarDate = remember { CalendarDate.today() },
    onBack: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val weekStart = LocalCalendarSettings.current.settings.weekStart
    var editing by remember { mutableStateOf<TaskTarget?>(null) }
    val undo = LocalUndo.current
    val deletedMessage = stringResource(Res.string.task_deleted)
    // Deleting says so and offers to put the task back as it was (the same id, so it lands where it was).
    val delete = { task: Task ->
        viewModel.delete(task)
        undo?.offer(deletedMessage) { viewModel.save(task) }
        Unit
    }
    // Until both lists have been read, a blank page rather than a flash of the empty state.
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        TasksScreen(
            tasks = state.tasks,
            done = state.done,
            members = state.members,
            me = state.me,
            today = today,
            weekStart = weekStart,
            onBack = onBack,
            onAdd = { date -> editing = TaskTarget(null, date) },
            onToggle = viewModel::toggle,
            onEdit = { task -> editing = TaskTarget(task, null) },
            onDelete = delete,
        )
        editing?.let { target ->
            TaskEditScreen(
                task = target.task,
                date = target.date,
                homeName = homeName,
                members = state.members,
                today = today,
                newId = { Uuid.random().toString() },
                onSave = { saved ->
                    viewModel.save(saved, movedFrom = target.task?.list)
                    editing = null
                },
                onDelete = { task ->
                    delete(task)
                    editing = null
                },
                onCancel = { editing = null },
            )
        }
    }
}
