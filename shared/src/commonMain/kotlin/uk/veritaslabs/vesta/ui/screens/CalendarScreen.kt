package uk.veritaslabs.vesta.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.EventOccurrence
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.allDayMillis
import uk.veritaslabs.vesta.calendar.utcMillisOf
import uk.veritaslabs.vesta.calendar.eventColoursOn
import uk.veritaslabs.vesta.calendar.eventsOn
import uk.veritaslabs.vesta.calendar.formatMinute
import uk.veritaslabs.vesta.calendar.today
import uk.veritaslabs.vesta.calendar.CalendarSettings
import uk.veritaslabs.vesta.calendar.LocalCalendarSettings
import uk.veritaslabs.vesta.calendar.monthGrid
import uk.veritaslabs.vesta.calendar.weekNumber
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.components.EmptyStateIllustration
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.calendar_add_event
import vesta.shared.generated.resources.calendar_all_day
import vesta.shared.generated.resources.calendar_event_time_from
import vesta.shared.generated.resources.calendar_event_time_range
import vesta.shared.generated.resources.calendar_event_time_until
import vesta.shared.generated.resources.calendar_private_event
import vesta.shared.generated.resources.ic_lock
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.calendar_back
import vesta.shared.generated.resources.calendar_collapse
import vesta.shared.generated.resources.calendar_expand
import vesta.shared.generated.resources.calendar_months
import vesta.shared.generated.resources.calendar_next_month
import vesta.shared.generated.resources.calendar_next_week
import vesta.shared.generated.resources.calendar_next_year
import vesta.shared.generated.resources.calendar_no_events
import vesta.shared.generated.resources.calendar_pick_cancel
import vesta.shared.generated.resources.calendar_pick_title
import vesta.shared.generated.resources.calendar_previous_month
import vesta.shared.generated.resources.calendar_previous_week
import vesta.shared.generated.resources.calendar_previous_year
import vesta.shared.generated.resources.calendar_week_number
import vesta.shared.generated.resources.calendar_weekdays
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.calendar_more_options
import vesta.shared.generated.resources.calendar_settings
import vesta.shared.generated.resources.ic_chevron_right
import vesta.shared.generated.resources.ic_dots_vertical

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object CalendarTestTags {
    const val BackButton = "calendar_back_button"
    const val Title = "calendar_title"
    const val Month = "calendar_month"
    const val Year = "calendar_year"
    const val Grid = "calendar_grid"
    const val ToggleButton = "calendar_toggle_button"
    const val PreviousButton = "calendar_previous_button"
    const val NextButton = "calendar_next_button"
    const val PickerDialog = "calendar_picker_dialog"
    const val PickerYear = "calendar_picker_year"
    const val PickerPreviousYear = "calendar_picker_previous_year"
    const val PickerNextYear = "calendar_picker_next_year"
    const val SelectedDay = "calendar_selected_day"
    const val Agenda = "calendar_agenda"
    const val OverflowButton = "calendar_overflow_button"
    const val SettingsMenuItem = "calendar_settings_menu_item"
    const val AddButton = "calendar_add_button"
    const val NoEvents = "calendar_no_events"

    fun event(id: String) = "calendar_event_$id"
    fun dots(date: CalendarDate) = "calendar_dots_${date.year}-${date.month}-${date.day}"

    fun pickerMonth(month: Int) = "calendar_picker_month_$month"
    fun day(date: CalendarDate) = "calendar_day_${date.year}-${date.month}-${date.day}"
    fun week(index: Int) = "calendar_week_$index"
    fun weekNumber(index: Int) = "calendar_week_number_$index"
}

private val MaxGridWidth = 480.dp

