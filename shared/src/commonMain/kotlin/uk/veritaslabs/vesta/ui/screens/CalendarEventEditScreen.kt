package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.spaces.MemberRole
import uk.veritaslabs.kits.spaces.MemberListItem
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.calendar.IcalMimeType
import uk.veritaslabs.vesta.calendar.icalFileName
import uk.veritaslabs.vesta.calendar.toIcal
import uk.veritaslabs.vesta.files.LocalFileExporter
import vesta.shared.generated.resources.event_edit_export_ical
import vesta.shared.generated.resources.event_edit_more_options
import vesta.shared.generated.resources.ic_dots_vertical
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.EventColourCount
import uk.veritaslabs.vesta.calendar.EventDraft
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.allDayMillis
import uk.veritaslabs.vesta.calendar.formatMinute
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.event_colours
import vesta.shared.generated.resources.event_edit_add_participants
import vesta.shared.generated.resources.event_edit_add_title
import vesta.shared.generated.resources.event_edit_all_day
import vesta.shared.generated.resources.event_edit_calendar_home
import vesta.shared.generated.resources.event_edit_calendar_private
import vesta.shared.generated.resources.event_edit_calendar_private_note
import vesta.shared.generated.resources.common_cancel
import vesta.shared.generated.resources.event_edit_close
import vesta.shared.generated.resources.event_edit_colour
import vesta.shared.generated.resources.event_edit_delete
import vesta.shared.generated.resources.event_edit_description
import vesta.shared.generated.resources.event_edit_edit_title
import vesta.shared.generated.resources.event_edit_ends
import vesta.shared.generated.resources.event_edit_ends_before_start
import vesta.shared.generated.resources.event_edit_location
import vesta.shared.generated.resources.event_edit_participants_done
import vesta.shared.generated.resources.event_edit_participants_title
import vesta.shared.generated.resources.event_edit_pick_ok
import vesta.shared.generated.resources.event_edit_repeat_daily
import vesta.shared.generated.resources.event_edit_repeat_monthly
import vesta.shared.generated.resources.event_edit_repeat_none
import vesta.shared.generated.resources.event_edit_repeat_weekly
import vesta.shared.generated.resources.event_edit_repeat_yearly
import vesta.shared.generated.resources.event_edit_save
import vesta.shared.generated.resources.event_edit_starts
import vesta.shared.generated.resources.event_edit_title_hint
import vesta.shared.generated.resources.calendar_months
import vesta.shared.generated.resources.ic_align_left
import vesta.shared.generated.resources.ic_calendar
import vesta.shared.generated.resources.ic_clock
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.ic_lock
import vesta.shared.generated.resources.ic_map_pin
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_repeat
import vesta.shared.generated.resources.ic_trash
import vesta.shared.generated.resources.ic_users

private val EventEditTabletBreakpoint = 600.dp
private const val MillisPerDay = 86_400_000L

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object EventEditTestTags {
    const val CancelButton = "event_edit_cancel_button"
    const val SaveButton = "event_edit_save_button"
    const val DeleteButton = "event_edit_delete_button"
    const val OverflowButton = "event_edit_overflow_button"
    const val ExportMenuItem = "event_edit_export_menu_item"
    const val TitleField = "event_edit_title"
    const val StartDate = "event_edit_start_date"
    const val StartTime = "event_edit_start_time"
    const val EndDate = "event_edit_end_date"
    const val EndTime = "event_edit_end_time"
    const val AllDay = "event_edit_all_day"
    const val Repeat = "event_edit_repeat"
    const val CalendarField = "event_edit_calendar"
    const val ColourField = "event_edit_colour"
    const val ParticipantsField = "event_edit_participants"
    const val ParticipantsDialog = "event_edit_participants_dialog"
    const val ParticipantsDone = "event_edit_participants_done"
    const val LocationField = "event_edit_location"
    const val DescriptionField = "event_edit_description"
    const val EndsBeforeStart = "event_edit_ends_before_start"
    const val PrivateNote = "event_edit_private_note"
    const val PickerOk = "event_edit_picker_ok"

    fun repeatOption(repeat: EventRepeat) = "event_edit_repeat_${repeat.name}"

    fun calendarOption(calendar: EventCalendar) = "event_edit_calendar_${calendar.name}"

    fun colourOption(index: Int) = "event_edit_colour_$index"

    fun participant(ref: String) = "event_edit_participant_$ref"

    fun participantChip(ref: String) = "event_edit_participant_chip_$ref"
}

