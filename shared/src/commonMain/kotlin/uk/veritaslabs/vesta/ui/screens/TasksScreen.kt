package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.spaces.MemberRole
import uk.veritaslabs.kits.spaces.MemberListItem
import uk.veritaslabs.vesta.ui.components.ContentMaxWidth
import uk.veritaslabs.vesta.ui.components.FabClearance
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.calendar.formatMinute
import uk.veritaslabs.vesta.calendar.localMoment
import uk.veritaslabs.vesta.calendar.today
import uk.veritaslabs.vesta.tasks.DoneIndex
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDone
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.tasks.TaskOccurrence
import uk.veritaslabs.vesta.tasks.anytime
import uk.veritaslabs.vesta.tasks.isFor
import uk.veritaslabs.vesta.tasks.overdue
import uk.veritaslabs.vesta.tasks.parseIsoDate
import uk.veritaslabs.vesta.tasks.recentlyDone
import uk.veritaslabs.vesta.tasks.tasksOn
import uk.veritaslabs.vesta.tasks.upcoming
import uk.veritaslabs.vesta.ui.components.ActionCard
import uk.veritaslabs.vesta.ui.components.ActionCardOrientation
import uk.veritaslabs.vesta.ui.components.EmptyState
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.calendar_months
import vesta.shared.generated.resources.ic_adjustments
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_check
import vesta.shared.generated.resources.ic_chevron_right
import vesta.shared.generated.resources.ic_lock
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_repeat
import vesta.shared.generated.resources.ic_trash
import vesta.shared.generated.resources.meal_planner_day_month
import vesta.shared.generated.resources.meal_planner_next_week
import vesta.shared.generated.resources.meal_planner_previous_week
import vesta.shared.generated.resources.meal_planner_this_week
import vesta.shared.generated.resources.meal_planner_today
import vesta.shared.generated.resources.meal_planner_weekdays
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.tasks_add
import vesta.shared.generated.resources.tasks_add_on_day
import vesta.shared.generated.resources.tasks_anyone
import vesta.shared.generated.resources.tasks_done_by
import vesta.shared.generated.resources.tasks_done_by_someone
import vesta.shared.generated.resources.tasks_done_by_when
import vesta.shared.generated.resources.tasks_empty_body
import vesta.shared.generated.resources.tasks_empty_mine_body
import vesta.shared.generated.resources.tasks_empty_mine_title
import vesta.shared.generated.resources.tasks_empty_title
import vesta.shared.generated.resources.tasks_filter_everyone
import vesta.shared.generated.resources.tasks_filter_mine
import vesta.shared.generated.resources.tasks_private
import vesta.shared.generated.resources.tasks_repeats
import vesta.shared.generated.resources.tasks_section_anytime
import vesta.shared.generated.resources.tasks_section_done
import vesta.shared.generated.resources.tasks_section_overdue
import vesta.shared.generated.resources.tasks_section_today
import vesta.shared.generated.resources.tasks_section_upcoming
import vesta.shared.generated.resources.tasks_sheet_delete
import vesta.shared.generated.resources.tasks_sheet_delete_body
import vesta.shared.generated.resources.tasks_sheet_edit
import vesta.shared.generated.resources.tasks_sheet_edit_body
import vesta.shared.generated.resources.tasks_sheet_repeat_note
import vesta.shared.generated.resources.tasks_summary_none
import vesta.shared.generated.resources.tasks_summary_progress
import vesta.shared.generated.resources.tasks_tick
import vesta.shared.generated.resources.tasks_title
import vesta.shared.generated.resources.tasks_untick
import vesta.shared.generated.resources.tasks_view_list
import vesta.shared.generated.resources.tasks_view_week
import vesta.shared.generated.resources.tasks_week_day_heading

private val ContentPadding = 16.dp
private val TickSize = 28.dp

private const val DaysPerWeek = 7
private const val UpcomingDays = 7
private const val RecentDays = 7

/** The two views of the same tasks (ADR-031). */
enum class TasksView { Week, List }

