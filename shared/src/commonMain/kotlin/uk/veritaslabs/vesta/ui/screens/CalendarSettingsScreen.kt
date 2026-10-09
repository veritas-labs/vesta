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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import vesta.shared.generated.resources.calendar_back
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
    fun weekStart(start: WeekStart) = "calendar_settings_week_start_${start.name}"
}

/**
 * The calendar's settings: the day the week starts on, whether to show week numbers and the days of neighbouring months,
 * and whether the month grid opens collapsed. Every change calls [onChange] with the new [settings] straight away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsScreen(
    settings: CalendarSettings,
    onChange: (CalendarSettings) -> Unit,
    modifier: Modifier = Modifier,
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
                    Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.calendar_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(Res.string.calendar_setting_week_start),
                style = MaterialTheme.typography.titleSmall,
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
            SwitchRow(Res.string.calendar_setting_week_numbers, settings.showWeekNumbers, CalendarSettingsTestTags.WeekNumbers) {
                onChange(settings.copy(showWeekNumbers = it))
            }
            SwitchRow(Res.string.calendar_setting_other_months, settings.showOtherMonthDays, CalendarSettingsTestTags.OtherMonths) {
                onChange(settings.copy(showOtherMonthDays = it))
            }
            SwitchRow(Res.string.calendar_setting_start_collapsed, settings.startCollapsed, CalendarSettingsTestTags.StartCollapsed) {
                onChange(settings.copy(startCollapsed = it))
            }
        }
    }
}

private fun WeekStart.label(): StringResource = when (this) {
    WeekStart.Monday -> Res.string.calendar_week_start_monday
    WeekStart.Saturday -> Res.string.calendar_week_start_saturday
    WeekStart.Sunday -> Res.string.calendar_week_start_sunday
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