/**
 * The form for adding an event on [date] ([event] null) or editing [event] (ADR-029), laid out like Proton Calendar's: title; start and
 * end date and time with an All day box and how it repeats; which calendar it is on (the Home's, or the user's private one) and its
 * colour; participants chosen from the Home's [members] (Home calendar only); location; description. Save is enabled once there is a
 * title and the end is not before the start. Editing also shows Delete. [homeName] names the Home calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEventEditScreen(
    event: CalendarEvent?,
    date: CalendarDate,
    modifier: Modifier = Modifier,
    homeName: String = "",
    members: List<MemberListItem> = emptyList(),
    onSave: (CalendarEvent) -> Unit = {},
    onDelete: (CalendarEvent) -> Unit = {},
    onCancel: () -> Unit = {},
    newId: () -> String = { "" },
) {
    var draft by remember(event) { mutableStateOf(event?.let { EventDraft.of(it) } ?: EventDraft.on(date)) }
    var picking by remember { mutableStateOf<DateTimeField?>(null) }
    var choosingParticipants by remember { mutableStateOf(false) }
    val endsBeforeStart = draft.title.isNotBlank() && !draft.isValid()
    val months = stringArrayResource(Res.array.calendar_months)
    val colourNames = stringArrayResource(Res.array.event_colours)
    val exporter = LocalFileExporter.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= EventEditTabletBreakpoint) 24.dp else 16.dp
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(if (event == null) Res.string.event_edit_add_title else Res.string.event_edit_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag(EventEditTestTags.CancelButton)) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.common_cancel))
                    }
                },
                actions = {
                    event?.let {
                        IconButton(onClick = { onDelete(it) }, modifier = Modifier.testTag(EventEditTestTags.DeleteButton)) {
                            Icon(painterResource(Res.drawable.ic_trash), contentDescription = stringResource(Res.string.event_edit_delete))
                        }
                    }
                    if (event != null && exporter != null) {
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(EventEditTestTags.OverflowButton)) {
                                Icon(painterResource(Res.drawable.ic_dots_vertical), contentDescription = stringResource(Res.string.event_edit_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.event_edit_export_ical)) },
                                    onClick = {
                                        menuOpen = false
                                        // What is on the form if it is a valid event, so edits not yet saved are exported too.
                                        val exported = if (draft.isValid()) draft.toEvent(event.id) else event
                                        scope.launch { exporter.export(exported.icalFileName(), IcalMimeType, exported.toIcal().encodeToByteArray()) }
                                    },
                                    modifier = Modifier.testTag(EventEditTestTags.ExportMenuItem),
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = contentPadding)
                    .padding(bottom = 32.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { draft = draft.copy(title = it) },
                        placeholder = { Text(stringResource(Res.string.event_edit_title_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(EventEditTestTags.TitleField),
                    )

                    // When: two rows of a date and a time, as Proton's form has.
                    FormRow(Res.drawable.ic_clock) {
                        DateTimeRow(
                            label = stringResource(Res.string.event_edit_starts),
                            dateText = dateText(draft.startDate, months),
                            timeText = formatMinute(draft.startMinute),
                            showTime = !draft.allDay,
                            onDateClick = { picking = DateTimeField.StartDate },
                            onTimeClick = { picking = DateTimeField.StartTime },
                            dateTag = EventEditTestTags.StartDate,
                            timeTag = EventEditTestTags.StartTime,
                        )
                    }
                    FormRow(Res.drawable.ic_clock) {
                        DateTimeRow(
                            label = stringResource(Res.string.event_edit_ends),
                            dateText = dateText(draft.endDate, months),
                            timeText = formatMinute(draft.endMinute),
                            showTime = !draft.allDay,
                            onDateClick = { picking = DateTimeField.EndDate },
                            onTimeClick = { picking = DateTimeField.EndTime },
                            dateTag = EventEditTestTags.EndDate,
                            timeTag = EventEditTestTags.EndTime,
                        )
                    }
                    FormRow(null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (endsBeforeStart) {
                                Text(
                                    stringResource(Res.string.event_edit_ends_before_start),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag(EventEditTestTags.EndsBeforeStart),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { draft = draft.copy(allDay = !draft.allDay) }.padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = draft.allDay, onCheckedChange = { draft = draft.copy(allDay = it) }, modifier = Modifier.testTag(EventEditTestTags.AllDay))
                                    Text(stringResource(Res.string.event_edit_all_day))
                                }
                                ChoiceMenu(
                                    current = repeatLabel(draft.repeat),
                                    options = EventRepeat.entries,
                                    label = { repeatLabel(it) },
                                    optionTag = EventEditTestTags::repeatOption,
                                    onChoose = { draft = draft.copy(repeat = it) },
                                    tag = EventEditTestTags.Repeat,
                                    leadingIcon = Res.drawable.ic_repeat,
                                )
                            }
                        }
                    }

                    // Which calendar, and the colour.
                    FormRow(Res.drawable.ic_calendar) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChoiceMenu(
                                current = calendarLabel(draft.calendar, homeName),
                                options = EventCalendar.entries,
                                label = { calendarLabel(it, homeName) },
                                optionTag = EventEditTestTags::calendarOption,
                                onChoose = { draft = draft.withCalendar(it) },
                                tag = EventEditTestTags.CalendarField,
                                modifier = Modifier.weight(1f),
                                fillWidth = true,
                            )
                            ColourMenu(
                                selected = draft.color,
                                names = colourNames,
                                onChoose = { draft = draft.copy(color = it) },
                                tag = EventEditTestTags.ColourField,
                            )
                        }
                    }
                    if (draft.calendar == EventCalendar.Private) {
                        FormRow(Res.drawable.ic_lock) {
                            Text(
                                stringResource(Res.string.event_edit_calendar_private_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag(EventEditTestTags.PrivateNote),
                            )
                        }
                    } else {
                        FormRow(Res.drawable.ic_users) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                IconButton(onClick = { choosingParticipants = true }, modifier = Modifier.testTag(EventEditTestTags.ParticipantsField)) {
                                    Icon(painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.event_edit_add_participants))
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                ) {
                                    val chosen = members.filter { memberRef(it.memberId) in draft.participants }
                                    chosen.forEach { member ->
                                        InputChip(
                                            selected = true,
                                            onClick = { draft = draft.copy(participants = draft.participants - memberRef(member.memberId)) },
                                            label = { Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            trailingIcon = { Icon(painterResource(Res.drawable.ic_close), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            modifier = Modifier.testTag(EventEditTestTags.participantChip(memberRef(member.memberId))),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    FormRow(Res.drawable.ic_map_pin) {
                        OutlinedTextField(
                            value = draft.location,
                            onValueChange = { draft = draft.copy(location = it) },
                            placeholder = { Text(stringResource(Res.string.event_edit_location)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().testTag(EventEditTestTags.LocationField),
                        )
                    }
                    FormRow(Res.drawable.ic_align_left, topAligned = true) {
                        OutlinedTextField(
                            value = draft.description,
                            onValueChange = { draft = draft.copy(description = it) },
                            placeholder = { Text(stringResource(Res.string.event_edit_description)) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag(EventEditTestTags.DescriptionField),
                        )
                    }
                    PrimaryButton(
                        label = stringResource(Res.string.event_edit_save),
                        enabled = draft.isValid(),
                        onClick = { onSave(draft.toEvent(event?.id ?: newId())) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(EventEditTestTags.SaveButton),
                    )
                }
            }
        }
    }

    picking?.let { field ->
        when (field) {
            DateTimeField.StartDate, DateTimeField.EndDate -> DatePickerPopup(
                initial = if (field == DateTimeField.StartDate) draft.startDate else draft.endDate,
                onPick = { picked ->
                    draft = if (field == DateTimeField.StartDate) draft.withStart(date = picked) else draft.copy(endDate = picked)
                    picking = null
                },
                onDismiss = { picking = null },
            )
            DateTimeField.StartTime, DateTimeField.EndTime -> TimePickerPopup(
                initial = if (field == DateTimeField.StartTime) draft.startMinute else draft.endMinute,
                onPick = { minute ->
                    draft = if (field == DateTimeField.StartTime) draft.withStart(minute = minute) else draft.copy(endMinute = minute)
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
    }
    if (choosingParticipants) {
        ParticipantsDialog(
            members = members,
            chosen = draft.participants,
            onChange = { draft = draft.copy(participants = it) },
            onDismiss = { choosingParticipants = false },
        )
    }
}

private enum class DateTimeField { StartDate, StartTime, EndDate, EndTime }

internal fun dateText(date: CalendarDate, months: List<String>) = "${date.day} ${months[date.month - 1].take(3)} ${date.year}"

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

@Composable
private fun calendarLabel(calendar: EventCalendar, homeName: String) =
    if (calendar == EventCalendar.Home) stringResource(Res.string.event_edit_calendar_home, homeName).trim() else stringResource(Res.string.event_edit_calendar_private)

/** A form line: an icon in the left margin and its [content] beside it. */
@Composable
internal fun FormRow(icon: DrawableResource?, topAligned: Boolean = false, content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = if (topAligned) Alignment.Top else Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        icon?.let {
            Icon(
                painterResource(it),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.then(if (topAligned) Modifier.padding(top = 10.dp) else Modifier).size(20.dp),
            )
        } ?: Spacer(Modifier.size(20.dp))
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    dateText: String,
    timeText: String,
    showTime: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    dateTag: String,
    timeTag: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
        OutlinedButton(onClick = onDateClick, modifier = Modifier.weight(1f).testTag(dateTag)) { Text(dateText, maxLines = 1) }
        if (showTime) OutlinedButton(onClick = onTimeClick, modifier = Modifier.testTag(timeTag)) { Text(timeText) }
    }
}