/** Stable hooks for UI tests to target, independent of displayed copy or task titles. */
internal object TasksTestTags {
    const val BackButton = "tasks_back_button"
    const val AddButton = "tasks_add_button"
    const val ViewWeek = "tasks_view_week"
    const val ViewList = "tasks_view_list"
    const val PreviousWeek = "tasks_previous_week"
    const val NextWeek = "tasks_next_week"
    const val ThisWeek = "tasks_this_week"
    const val WeekRange = "tasks_week_range"
    const val FilterMine = "tasks_filter_mine"
    const val FilterEveryone = "tasks_filter_everyone"
    const val Empty = "tasks_empty"
    const val Sheet = "tasks_sheet"
    const val SheetEdit = "tasks_sheet_edit"
    const val SheetDelete = "tasks_sheet_delete"
    const val RepeatNote = "tasks_sheet_repeat_note"

    private fun dateTag(date: CalendarDate) = "${date.year}-${date.month}-${date.day}"

    fun day(date: CalendarDate) = "tasks_day_${dateTag(date)}"

    fun daySummary(date: CalendarDate) = "tasks_day_summary_${dateTag(date)}"

    fun dayAdd(date: CalendarDate) = "tasks_day_add_${dateTag(date)}"

    /** The section of the List view named [name] (`overdue`, `today`, `upcoming`, `anytime` or `done`). */
    fun section(name: String) = "tasks_section_$name"

    /** The row of one occurrence of a task, in whichever view shows it. [occurrence] is the date text or `anytime`. */
    fun row(taskId: String, occurrence: String) = "tasks_row_${taskId}_$occurrence"

    fun tick(taskId: String, occurrence: String) = "tasks_tick_${taskId}_$occurrence"

    /** The line under a task's title: when, who for, who did it. */
    fun subtitle(taskId: String, occurrence: String) = "tasks_subtitle_${taskId}_$occurrence"
}

