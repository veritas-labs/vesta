package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.spaces.MemberRole
import uk.veritaslabs.kits.spaces.MemberListItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.formatMinute
import uk.veritaslabs.vesta.calendar.today
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDraft
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.calendar_months
import vesta.shared.generated.resources.event_edit_repeat_daily
import vesta.shared.generated.resources.event_edit_repeat_monthly
import vesta.shared.generated.resources.event_edit_repeat_none
import vesta.shared.generated.resources.event_edit_repeat_weekly
import vesta.shared.generated.resources.event_edit_repeat_yearly
import vesta.shared.generated.resources.ic_align_left
import vesta.shared.generated.resources.ic_calendar
import vesta.shared.generated.resources.ic_clock
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.ic_home
import vesta.shared.generated.resources.ic_lock
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_repeat
import vesta.shared.generated.resources.ic_trash
import vesta.shared.generated.resources.ic_users
import vesta.shared.generated.resources.task_edit_add_title
import vesta.shared.generated.resources.task_edit_assign
import vesta.shared.generated.resources.task_edit_assign_done
import vesta.shared.generated.resources.task_edit_assign_hint
import vesta.shared.generated.resources.task_edit_assign_title
import vesta.shared.generated.resources.common_cancel
import vesta.shared.generated.resources.task_edit_clear_date
import vesta.shared.generated.resources.task_edit_clear_time
import vesta.shared.generated.resources.task_edit_delete
import vesta.shared.generated.resources.task_edit_description_hint
import vesta.shared.generated.resources.task_edit_edit_title
import vesta.shared.generated.resources.task_edit_every
import vesta.shared.generated.resources.task_edit_every_days
import vesta.shared.generated.resources.task_edit_every_invalid
import vesta.shared.generated.resources.task_edit_every_months
import vesta.shared.generated.resources.task_edit_every_weeks
import vesta.shared.generated.resources.task_edit_every_years
import vesta.shared.generated.resources.task_edit_list_home
import vesta.shared.generated.resources.task_edit_list_private
import vesta.shared.generated.resources.task_edit_no_date
import vesta.shared.generated.resources.task_edit_no_time
import vesta.shared.generated.resources.task_edit_private_note
import vesta.shared.generated.resources.task_edit_save
import vesta.shared.generated.resources.task_edit_title_hint

private val TaskEditTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object TaskEditTestTags {
    const val CancelButton = "task_edit_cancel_button"
    const val SaveButton = "task_edit_save_button"
    const val DeleteButton = "task_edit_delete_button"
    const val TitleField = "task_edit_title"
    const val DueDate = "task_edit_due_date"
    const val ClearDate = "task_edit_clear_date"
    const val DueTime = "task_edit_due_time"
    const val ClearTime = "task_edit_clear_time"
    const val Repeat = "task_edit_repeat"
    const val EveryField = "task_edit_every"
    const val EveryInvalid = "task_edit_every_invalid"
    const val ListField = "task_edit_list"
    const val PrivateNote = "task_edit_private_note"
    const val AssigneesField = "task_edit_assignees"
    const val AssigneesDialog = "task_edit_assignees_dialog"
    const val AssigneesDone = "task_edit_assignees_done"
    const val AssigneesHint = "task_edit_assignees_hint"
    const val DescriptionField = "task_edit_description"
    const val PickerOk = "event_edit_picker_ok"

    fun repeatOption(repeat: EventRepeat) = "task_edit_repeat_${repeat.name}"

    fun listOption(list: TaskList) = "task_edit_list_${list.name}"

    fun assignee(ref: String) = "task_edit_assignee_$ref"

    fun assigneeChip(ref: String) = "task_edit_assignee_chip_$ref"
}

private enum class TaskPicker { Date, Time }