/** A button showing [current] that opens a menu of [options]. */
@Composable
internal fun <T> ChoiceMenu(
    current: String,
    options: List<T>,
    label: @Composable (T) -> String,
    optionTag: (T) -> String,
    onChoose: (T) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    leadingIcon: DrawableResource? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier).testTag(tag)) {
            leadingIcon?.let {
                Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        open = false
                        onChoose(option)
                    },
                    modifier = Modifier.testTag(optionTag(option)),
                )
            }
        }
    }
}

/** A round swatch of the chosen colour that opens a menu of the palette. */
@Composable
private fun ColourMenu(selected: Int, names: List<String>, onChoose: (Int) -> Unit, tag: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag(tag)) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(eventColour(selected)))
            Spacer(Modifier.width(6.dp))
            Text(names[selected.coerceIn(0, EventColourCount - 1)], maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(eventColour(index)).border(if (index == selected) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)) },
                    onClick = {
                        open = false
                        onChoose(index)
                    },
                    modifier = Modifier.testTag(EventEditTestTags.colourOption(index)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerPopup(initial: CalendarDate, onPick: (CalendarDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = allDayMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(CalendarDate.fromEpochDay(it.floorDiv(MillisPerDay))) } ?: onDismiss() },
                modifier = Modifier.testTag(EventEditTestTags.PickerOk),
            ) { Text(stringResource(Res.string.event_edit_pick_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerPopup(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    // The vertical layout always: the automatic one goes horizontal on a short window and the dial then overlaps the digits in a dialog this narrow.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state, layoutType = TimePickerLayoutType.Vertical)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
                    TextButton(onClick = { onPick(state.hour * 60 + state.minute) }, modifier = Modifier.testTag(EventEditTestTags.PickerOk)) {
                        Text(stringResource(Res.string.event_edit_pick_ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantsDialog(members: List<MemberListItem>, chosen: Set<String>, onChange: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(EventEditTestTags.ParticipantsDialog),
        title = { Text(stringResource(Res.string.event_edit_participants_title)) },
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
                            .testTag(EventEditTestTags.participant(ref)),
                    ) {
                        Checkbox(checked = ref in chosen, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                        Text(member.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(EventEditTestTags.ParticipantsDone)) {
                Text(stringResource(Res.string.event_edit_participants_done))
            }
        },
    )
}

private val PreviewDate = CalendarDate(2026, 10, 14)
private val PreviewMembers = listOf(
    MemberListItem("a", "Alex", MemberRole.Admin, isYou = true),
    MemberListItem("b", "Sam", MemberRole.Member),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun CalendarEventEditScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { CalendarEventEditScreen(event = null, date = PreviewDate, homeName = "Our house", members = PreviewMembers) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun CalendarEventEditScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { CalendarEventEditScreen(event = null, date = PreviewDate, homeName = "Our house", members = PreviewMembers) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CalendarEventEditScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { CalendarEventEditScreen(event = null, date = PreviewDate, homeName = "Our house", members = PreviewMembers) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CalendarEventEditScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { CalendarEventEditScreen(event = null, date = PreviewDate, homeName = "Our house", members = PreviewMembers) }
}