/**
 * The tasks of a Home and the user's private ones (ADR-031), in two views the user switches between. **Week** is the Meal Planner's layout:
 * a card per day of the week containing [today] shifted by a number of weeks, starting on [weekStart], each listing the tasks due that day
 * (repeating ones on each day they come round) with a tick circle. **List** has Overdue, Today, Upcoming, Anytime and Done recently, and a
 * Mine / Everyone filter on [me]. [done] says which occurrences are ticked; [members] name who a task is for and who did it.
 *
 * The tick calls [onToggle]; tapping a task opens a sheet with [onEdit] and [onDelete]. A day's plus in the Week view calls [onAdd] with that day;
 * the floating plus, shown only in the List view, calls it with null: a task for whenever.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    tasks: List<Task>,
    modifier: Modifier = Modifier,
    done: List<TaskDone> = emptyList(),
    members: List<MemberListItem> = emptyList(),
    me: String? = null,
    today: CalendarDate = remember { CalendarDate.today() },
    weekStart: WeekStart = WeekStart.Monday,
    nowMillis: Long = remember { Clock.System.now().toEpochMilliseconds() },
    initialView: TasksView = TasksView.Week,
    onBack: () -> Unit = {},
    onAdd: (CalendarDate?) -> Unit = {},
    onToggle: (TaskOccurrence) -> Unit = {},
    onEdit: (Task) -> Unit = {},
    onDelete: (Task) -> Unit = {},
) {
    var view by rememberSaveable { mutableIntStateOf(initialView.ordinal) }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var mineOnly by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val index = remember(done) { DoneIndex(done) }
    val tasksById = remember(tasks) { tasks.associateBy { it.id } }
    val people = remember(members) { members.associate { memberRef(it.memberId) to it.name } }
    val currentView = TasksView.entries[view]

    Scaffold(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TasksTestTags.BackButton)) {
                        Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            // The Week view has a plus on every day, so only the List view needs this one.
            if (currentView == TasksView.List) {
                FloatingActionButton(onClick = { onAdd(null) }, modifier = Modifier.testTag(TasksTestTags.AddButton)) {
                    Icon(painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.tasks_add))
                }
            }
        },
    ) { innerPadding ->
        // Only what is under the app bar is held to a readable width, so the bar spans the window like every other screen's.
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.fillMaxHeight().widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 4.dp)) {
                    SegmentedButton(
                        selected = currentView == TasksView.Week,
                        onClick = { view = TasksView.Week.ordinal },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag(TasksTestTags.ViewWeek),
                    ) { Text(stringResource(Res.string.tasks_view_week)) }
                    SegmentedButton(
                        selected = currentView == TasksView.List,
                        onClick = { view = TasksView.List.ordinal },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag(TasksTestTags.ViewList),
                    ) { Text(stringResource(Res.string.tasks_view_list)) }
                }
                when (currentView) {
                    TasksView.Week -> WeekView(
                        tasks = tasks,
                        index = index,
                        people = people,
                        today = today,
                        weekStart = weekStart,
                        weekOffset = weekOffset,
                        onPreviousWeek = { weekOffset-- },
                        onNextWeek = { weekOffset++ },
                        onThisWeek = { weekOffset = 0 },
                        onAdd = onAdd,
                        onToggle = onToggle,
                        onOpen = { sheet = it.id },
                    )
                    TasksView.List -> ListView(
                        tasks = if (mineOnly && me != null) tasks.filter { it.isFor(me) } else tasks,
                        done = done,
                        index = index,
                        people = people,
                        today = today,
                        nowMillis = nowMillis,
                        mineOnly = mineOnly,
                        canFilter = me != null,
                        onFilter = { mineOnly = it },
                        onToggle = onToggle,
                        onOpen = { sheet = it.id },
                    )
                }
            }
        }
    }

    // Looked up in the current tasks so the sheet closes if the task goes away.
    sheet?.let { tasksById[it] }?.let { task ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            TaskSheetContent(
                task = task,
                onEdit = {
                    sheet = null
                    onEdit(task)
                },
                onDelete = {
                    sheet = null
                    onDelete(task)
                },
            )
        }
    }
}

/** What can be done with a task: change it, or delete it; two action cards, one above the other, and a note when it repeats. */
@Composable
private fun TaskSheetContent(task: Task, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).testTag(TasksTestTags.Sheet),
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
        ActionCard(
            title = stringResource(Res.string.tasks_sheet_edit),
            body = stringResource(Res.string.tasks_sheet_edit_body),
            icon = { Icon(painterResource(Res.drawable.ic_adjustments), contentDescription = null) },
            orientation = ActionCardOrientation.Horizontal,
            onClick = onEdit,
            modifier = Modifier.testTag(TasksTestTags.SheetEdit),
        )
        ActionCard(
            title = stringResource(Res.string.tasks_sheet_delete),
            body = stringResource(Res.string.tasks_sheet_delete_body),
            icon = { Icon(painterResource(Res.drawable.ic_trash), contentDescription = null) },
            orientation = ActionCardOrientation.Horizontal,
            onClick = onDelete,
            modifier = Modifier.testTag(TasksTestTags.SheetDelete),
        )
        if (task.repeat != EventRepeat.None) {
            Text(
                stringResource(Res.string.tasks_sheet_repeat_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(TasksTestTags.RepeatNote),
            )
        }
    }
}