/**
 * The Home's calendar. The app bar shows the visible month over its year; tapping that opens a dialog to jump to another
 * month and year. Under it is a square month grid (Monday first) that can be collapsed to just the week of the selected day
 * with the handle below it, and swiped sideways to change month. Tapping a day selects it. [today] is highlighted and is
 * where the calendar opens. The overflow menu's Settings calls [onSettingsClick]; [settings] set the first day of the week,
 * week numbers and so on. Under the grid are the [events] of the selected day (tap one for [onEventClick]), and the plus button
 * calls [onAddEvent] with that day; a day with events has a dot of each event's colour in the grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    today: CalendarDate = remember { CalendarDate.today() },
    settings: CalendarSettings = LocalCalendarSettings.current.settings,
    onBack: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    events: List<CalendarEvent> = emptyList(),
    onAddEvent: (CalendarDate) -> Unit = {},
    onEventClick: (CalendarEvent) -> Unit = {},
) {
    var selected by remember { mutableStateOf(today) }
    var visibleYear by remember { mutableStateOf(today.year) }
    var visibleMonth by remember { mutableStateOf(today.month) }
    var expanded by remember { mutableStateOf(!settings.startCollapsed) }
    var picking by remember { mutableStateOf(false) }
    val months = stringArrayResource(Res.array.calendar_months)

    fun showMonth(year: Int, month: Int) {
        visibleYear = year
        visibleMonth = month
        // Keep a day selected that belongs to the month on show: today if it is in that month, otherwise the 1st.
        selected = if (today.year == year && today.month == month) today else CalendarDate(year, month, 1)
    }

    fun shiftMonth(by: Int) {
        val moved = CalendarDate(visibleYear, visibleMonth, 1).plusMonths(by)
        showMonth(moved.year, moved.month)
    }

    fun shiftWeek(by: Int) {
        val moved = selected.plusDays(7 * by)
        selected = moved
        visibleYear = moved.year
        visibleMonth = moved.month
    }

    // Collapsed, the calendar steps a week at a time; expanded, a month.
    fun step(by: Int) = if (expanded) shiftMonth(by) else shiftWeek(by)

    val dayEvents = remember(events, selected) { eventsOn(events, selected) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .testTag(CalendarTestTags.Title)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { picking = true }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Text(
                        months[visibleMonth - 1],
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(CalendarTestTags.Month),
                    )
                    Text(
                        visibleYear.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(CalendarTestTags.Year),
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(CalendarTestTags.BackButton)) {
                    Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.calendar_back))
                }
            },
            actions = {
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag(CalendarTestTags.OverflowButton)) {
                        Icon(painterResource(Res.drawable.ic_dots_vertical), contentDescription = stringResource(Res.string.calendar_more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.calendar_settings)) },
                            onClick = {
                                menuExpanded = false
                                onSettingsClick()
                            },
                            modifier = Modifier.testTag(CalendarTestTags.SettingsMenuItem),
                        )
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MonthGrid(
                year = visibleYear,
                month = visibleMonth,
                today = today,
                settings = settings,
                selected = selected,
                events = events,
                expanded = expanded,
                onSelect = { date ->
                    selected = date
                    // A day greyed out from a neighbouring month takes the grid to that month.
                    visibleYear = date.year
                    visibleMonth = date.month
                },
                onSwipe = ::step,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { step(-1) }, modifier = Modifier.testTag(CalendarTestTags.PreviousButton)) {
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = stringResource(if (expanded) Res.string.calendar_previous_month else Res.string.calendar_previous_week),
                        modifier = Modifier.rotate(180f),
                    )
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag(CalendarTestTags.ToggleButton)) {
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = stringResource(if (expanded) Res.string.calendar_collapse else Res.string.calendar_expand),
                        modifier = Modifier.rotate(if (expanded) -90f else 90f),
                    )
                }
                IconButton(onClick = { step(1) }, modifier = Modifier.testTag(CalendarTestTags.NextButton)) {
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = stringResource(if (expanded) Res.string.calendar_next_month else Res.string.calendar_next_week),
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp).testTag(CalendarTestTags.Agenda)) {
            Text(
                "${selected.day} ${months[selected.month - 1]} ${selected.year}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(CalendarTestTags.SelectedDay),
            )
            if (dayEvents.isEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    // As big as fits above the words, up to 200dp.
                    val pictureSize = (maxHeight - 48.dp).coerceIn(0.dp, 200.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (pictureSize >= 48.dp) EmptyStateIllustration(EmptyStateArt.QuietDay, size = pictureSize)
                        Text(
                            stringResource(Res.string.calendar_no_events),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(CalendarTestTags.NoEvents),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.widthIn(max = MaxGridWidth * 1.5f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(dayEvents, key = { it.event.id }) { occurrence ->
                        EventRow(occurrence, onClick = { onEventClick(occurrence.event) })
                    }
                }
            }
        }
    }
    FloatingActionButton(
        onClick = { onAddEvent(selected) },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag(CalendarTestTags.AddButton),
    ) {
        Icon(painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.calendar_add_event))
    }
    }

    if (picking) {
        MonthYearPickerDialog(
            year = visibleYear,
            month = visibleMonth,
            onPick = { year, month ->
                picking = false
                showMonth(year, month)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    today: CalendarDate,
    settings: CalendarSettings,
    selected: CalendarDate,
    events: List<CalendarEvent>,
    expanded: Boolean,
    onSelect: (CalendarDate) -> Unit,
    onSwipe: (Int) -> Unit,
) {
    val weeks = remember(year, month, settings.weekStart) { monthGrid(year, month, settings.weekStart) }
    val allWeekdays = stringArrayResource(Res.array.calendar_weekdays)
    val weekdays = List(7) { allWeekdays[(settings.weekStart.dayOfWeek + it) % 7] }
    // The week-number column is half a cell wide; the cells stay square.
    val numberColumn = if (settings.showWeekNumbers) 0.5f else 0f
    // Collapsed, only the week holding the selected day stays (the first week when the selection is in another month).
    val shownWeeks = if (expanded) weeks.withIndex().toList() else weeks.withIndex().filter { (_, week) -> selected in week }.ifEmpty { weeks.withIndex().take(1) }
    var dragged by remember { mutableStateOf(0f) }

    // One header row plus six week rows make seven rows of seven cells: a square. Collapsed, it is two rows.
    Column(
        modifier = Modifier
            .widthIn(max = MaxGridWidth)
            .fillMaxWidth()
            .aspectRatio((7f + numberColumn) / (if (expanded) 7f else 2f))
            .animateContentSize()
            .testTag(CalendarTestTags.Grid)
            .pointerInput(year, month, expanded) {
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged > 80f) onSwipe(-1) else if (dragged < -80f) onSwipe(1)
                    },
                ) { _, amount -> dragged += amount }
            },
    ) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (numberColumn > 0f) Spacer(Modifier.weight(numberColumn))
            weekdays.forEach { name ->
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        shownWeeks.forEach { (index, week) ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth().testTag(CalendarTestTags.week(index))) {
                if (numberColumn > 0f) Box(modifier = Modifier.weight(numberColumn).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.calendar_week_number, weekNumber(week)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(CalendarTestTags.weekNumber(index)),
                    )
                }
                week.forEach { date ->
                    DayCell(
                        date = date,
                        inMonth = date.month == month,
                        showOtherMonth = settings.showOtherMonthDays,
                        isToday = date == today,
                        isSelected = date == selected,
                        colours = remember(events, date) { eventColoursOn(events, date) },
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: CalendarDate,
    inMonth: Boolean,
    showOtherMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    colours: List<Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    if (!inMonth && !showOtherMonth) {
        Box(modifier)
        return
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(scheme.primary) else if (isToday) Modifier.border(1.dp, scheme.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .testTag(CalendarTestTags.day(date)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = when {
                isSelected -> scheme.onPrimary
                inMonth -> scheme.onBackground
                else -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
        if (colours.isNotEmpty()) {
            // A dot for each colour of event the day has, up to three, under the number.
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).testTag(CalendarTestTags.dots(date)),
            ) {
                colours.take(MaxDots).forEach { colour ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(eventColour(colour)))
                }
            }
        }
    }
}

private const val MaxDots = 3

/** One event in the day's list: a bar in its colour, its title and when it is, and a padlock if it is on the private calendar. */
@Composable
private fun EventRow(occurrence: EventOccurrence, onClick: () -> Unit) {
    val event = occurrence.event
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .testTag(CalendarTestTags.event(event.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).height(56.dp).background(eventColour(event.color)))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(timeLabel(occurrence), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (event.calendar == EventCalendar.Private) {
            Icon(
                painterResource(Res.drawable.ic_lock),
                contentDescription = stringResource(Res.string.calendar_private_event),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun timeLabel(occurrence: EventOccurrence): String {
    val start = occurrence.startMinute
    val end = occurrence.endMinute
    return when {
        occurrence.wholeDay || start == null || end == null -> stringResource(Res.string.calendar_all_day)
        occurrence.isFirstDay && occurrence.isLastDay -> stringResource(Res.string.calendar_event_time_range, formatMinute(start), formatMinute(end))
        occurrence.isFirstDay -> stringResource(Res.string.calendar_event_time_from, formatMinute(start))
        else -> stringResource(Res.string.calendar_event_time_until, formatMinute(end))
    }
}

@Composable
private fun MonthYearPickerDialog(year: Int, month: Int, onPick: (year: Int, month: Int) -> Unit, onDismiss: () -> Unit) {
    var pickerYear by remember { mutableStateOf(year) }
    val months = stringArrayResource(Res.array.calendar_months)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(CalendarTestTags.PickerDialog),
        title = { Text(stringResource(Res.string.calendar_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { pickerYear-- }, modifier = Modifier.testTag(CalendarTestTags.PickerPreviousYear)) {
                        Icon(
                            painterResource(Res.drawable.ic_chevron_right),
                            contentDescription = stringResource(Res.string.calendar_previous_year),
                            modifier = Modifier.rotate(180f),
                        )
                    }
                    Text(pickerYear.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(CalendarTestTags.PickerYear))
                    IconButton(onClick = { pickerYear++ }, modifier = Modifier.testTag(CalendarTestTags.PickerNextYear)) {
                        Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = stringResource(Res.string.calendar_next_year))
                    }
                }
                // Twelve months, three to a row.
                months.chunked(3).forEachIndexed { row, names ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.forEachIndexed { column, name ->
                            val number = row * 3 + column + 1
                            FilterChip(
                                selected = pickerYear == year && number == month,
                                onClick = { onPick(pickerYear, number) },
                                label = { Text(name.take(3), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f).testTag(CalendarTestTags.pickerMonth(number)),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.calendar_pick_cancel)) } },
    )
}

private val PreviewToday = CalendarDate(2026, 10, 9)
private val PreviewEvents = listOf(
    CalendarEvent("1", EventCalendar.Home, "Parents' evening", false, utcMillisOf(PreviewToday, 18 * 60), utcMillisOf(PreviewToday, 19 * 60), color = 2),
    CalendarEvent("2", EventCalendar.Private, "Gym", false, utcMillisOf(PreviewToday, 7 * 60), utcMillisOf(PreviewToday, 8 * 60), color = 4),
    CalendarEvent("3", EventCalendar.Home, "Bin day", true, allDayMillis(PreviewToday.plusDays(3)), allDayMillis(PreviewToday.plusDays(3)), EventRepeat.Weekly, color = 1),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun CalendarScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { CalendarScreen(today = PreviewToday, events = PreviewEvents) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun CalendarScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { CalendarScreen(today = PreviewToday, events = PreviewEvents) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CalendarScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { CalendarScreen(today = PreviewToday, events = PreviewEvents) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CalendarScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { CalendarScreen(today = PreviewToday, events = PreviewEvents) }
}
