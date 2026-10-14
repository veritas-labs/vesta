package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.IcalMimeType
import uk.veritaslabs.vesta.calendar.icalCalendar
import uk.veritaslabs.vesta.calendar.icalFileName
import uk.veritaslabs.vesta.files.LocalFileExporter
import uk.veritaslabs.vesta.files.LocalFileImporter
import uk.veritaslabs.vesta.calendar.parseIcal
import vesta.shared.generated.resources.calendar_import_button
import vesta.shared.generated.resources.calendar_import_done
import vesta.shared.generated.resources.calendar_import_done_one
import vesta.shared.generated.resources.calendar_import_into
import vesta.shared.generated.resources.calendar_import_none
import vesta.shared.generated.resources.calendar_import_simplified
import vesta.shared.generated.resources.calendar_import_skipped
import vesta.shared.generated.resources.calendar_import_title
import vesta.shared.generated.resources.calendar_export_button
import vesta.shared.generated.resources.calendar_export_count
import vesta.shared.generated.resources.calendar_export_count_one
import vesta.shared.generated.resources.calendar_export_home
import vesta.shared.generated.resources.calendar_export_private
import vesta.shared.generated.resources.calendar_export_title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarSettings
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.calendar_settings
import vesta.shared.generated.resources.calendar_setting_other_months
import vesta.shared.generated.resources.calendar_setting_start_collapsed
import vesta.shared.generated.resources.calendar_setting_week_numbers
import vesta.shared.generated.resources.calendar_setting_week_start
import vesta.shared.generated.resources.calendar_week_start_monday
import vesta.shared.generated.resources.calendar_week_start_saturday
import vesta.shared.generated.resources.calendar_week_start_sunday
import vesta.shared.generated.resources.ic_arrow_left

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object CalendarSettingsTestTags {
    const val BackButton = "calendar_settings_back_button"
    const val WeekNumbers = "calendar_settings_week_numbers"
    const val OtherMonths = "calendar_settings_other_months"
    const val StartCollapsed = "calendar_settings_start_collapsed"
    const val ExportSection = "calendar_settings_export_section"
    const val ExportButton = "calendar_settings_export_button"
    const val ImportSection = "calendar_settings_import_section"
    const val ImportButton = "calendar_settings_import_button"
    const val ImportResult = "calendar_settings_import_result"

    fun importInto(calendar: EventCalendar) = "calendar_settings_import_into_${calendar.name}"

    fun exportCalendar(calendar: EventCalendar) = "calendar_settings_export_${calendar.name}"
    fun weekStart(start: WeekStart) = "calendar_settings_week_start_${start.name}"
}