@Composable
private fun WeekView(
    tasks: List<Task>,
    index: DoneIndex,
    people: Map<String, String>,
    today: CalendarDate,
    weekStart: WeekStart,
    weekOffset: Int,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onAdd: (CalendarDate?) -> Unit,
    onToggle: (TaskOccurrence) -> Unit,
    onOpen: (Task) -> Unit,
) {
    val days = weekDays(today.plusDays(weekOffset * DaysPerWeek), weekStart)
    WeekHeader(
        days = days,
        showThisWeek = weekOffset != 0,
        onPreviousWeek = onPreviousWeek,
        onNextWeek = onNextWeek,
        onThisWeek = onThisWeek,
        modifier = Modifier.padding(horizontal = ContentPadding),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ContentPadding, end = ContentPadding, top = 16.dp, bottom = FabClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(days, key = { it.epochDay }) { date ->
            DayCard(
                date = date,
                isToday = date == today,
                occurrences = tasksOn(tasks, index, date),
                people = people,
                onAdd = { onAdd(date) },
                onToggle = onToggle,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun WeekHeader(
    days: List<CalendarDate>,
    showThisWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousWeek, modifier = Modifier.testTag(TasksTestTags.PreviousWeek)) {
                // The chevron points right; turn it to point back.
                Icon(
                    painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = stringResource(Res.string.meal_planner_previous_week),
                    modifier = Modifier.rotate(180f),
                )
            }
            Text(
                text = weekRangeText(days),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag(TasksTestTags.WeekRange),
            )
            IconButton(onClick = onNextWeek, modifier = Modifier.testTag(TasksTestTags.NextWeek)) {
                Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = stringResource(Res.string.meal_planner_next_week))
            }
        }
        if (showThisWeek) {
            TextButton(onClick = onThisWeek, modifier = Modifier.testTag(TasksTestTags.ThisWeek)) {
                Text(stringResource(Res.string.meal_planner_this_week))
            }
        }
    }
}

@Composable
private fun DayCard(
    date: CalendarDate,
    isToday: Boolean,
    occurrences: List<TaskOccurrence>,
    people: Map<String, String>,
    onAdd: () -> Unit,
    onToggle: (TaskOccurrence) -> Unit,
    onOpen: (Task) -> Unit,
) {
    val months = stringArrayResource(Res.array.calendar_months)
    val weekday = stringArrayResource(Res.array.meal_planner_weekdays)[date.dayOfWeek]
    val accent = DayAccents[date.dayOfWeek]
    val doneCount = occurrences.count { it.isDone }
    val summary = if (occurrences.isEmpty()) {
        stringResource(Res.string.tasks_summary_none)
    } else {
        stringResource(Res.string.tasks_summary_progress, doneCount, occurrences.size)
    }
    val dayLabel = stringResource(Res.string.tasks_week_day_heading, weekday.take(3), date.day, months[date.month - 1].take(3))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag(TasksTestTags.day(date)),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(6.dp).background(accent))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = weekday,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = buildString {
                                    if (isToday) append(stringResource(Res.string.meal_planner_today)).append(" · ")
                                    append(stringResource(Res.string.meal_planner_day_month, date.day, months[date.month - 1].take(3)))
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(TasksTestTags.daySummary(date)),
                        )
                    }
                    RoundButton(onClick = onAdd, modifier = Modifier.testTag(TasksTestTags.dayAdd(date))) {
                        Icon(
                            painterResource(Res.drawable.ic_plus),
                            contentDescription = stringResource(Res.string.tasks_add_on_day, dayLabel),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                occurrences.forEach { occurrence ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
                    TaskRow(occurrence, people, showDate = false, onToggle = { onToggle(occurrence) }, onOpen = { onOpen(occurrence.task) })
                }
            }
        }
    }
}

