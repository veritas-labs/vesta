package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.calendar_months
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.meal_planner_copy_already
import vesta.shared.generated.resources.meal_planner_copy_back
import vesta.shared.generated.resources.meal_planner_copy_confirm
import vesta.shared.generated.resources.meal_planner_copy_title
import vesta.shared.generated.resources.meal_planner_day_heading
import vesta.shared.generated.resources.meal_planner_day_month
import vesta.shared.generated.resources.meal_planner_eaters_for
import vesta.shared.generated.resources.meal_planner_pick_for
import vesta.shared.generated.resources.meal_planner_weekdays

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MealPlannerCopyTestTags {
    const val Screen = "meal_planner_copy_screen"
    const val BackButton = "meal_planner_copy_back"
    const val Confirm = "meal_planner_copy_confirm"

    private fun dateTag(date: CalendarDate) = "${date.year}-${date.month}-${date.day}"

    fun day(date: CalendarDate) = "meal_planner_copy_day_${dateTag(date)}"

    fun checkbox(date: CalendarDate) = "meal_planner_copy_checkbox_${dateTag(date)}"
}

/** The weeks a recipe planned on [date] can be copied into: its own week and the next, each in full ([date] is left out when listed). */
fun copyTargetWeeks(date: CalendarDate, weekStart: WeekStart): List<List<CalendarDate>> =
    listOf(weekDays(date, weekStart), weekDays(date.plusDays(7), weekStart))

/**
 * Where a recipe planned for [slot] on [date] is copied to other days (ADR-028): each day of [weeks] but [date] with a checkbox, those in
 * [alreadyPlanned] (they have the recipe in that slot) ticked-off and disabled. The Copy button, enabled once a day is ticked, calls
 * [onCopy] with the ticked days; back calls [onBack] having copied nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlannerCopyScreen(
    recipeTitle: String,
    date: CalendarDate,
    slot: MealSlot,
    weeks: List<List<CalendarDate>>,
    modifier: Modifier = Modifier,
    alreadyPlanned: Set<CalendarDate> = emptySet(),
    onCopy: (Set<CalendarDate>) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var ticked by remember { mutableStateOf(emptySet<CalendarDate>()) }
    val weekdays = stringArrayResource(Res.array.meal_planner_weekdays)
    val months = stringArrayResource(Res.array.calendar_months)
    val subtitle = stringResource(
        Res.string.meal_planner_eaters_for,
        recipeTitle,
        stringResource(
            Res.string.meal_planner_pick_for,
            mealSlotLabel(slot),
            stringResource(Res.string.meal_planner_day_heading, weekdays[date.dayOfWeek].take(3), date.day),
        ),
    )
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(MealPlannerCopyTestTags.Screen)) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(Res.string.meal_planner_copy_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(MealPlannerCopyTestTags.BackButton)) {
                        Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.meal_planner_copy_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                weeks.forEach { week ->
                    item(key = "week-${week.first().epochDay}") { Heading(weekRangeText(week)) }
                    items(week.filter { it != date }, key = { it.epochDay }) { day ->
                        val already = day in alreadyPlanned
                        val toggle = { ticked = if (day in ticked) ticked - day else ticked + day }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().testTag(MealPlannerCopyTestTags.day(day)),
                        ) {
                            Checkbox(
                                checked = day in ticked,
                                onCheckedChange = { toggle() },
                                enabled = !already,
                                modifier = Modifier.testTag(MealPlannerCopyTestTags.checkbox(day)),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = !already, role = Role.Checkbox, onClick = toggle)
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    text = weekdays[day.dayOfWeek] + " " + stringResource(Res.string.meal_planner_day_month, day.day, months[day.month - 1].take(3)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (already) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                if (already) {
                                    Text(
                                        stringResource(Res.string.meal_planner_copy_already),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onCopy(ticked) },
                enabled = ticked.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(MealPlannerCopyTestTags.Confirm),
            ) { Text(stringResource(Res.string.meal_planner_copy_confirm)) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}

private val PreviewDate = CalendarDate(2026, 10, 14)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerCopyPhoneLightPreview() {
    VestaTheme(darkTheme = false) {
        MealPlannerCopyScreen("Shepherd's Pie", PreviewDate, MealSlot.Dinner, copyTargetWeeks(PreviewDate, WeekStart.Monday), alreadyPlanned = setOf(PreviewDate.plusDays(2)))
    }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerCopyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) {
        MealPlannerCopyScreen("Shepherd's Pie", PreviewDate, MealSlot.Dinner, copyTargetWeeks(PreviewDate, WeekStart.Monday), alreadyPlanned = setOf(PreviewDate.plusDays(2)))
    }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerCopyTabletLightPreview() {
    VestaTheme(darkTheme = false) {
        MealPlannerCopyScreen("Shepherd's Pie", PreviewDate, MealSlot.Dinner, copyTargetWeeks(PreviewDate, WeekStart.Monday), alreadyPlanned = setOf(PreviewDate.plusDays(2)))
    }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerCopyTabletDarkPreview() {
    VestaTheme(darkTheme = true) {
        MealPlannerCopyScreen("Shepherd's Pie", PreviewDate, MealSlot.Dinner, copyTargetWeeks(PreviewDate, WeekStart.Monday), alreadyPlanned = setOf(PreviewDate.plusDays(2)))
    }
}