/**
 * The calendar's settings: the day the week starts on, whether to show week numbers and the days of neighbouring months,
 * and whether the month grid opens collapsed. Every change calls [onChange] with the new [settings] straight away. Where the app can
 * save files, an Export section lists the Home's calendar ([homeName]) and the user's private one, each with how many of [events] are on it;
 * ticking either or both and tapping Export as iCal saves those events as one `.ics` file. Where the app can open files, an Import section
 * chooses which calendar the events go on and then a `.ics` file; its events are passed to [onImport] and the screen says how many it read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsScreen(
    settings: CalendarSettings,
    onChange: (CalendarSettings) -> Unit,
    modifier: Modifier = Modifier,
    events: List<CalendarEvent> = emptyList(),
    homeName: String = "",
    onImport: (EventCalendar, List<CalendarEvent>) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        TopAppBar(
            title = { Text(stringResource(Res.string.calendar_settings)) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(CalendarSettingsTestTags.BackButton)) {
                    Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.common_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(Res.string.calendar_setting_week_start),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Column(modifier = Modifier.selectableGroup()) {
                WeekStart.entries.forEach { start ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = settings.weekStart == start, role = Role.RadioButton, onClick = { onChange(settings.copy(weekStart = start)) })
                            .testTag(CalendarSettingsTestTags.weekStart(start)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.weekStart == start, onClick = null, modifier = Modifier.padding(end = 12.dp, top = 12.dp, bottom = 12.dp))
                        Text(stringResource(start.label()))
                    }
                }
            }
            SectionDivider()
            SwitchRow(Res.string.calendar_setting_week_numbers, settings.showWeekNumbers, CalendarSettingsTestTags.WeekNumbers) {
                onChange(settings.copy(showWeekNumbers = it))
            }
            SwitchRow(Res.string.calendar_setting_other_months, settings.showOtherMonthDays, CalendarSettingsTestTags.OtherMonths) {
                onChange(settings.copy(showOtherMonthDays = it))
            }
            SwitchRow(Res.string.calendar_setting_start_collapsed, settings.startCollapsed, CalendarSettingsTestTags.StartCollapsed) {
                onChange(settings.copy(startCollapsed = it))
            }
            LocalFileImporter.current?.let { importer ->
                var target by remember { mutableStateOf(EventCalendar.Home) }
                var result by remember { mutableStateOf<ImportOutcome?>(null) }
                val scope = rememberCoroutineScope()
                SectionDivider()
                Column(modifier = Modifier.testTag(CalendarSettingsTestTags.ImportSection), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.calendar_import_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    Text(stringResource(Res.string.calendar_import_into), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.selectableGroup()) {
                        EventCalendar.entries.forEach { calendar ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = target == calendar, role = Role.RadioButton, onClick = { target = calendar })
                                    .testTag(CalendarSettingsTestTags.importInto(calendar)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = target == calendar, onClick = null, modifier = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp))
                                Text(
                                    if (calendar == EventCalendar.Home) stringResource(Res.string.calendar_export_home, homeName).trim()
                                    else stringResource(Res.string.calendar_export_private),
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val file = importer.pick() ?: return@launch
                                val read = parseIcal(file.bytes.decodeToString(), target)
                                if (read.events.isNotEmpty()) onImport(target, read.events)
                                result = ImportOutcome(read.events.size, read.skipped, read.simplifiedRepeats)
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp).testTag(CalendarSettingsTestTags.ImportButton),
                    ) { Text(stringResource(Res.string.calendar_import_button)) }
                    result?.let { outcome ->
                        Column(modifier = Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) {}.testTag(CalendarSettingsTestTags.ImportResult)) {
                            Text(
                                when (outcome.imported) {
                                    0 -> stringResource(Res.string.calendar_import_none)
                                    1 -> stringResource(Res.string.calendar_import_done_one)
                                    else -> stringResource(Res.string.calendar_import_done, outcome.imported)
                                },
                            )
                            if (outcome.skipped > 0) Text(stringResource(Res.string.calendar_import_skipped, outcome.skipped), style = MaterialTheme.typography.bodySmall)
                            if (outcome.simplified > 0) Text(stringResource(Res.string.calendar_import_simplified, outcome.simplified), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            LocalFileExporter.current?.let { exporter ->
                var chosen by remember { mutableStateOf(EventCalendar.entries.toSet()) }
                val scope = rememberCoroutineScope()
                val toExport = events.filter { it.calendar in chosen }
                SectionDivider()
                Column(modifier = Modifier.testTag(CalendarSettingsTestTags.ExportSection), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.calendar_export_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    EventCalendar.entries.forEach { calendar ->
                        val count = events.count { it.calendar == calendar }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = calendar in chosen,
                                    role = Role.Checkbox,
                                    onValueChange = { chosen = if (it) chosen + calendar else chosen - calendar },
                                )
                                .padding(vertical = 4.dp)
                                .testTag(CalendarSettingsTestTags.exportCalendar(calendar)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = calendar in chosen, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                            Column {
                                Text(
                                    if (calendar == EventCalendar.Home) stringResource(Res.string.calendar_export_home, homeName).trim()
                                    else stringResource(Res.string.calendar_export_private),
                                )
                                Text(
                                    if (count == 1) stringResource(Res.string.calendar_export_count_one) else stringResource(Res.string.calendar_export_count, count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val name = exportFileName(chosen, homeName)
                            scope.launch { exporter.export(name, IcalMimeType, icalCalendar(toExport).encodeToByteArray()) }
                        },
                        enabled = toExport.isNotEmpty(),
                        modifier = Modifier.padding(top = 8.dp).testTag(CalendarSettingsTestTags.ExportButton),
                    ) { Text(stringResource(Res.string.calendar_export_button)) }
                }
            }
        }
    }
}

/** What the last import found, for the line under its button. */
private class ImportOutcome(val imported: Int, val skipped: Int, val simplified: Int)

/** `<Home> calendar.ics` for the Home's calendar, `<Home> private calendar.ics` for the private one, `<Home> all events.ics` for both. */
private fun exportFileName(chosen: Set<EventCalendar>, homeName: String): String {
    val home = homeName.trim().ifEmpty { "Vesta" }
    return when (chosen) {
        setOf(EventCalendar.Home) -> icalFileName("$home calendar", "calendar")
        setOf(EventCalendar.Private) -> icalFileName("$home private calendar", "private calendar")
        else -> icalFileName("$home all events", "calendar")
    }
}

private fun WeekStart.label(): StringResource = when (this) {
    WeekStart.Monday -> Res.string.calendar_week_start_monday
    WeekStart.Saturday -> Res.string.calendar_week_start_saturday
    WeekStart.Sunday -> Res.string.calendar_week_start_sunday
}

/** A rule between two logical sections of the screen. */
@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(label: StringResource, checked: Boolean, tag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun CalendarSettingsScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { CalendarSettingsScreen(CalendarSettings(), onChange = {}) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun CalendarSettingsScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { CalendarSettingsScreen(CalendarSettings(showWeekNumbers = true), onChange = {}) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CalendarSettingsScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { CalendarSettingsScreen(CalendarSettings(), onChange = {}) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CalendarSettingsScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { CalendarSettingsScreen(CalendarSettings(weekStart = WeekStart.Sunday), onChange = {}) }
}