/**
 * The form for adding a task due on [date] (null for one to do whenever; [task] null) or editing [task] (ADR-031): title; the day it is
 * due, and the time, both optional; how it repeats and, for a repeat, every how many days, weeks, months or years; which list it is on (the
 * Home's, or the user's private one); who it is for, chosen from the Home's [members] (Home list only; nobody means anyone); notes. Choosing
 * a repeat on a task with no date starts it on [today]. Save is enabled once there is a title, and for a repeat a date and a whole number of
 * 1 to 999. Editing also shows Delete. [homeName] names the Home list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    task: Task?,
    date: CalendarDate?,
    modifier: Modifier = Modifier,
    homeName: String = "",
    members: List<MemberListItem> = emptyList(),
    today: CalendarDate = remember { CalendarDate.today() },
    onSave: (Task) -> Unit = {},
    onDelete: (Task) -> Unit = {},
    onCancel: () -> Unit = {},
    newId: () -> String = { "" },
) {
    var draft by remember(task) { mutableStateOf(task?.let { TaskDraft.of(it) } ?: TaskDraft.on(date)) }
    var picking by remember { mutableStateOf<TaskPicker?>(null) }
    var choosingAssignees by remember { mutableStateOf(false) }
    val months = stringArrayResource(Res.array.calendar_months)
    val everyInvalid = draft.repeat != EventRepeat.None && draft.every == null

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= TaskEditTabletBreakpoint) 24.dp else 16.dp
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(if (task == null) Res.string.task_edit_add_title else Res.string.task_edit_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag(TaskEditTestTags.CancelButton)) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.common_cancel))
                    }
                },
                actions = {
                    task?.let {
                        IconButton(onClick = { onDelete(it) }, modifier = Modifier.testTag(TaskEditTestTags.DeleteButton)) {
                            Icon(painterResource(Res.drawable.ic_trash), contentDescription = stringResource(Res.string.task_edit_delete))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = contentPadding)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    placeholder = { Text(stringResource(Res.string.task_edit_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(TaskEditTestTags.TitleField),
                )

                // When it is due: a date, then a time once there is a date.
                FormRow(Res.drawable.ic_calendar) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { picking = TaskPicker.Date }, modifier = Modifier.weight(1f).testTag(TaskEditTestTags.DueDate)) {
                            Text(draft.due?.let { dateText(it, months) } ?: stringResource(Res.string.task_edit_no_date), maxLines = 1)
                        }
                        if (draft.due != null) {
                            IconButton(onClick = { draft = draft.withDue(null) }, modifier = Modifier.testTag(TaskEditTestTags.ClearDate)) {
                                Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.task_edit_clear_date), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (draft.due != null) {
                    FormRow(Res.drawable.ic_clock) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { picking = TaskPicker.Time }, modifier = Modifier.weight(1f).testTag(TaskEditTestTags.DueTime)) {
                                Text(draft.timeMinute?.let { formatMinute(it) } ?: stringResource(Res.string.task_edit_no_time), maxLines = 1)
                            }
                            if (draft.timeMinute != null) {
                                IconButton(onClick = { draft = draft.copy(timeMinute = null) }, modifier = Modifier.testTag(TaskEditTestTags.ClearTime)) {
                                    Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.task_edit_clear_time), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // How often it comes round.
                FormRow(Res.drawable.ic_repeat) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceMenu(
                            current = repeatLabel(draft.repeat),
                            options = EventRepeat.entries,
                            label = { repeatLabel(it) },
                            optionTag = TaskEditTestTags::repeatOption,
                            onChoose = { draft = draft.withRepeat(it, fallbackDue = today) },
                            tag = TaskEditTestTags.Repeat,
                        )
                        if (draft.repeat != EventRepeat.None) {
                            Text(stringResource(Res.string.task_edit_every), style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = draft.everyText,
                                onValueChange = { draft = draft.copy(everyText = it.filter(Char::isDigit).take(3)) },
                                singleLine = true,
                                isError = everyInvalid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(80.dp).testTag(TaskEditTestTags.EveryField),
                            )
                            Text(everyUnit(draft.repeat), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (everyInvalid) {
                    FormRow(null) {
                        Text(
                            stringResource(Res.string.task_edit_every_invalid),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(TaskEditTestTags.EveryInvalid),
                        )
                    }
                }

                // Which list, and who it is for.
                FormRow(if (draft.list == TaskList.Private) Res.drawable.ic_lock else Res.drawable.ic_home) {
                    ChoiceMenu(
                        current = listLabel(draft.list, homeName),
                        options = TaskList.entries,
                        label = { listLabel(it, homeName) },
                        optionTag = TaskEditTestTags::listOption,
                        onChoose = { draft = draft.withList(it) },
                        tag = TaskEditTestTags.ListField,
                        modifier = Modifier.fillMaxWidth(),
                        fillWidth = true,
                    )
                }
                if (draft.list == TaskList.Private) {
                    FormRow(null) {
                        Text(
                            stringResource(Res.string.task_edit_private_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(TaskEditTestTags.PrivateNote),
                        )
                    }
                } else {
                    FormRow(Res.drawable.ic_users) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { choosingAssignees = true }, modifier = Modifier.testTag(TaskEditTestTags.AssigneesField)) {
                                Icon(painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.task_edit_assign))
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            ) {
                                val chosen = members.filter { memberRef(it.memberId) in draft.assignees }
                                if (chosen.isEmpty()) {
                                    Text(
                                        stringResource(Res.string.task_edit_assign_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag(TaskEditTestTags.AssigneesHint),
                                    )
                                }
                                chosen.forEach { member ->
                                    InputChip(
                                        selected = true,
                                        onClick = { draft = draft.copy(assignees = draft.assignees - memberRef(member.memberId)) },
                                        label = { Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        trailingIcon = { Icon(painterResource(Res.drawable.ic_close), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag(TaskEditTestTags.assigneeChip(memberRef(member.memberId))),
                                    )
                                }
                            }
                        }
                    }
                }

                FormRow(Res.drawable.ic_align_left, topAligned = true) {
                    OutlinedTextField(
                        value = draft.description,
                        onValueChange = { draft = draft.copy(description = it) },
                        placeholder = { Text(stringResource(Res.string.task_edit_description_hint)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag(TaskEditTestTags.DescriptionField),
                    )
                }
                PrimaryButton(
                    label = stringResource(Res.string.task_edit_save),
                    enabled = draft.isValid(),
                    onClick = { onSave(draft.toTask(task?.id ?: newId(), task?.createdMillis ?: 0L, task?.createdBy.orEmpty())) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(TaskEditTestTags.SaveButton),
                )
            }
        }
    }

    picking?.let { field ->
        when (field) {
            TaskPicker.Date -> DatePickerPopup(
                initial = draft.due ?: today,
                onPick = {
                    draft = draft.withDue(it)
                    picking = null
                },
                onDismiss = { picking = null },
            )
            TaskPicker.Time -> TimePickerPopup(
                initial = draft.timeMinute ?: DefaultDueMinute,
                onPick = {
                    draft = draft.copy(timeMinute = it)
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
    }
    if (choosingAssignees) {
        AssigneesDialog(
            members = members,
            chosen = draft.assignees,
            onChange = { draft = draft.copy(assignees = it) },
            onDismiss = { choosingAssignees = false },
        )
    }
}

private const val DefaultDueMinute = 9 * 60

@Composable
private fun repeatLabel(repeat: EventRepeat) = stringResource(
    when (repeat) {
        EventRepeat.None -> Res.string.event_edit_repeat_none
        EventRepeat.Daily -> Res.string.event_edit_repeat_daily
        EventRepeat.Weekly -> Res.string.event_edit_repeat_weekly
        EventRepeat.Monthly -> Res.string.event_edit_repeat_monthly
        EventRepeat.Yearly -> Res.string.event_edit_repeat_yearly
    },
)

/** The unit after "Every N": days, weeks, months or years. */
@Composable
private fun everyUnit(repeat: EventRepeat) = stringResource(
    when (repeat) {
        EventRepeat.None, EventRepeat.Daily -> Res.string.task_edit_every_days
        EventRepeat.Weekly -> Res.string.task_edit_every_weeks
        EventRepeat.Monthly -> Res.string.task_edit_every_months
        EventRepeat.Yearly -> Res.string.task_edit_every_years
    },
)