@Composable
private fun ListView(
    tasks: List<Task>,
    done: List<TaskDone>,
    index: DoneIndex,
    people: Map<String, String>,
    today: CalendarDate,
    nowMillis: Long,
    mineOnly: Boolean,
    canFilter: Boolean,
    onFilter: (Boolean) -> Unit,
    onToggle: (TaskOccurrence) -> Unit,
    onOpen: (Task) -> Unit,
) {
    val overdueNow = overdue(tasks, index, today)
    val todayNow = tasksOn(tasks, index, today)
    val upcomingNow = upcoming(tasks, index, today.plusDays(1), UpcomingDays)
    val anytimeNow = anytime(tasks, index)
    val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
    val recent = recentlyDone(tasks, done.filter { it.taskId in taskIds }, nowMillis, RecentDays).map { (task, record) ->
        TaskOccurrence(task, parseIsoDate(record.occurrence), record)
    }
    val sections = listOf(
        Triple("overdue", Res.string.tasks_section_overdue, overdueNow),
        Triple("today", Res.string.tasks_section_today, todayNow),
        Triple("upcoming", Res.string.tasks_section_upcoming, upcomingNow),
        Triple("anytime", Res.string.tasks_section_anytime, anytimeNow),
        Triple("done", Res.string.tasks_section_done, recent),
    ).filter { it.third.isNotEmpty() }

    if (canFilter) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = ContentPadding, vertical = 4.dp)) {
            FilterChip(
                selected = mineOnly,
                onClick = { onFilter(true) },
                label = { Text(stringResource(Res.string.tasks_filter_mine)) },
                modifier = Modifier.testTag(TasksTestTags.FilterMine),
            )
            FilterChip(
                selected = !mineOnly,
                onClick = { onFilter(false) },
                label = { Text(stringResource(Res.string.tasks_filter_everyone)) },
                modifier = Modifier.testTag(TasksTestTags.FilterEveryone),
            )
        }
    }
    if (sections.isEmpty()) {
        EmptyState(
            art = EmptyStateArt.QuietDay,
            title = stringResource(if (mineOnly) Res.string.tasks_empty_mine_title else Res.string.tasks_empty_title),
            body = stringResource(if (mineOnly) Res.string.tasks_empty_mine_body else Res.string.tasks_empty_body),
            modifier = Modifier.fillMaxSize().testTag(TasksTestTags.Empty),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ContentPadding, end = ContentPadding, top = 8.dp, bottom = FabClearance),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sections.forEach { (name, title, occurrences) ->
            item(key = "header-$name") {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (name == "overdue") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).testTag(TasksTestTags.section(name)),
                )
            }
            items(occurrences, key = { "$name-${it.task.id}-${it.occurrence}" }) { occurrence ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceBright,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TaskRow(
                        occurrence,
                        people,
                        showDate = name != "today" && name != "anytime",
                        onToggle = { onToggle(occurrence) },
                        onOpen = { onOpen(occurrence.task) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/** A circle that is empty while the task is open and filled with a tick once it is done. */
@Composable
private fun TickCircle(done: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }) {
        Surface(
            shape = CircleShape,
            color = if (done) accent else Color.Transparent,
            border = if (done) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(TickSize),
        ) {
            if (done) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** One occurrence of a task: the tick, the title, and a line saying when, who it is for and (once done) who did it. */
@Composable
private fun TaskRow(
    occurrence: TaskOccurrence,
    people: Map<String, String>,
    showDate: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = occurrence.task
    val key = occurrence.occurrence
    val title = task.title
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(vertical = 2.dp).testTag(TasksTestTags.row(task.id, key)),
    ) {
        TickCircle(
            done = occurrence.isDone,
            label = stringResource(if (occurrence.isDone) Res.string.tasks_untick else Res.string.tasks_tick, title),
            onClick = onToggle,
            modifier = Modifier.testTag(TasksTestTags.tick(task.id, key)),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (occurrence.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (occurrence.isDone) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = taskSubtitle(occurrence, people, showDate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(TasksTestTags.subtitle(task.id, key)),
            )
        }
        if (task.repeat != EventRepeat.None) {
            Icon(
                painterResource(Res.drawable.ic_repeat),
                contentDescription = stringResource(Res.string.tasks_repeats),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (task.list == TaskList.Private) {
            Icon(
                painterResource(Res.drawable.ic_lock),
                contentDescription = stringResource(Res.string.tasks_private),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).size(18.dp),
            )
        }
    }
}

/**
 * The line under a task's title, its parts joined by a dot: the day ([showDate]) and time, then who it is for, then for a done one who did it
 * and when. A private task is only ever the user's own, so it doesn't say who it is for.
 */
@Composable
internal fun taskSubtitle(occurrence: TaskOccurrence, people: Map<String, String>, showDate: Boolean): String {
    val task = occurrence.task
    val months = stringArrayResource(Res.array.calendar_months)
    val weekdays = stringArrayResource(Res.array.meal_planner_weekdays)
    val parts = buildList {
        val date = occurrence.date
        if (showDate && date != null) {
            add(stringResource(Res.string.tasks_week_day_heading, weekdays[date.dayOfWeek].take(3), date.day, months[date.month - 1].take(3)))
        }
        task.timeMinute?.let { add(formatMinute(it)) }
        if (task.list == TaskList.Home) {
            val names = task.assignees.mapNotNull { people[it] }
            add(if (names.isEmpty()) stringResource(Res.string.tasks_anyone) else names.joinToString(", "))
        }
        occurrence.done?.let { record ->
            val who = people[record.by]
            val whenText = localMoment(record.atMillis).date.let { day ->
                stringResource(Res.string.meal_planner_day_month, day.day, months[day.month - 1].take(3))
            }
            add(
                when {
                    who != null && record.atMillis > 0 -> stringResource(Res.string.tasks_done_by_when, who, whenText)
                    who != null -> stringResource(Res.string.tasks_done_by, who)
                    else -> stringResource(Res.string.tasks_done_by_someone)
                },
            )
        }
    }
    return parts.joinToString(" · ")
}

private val PreviewToday = CalendarDate(2026, 10, 14)
private val PreviewMembers = listOf(
    MemberListItem("a", "Alex", MemberRole.Admin, isYou = true),
    MemberListItem("b", "Sam", MemberRole.Member),
)
private val PreviewTasks = listOf(
    Task("bins", TaskList.Home, "Put the bins out", due = PreviewToday, timeMinute = 7 * 60 + 30, repeat = EventRepeat.Weekly, assignees = setOf("m:b")),
    Task("hoover", TaskList.Home, "Hoover the stairs", due = PreviewToday.plusDays(-3), repeat = EventRepeat.Weekly),
    Task("dentist", TaskList.Home, "Book the dentist", due = PreviewToday.plusDays(2)),
    Task("loft", TaskList.Home, "Sort out the loft"),
    Task("gift", TaskList.Private, "Order Sam's present", due = PreviewToday.plusDays(1)),
)
private val PreviewDone = listOf(
    TaskDone("hoover", "2026-10-11", 1_791_000_000_000L, "m:a"),
    TaskDone("bins", "2026-10-14", 1_791_000_000_000L, "m:b"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun TasksScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { TasksScreen(PreviewTasks, done = PreviewDone, members = PreviewMembers, me = "m:a", today = PreviewToday) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun TasksScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { TasksScreen(PreviewTasks, done = PreviewDone, members = PreviewMembers, me = "m:a", today = PreviewToday) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun TasksScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { TasksScreen(PreviewTasks, done = PreviewDone, members = PreviewMembers, me = "m:a", today = PreviewToday) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun TasksScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { TasksScreen(PreviewTasks, done = PreviewDone, members = PreviewMembers, me = "m:a", today = PreviewToday) }
}

@Preview(name = "Light list", group = "Phone", device = Devices.PHONE)
@Composable
private fun TasksListScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) {
        TasksScreen(PreviewTasks, done = PreviewDone, members = PreviewMembers, me = "m:a", today = PreviewToday, nowMillis = 1_791_100_000_000L, initialView = TasksView.List)
    }
}

@Preview(name = "Dark list", group = "Phone", device = Devices.PHONE)
@Composable
private fun TasksListScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) {
        TasksScreen(PreviewTasks, done = PreviewDone, members = PreviewMembers, me = "m:a", today = PreviewToday, nowMillis = 1_791_100_000_000L, initialView = TasksView.List)
    }
}

@Preview(name = "Light list", group = "Tablet", device = Devices.TABLET)
@Composable
private fun TasksListScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) {
        TasksScreen(PreviewTasks, done = PreviewDone, members = PreviewMembers, me = "m:a", today = PreviewToday, nowMillis = 1_791_100_000_000L, initialView = TasksView.List)
    }
}

@Preview(name = "Dark list", group = "Tablet", device = Devices.TABLET)
@Composable
private fun TasksListScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) {
        TasksScreen(PreviewTasks, done = PreviewDone, members = PreviewMembers, me = "m:a", today = PreviewToday, nowMillis = 1_791_100_000_000L, initialView = TasksView.List)
    }
}