@Composable
private fun listLabel(list: TaskList, homeName: String) =
    if (list == TaskList.Home) stringResource(Res.string.task_edit_list_home, homeName).trim() else stringResource(Res.string.task_edit_list_private)

@Composable
private fun AssigneesDialog(members: List<MemberListItem>, chosen: Set<String>, onChange: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(TaskEditTestTags.AssigneesDialog),
        title = { Text(stringResource(Res.string.task_edit_assign_title)) },
        text = {
            Column {
                members.forEach { member ->
                    val ref = memberRef(member.memberId)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onChange(if (ref in chosen) chosen - ref else chosen + ref) }
                            .testTag(TaskEditTestTags.assignee(ref)),
                    ) {
                        Checkbox(checked = ref in chosen, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                        Text(member.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(TaskEditTestTags.AssigneesDone)) {
                Text(stringResource(Res.string.task_edit_assign_done))
            }
        },
    )
}

private val PreviewDate = CalendarDate(2026, 10, 14)
private val PreviewMembers = listOf(
    MemberListItem("a", "Alex", MemberRole.Admin, isYou = true),
    MemberListItem("b", "Sam", MemberRole.Member),
)
private val PreviewTask = Task(
    "t1", TaskList.Home, "Put the bins out", "Green one this week", PreviewDate, 7 * 60 + 30, EventRepeat.Weekly, 2, setOf("m:b"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun TaskEditScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { TaskEditScreen(task = PreviewTask, date = null, homeName = "Our house", members = PreviewMembers, today = PreviewDate) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun TaskEditScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { TaskEditScreen(task = PreviewTask, date = null, homeName = "Our house", members = PreviewMembers, today = PreviewDate) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun TaskEditScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { TaskEditScreen(task = PreviewTask, date = null, homeName = "Our house", members = PreviewMembers, today = PreviewDate) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun TaskEditScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { TaskEditScreen(task = PreviewTask, date = null, homeName = "Our house", members = PreviewMembers, today = PreviewDate) }
}
