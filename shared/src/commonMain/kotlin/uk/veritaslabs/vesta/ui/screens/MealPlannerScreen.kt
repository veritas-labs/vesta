package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.LocalCalendarSettings
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.calendar_months
import vesta.shared.generated.resources.calendar_weekdays
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_chevron_right
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.meal_planner_add_recipe
import vesta.shared.generated.resources.meal_planner_day_heading
import vesta.shared.generated.resources.meal_planner_day_month
import vesta.shared.generated.resources.meal_planner_day_month_year
import vesta.shared.generated.resources.meal_planner_next_week
import vesta.shared.generated.resources.meal_planner_pick_cancel
import vesta.shared.generated.resources.meal_planner_pick_empty_cookbook
import vesta.shared.generated.resources.meal_planner_pick_no_matches
import vesta.shared.generated.resources.meal_planner_pick_search
import vesta.shared.generated.resources.meal_planner_pick_title
import vesta.shared.generated.resources.meal_planner_previous_week
import vesta.shared.generated.resources.meal_planner_recipe_removed
import vesta.shared.generated.resources.meal_planner_remove_recipe
import vesta.shared.generated.resources.meal_planner_slot_breakfast
import vesta.shared.generated.resources.meal_planner_slot_dinner
import vesta.shared.generated.resources.meal_planner_slot_lunch
import vesta.shared.generated.resources.meal_planner_this_week
import vesta.shared.generated.resources.meal_planner_title
import vesta.shared.generated.resources.meal_planner_week_range
import vesta.shared.generated.resources.pantry_back

private val MealPlannerTabletBreakpoint = 600.dp
private const val WeeklyShift = 7

/** Stable hooks for UI tests to target, independent of displayed copy or recipe titles. */
internal object MealPlannerTestTags {
    const val BackButton = "meal_planner_back_button"
    const val PreviousWeek = "meal_planner_previous_week"
    const val NextWeek = "meal_planner_next_week"
    const val ThisWeek = "meal_planner_this_week"
    const val WeekRange = "meal_planner_week_range"
    const val PickerDialog = "meal_planner_picker"
    const val PickerSearch = "meal_planner_picker_search"
    const val PickerCancel = "meal_planner_picker_cancel"
    const val PickerEmpty = "meal_planner_picker_empty"

    private fun dateTag(date: CalendarDate) = "${date.year}-${date.month}-${date.day}"

    fun day(date: CalendarDate) = "meal_planner_day_${dateTag(date)}"

    fun addButton(date: CalendarDate, slot: MealSlot) = "meal_planner_add_${dateTag(date)}_${slot.name}"

    /** The row for [recipeKey] in a slot, whether or not the cookbook still has the recipe. */
    fun entry(date: CalendarDate, slot: MealSlot, recipeKey: String) = "meal_planner_entry_${dateTag(date)}_${slot.name}_$recipeKey"

    fun removeButton(date: CalendarDate, slot: MealSlot, recipeKey: String) = "meal_planner_remove_${dateTag(date)}_${slot.name}_$recipeKey"

    fun pickerRecipe(key: String) = "meal_planner_picker_recipe_$key"
}

@Composable
internal fun mealSlotLabel(slot: MealSlot): String = stringResource(
    when (slot) {
        MealSlot.Breakfast -> Res.string.meal_planner_slot_breakfast
        MealSlot.Lunch -> Res.string.meal_planner_slot_lunch
        MealSlot.Dinner -> Res.string.meal_planner_slot_dinner
    },
)

/** The meal planner (ADR-028) wired to [viewModel]; blank until the plan and the cookbook have loaded. */
@Composable
fun MealPlannerScreen(
    viewModel: MealPlannerViewModel,
    modifier: Modifier = Modifier,
    today: CalendarDate = remember { CalendarDate.todayUtc() },
    weekStart: WeekStart = LocalCalendarSettings.current.settings.weekStart,
    onBack: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        MealPlannerScreen(
            recipes = state.recipes,
            plan = state.plan,
            modifier = modifier,
            today = today,
            weekStart = weekStart,
            weekOffset = state.weekOffset,
            onBack = onBack,
            onPreviousWeek = viewModel::previousWeek,
            onNextWeek = viewModel::nextWeek,
            onThisWeek = viewModel::thisWeek,
            onAdd = viewModel::add,
            onRemove = viewModel::remove,
        )
    }
}

/**
 * One week of meals: a card per day of the week containing [today] shifted by [weekOffset] weeks, starting on [weekStart],
 * each with Breakfast, Lunch and Dinner and the cookbook [recipes] [plan]ned in them. The days are a vertical list, and each day's meals a vertical list inside it. The plus on a slot opens a list of the recipes not yet in it and calls [onAdd] with the one chosen; the cross
 * on a planned recipe calls [onRemove]. A planned recipe that is no longer in [recipes] shows as removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlannerScreen(
    recipes: List<Recipe>,
    modifier: Modifier = Modifier,
    plan: MealPlan = MealPlan(),
    today: CalendarDate = remember { CalendarDate.todayUtc() },
    weekStart: WeekStart = WeekStart.Monday,
    weekOffset: Int = 0,
    onBack: () -> Unit = {},
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onThisWeek: () -> Unit = {},
    onAdd: (CalendarDate, MealSlot, String) -> Unit = { _, _, _ -> },
    onRemove: (CalendarDate, MealSlot, String) -> Unit = { _, _, _ -> },
) {
    val days = weekDays(today.plusDays(weekOffset * WeeklyShift), weekStart)
    var picking by remember { mutableStateOf<Pair<CalendarDate, MealSlot>?>(null) }
    val titles = remember(recipes) { recipes.associate { it.key to it.title } }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val tablet = maxWidth >= MealPlannerTabletBreakpoint
        val contentPadding = if (tablet) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.meal_planner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(MealPlannerTestTags.BackButton)) {
                        Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.pantry_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            WeekHeader(
                days = days,
                showThisWeek = weekOffset != 0,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onThisWeek = onThisWeek,
                modifier = Modifier.padding(horizontal = contentPadding),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = contentPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(days, key = { it.epochDay }) { date ->
                    DayCard(
                        date = date,
                        isToday = date == today,
                        plan = plan,
                        titles = titles,
                        onAdd = { slot -> picking = date to slot },
                        onRemove = onRemove,
                    )
                }
            }
        }
    }

    picking?.let { (date, slot) ->
        RecipePickerDialog(
            recipes = recipes,
            planned = plan.recipes(date, slot),
            onPick = { recipe ->
                onAdd(date, slot, recipe.key)
                picking = null
            },
            onDismiss = { picking = null },
        )
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
    val months = stringArrayResource(Res.array.calendar_months)
    val first = days.first()
    val last = days.last()
    val range = stringResource(
        Res.string.meal_planner_week_range,
        stringResource(Res.string.meal_planner_day_month, first.day, months[first.month - 1].take(3)),
        stringResource(Res.string.meal_planner_day_month_year, last.day, months[last.month - 1].take(3), last.year),
    )
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPreviousWeek, modifier = Modifier.testTag(MealPlannerTestTags.PreviousWeek)) {
            // The chevron points right; turn it to point back.
            Icon(
                painterResource(Res.drawable.ic_chevron_right),
                contentDescription = stringResource(Res.string.meal_planner_previous_week),
                modifier = Modifier.rotate(180f),
            )
        }
        Text(
            text = range,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).testTag(MealPlannerTestTags.WeekRange),
        )
        IconButton(onClick = onNextWeek, modifier = Modifier.testTag(MealPlannerTestTags.NextWeek)) {
            Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = stringResource(Res.string.meal_planner_next_week))
        }
        if (showThisWeek) {
            TextButton(onClick = onThisWeek, modifier = Modifier.testTag(MealPlannerTestTags.ThisWeek)) {
                Text(stringResource(Res.string.meal_planner_this_week))
            }
        }
    }
}

@Composable
private fun DayCard(
    date: CalendarDate,
    isToday: Boolean,
    plan: MealPlan,
    titles: Map<String, String>,
    onAdd: (MealSlot) -> Unit,
    onRemove: (CalendarDate, MealSlot, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekday = stringArrayResource(Res.array.calendar_weekdays)[date.dayOfWeek]
    val heading = stringResource(Res.string.meal_planner_day_heading, weekday, date.day)
    OutlinedCard(modifier = modifier.fillMaxWidth().testTag(MealPlannerTestTags.day(date))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            MealSlot.entries.forEach { slot ->
                SlotSection(date, slot, heading, plan.recipes(date, slot), titles, onAdd = { onAdd(slot) }, onRemove = { onRemove(date, slot, it) })
            }
        }
    }
}

@Composable
private fun SlotSection(
    date: CalendarDate,
    slot: MealSlot,
    dayHeading: String,
    recipeKeys: List<String>,
    titles: Map<String, String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val slotLabel = mealSlotLabel(slot)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = slotLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd, modifier = Modifier.size(36.dp).testTag(MealPlannerTestTags.addButton(date, slot))) {
                Icon(
                    painterResource(Res.drawable.ic_plus),
                    contentDescription = stringResource(Res.string.meal_planner_add_recipe, slotLabel, dayHeading),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        recipeKeys.forEach { key ->
            val title = titles[key]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().testTag(MealPlannerTestTags.entry(date, slot, key)),
            ) {
                Text(
                    text = title ?: stringResource(Res.string.meal_planner_recipe_removed),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (title == null) FontStyle.Italic else FontStyle.Normal,
                    color = if (title == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(key) }, modifier = Modifier.size(32.dp).testTag(MealPlannerTestTags.removeButton(date, slot, key))) {
                    Icon(
                        painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.meal_planner_remove_recipe, title ?: stringResource(Res.string.meal_planner_recipe_removed)),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** A dialog listing the [recipes] not in [planned], narrowed by a search field; a tap on one calls [onPick]. */
@Composable
private fun RecipePickerDialog(recipes: List<Recipe>, planned: List<String>, onPick: (Recipe) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val pickable = pickableRecipes(recipes, planned, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(MealPlannerTestTags.PickerDialog),
        title = { Text(stringResource(Res.string.meal_planner_pick_title)) },
        text = {
            Column {
                if (recipes.isEmpty()) {
                    Text(
                        stringResource(Res.string.meal_planner_pick_empty_cookbook),
                        modifier = Modifier.testTag(MealPlannerTestTags.PickerEmpty),
                    )
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(Res.string.meal_planner_pick_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(MealPlannerTestTags.PickerSearch),
                    )
                    if (pickable.isEmpty()) {
                        Text(
                            stringResource(Res.string.meal_planner_pick_no_matches),
                            modifier = Modifier.padding(top = 12.dp).testTag(MealPlannerTestTags.PickerEmpty),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                            items(pickable, key = { it.key }) { recipe ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(recipe) }
                                        .padding(vertical = 10.dp)
                                        .testTag(MealPlannerTestTags.pickerRecipe(recipe.key)),
                                ) {
                                    Text(recipe.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        recipeTypeLabel(recipe.type),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(MealPlannerTestTags.PickerCancel)) {
                Text(stringResource(Res.string.meal_planner_pick_cancel))
            }
        },
    )
}

private val PreviewToday = CalendarDate(2026, 10, 14)

private val PreviewPlan = MealPlan(
    mapOf(
        PreviewToday to mapOf(
            MealSlot.Breakfast to listOf("porridge"),
            MealSlot.Dinner to listOf("pie", "crumble"),
        ),
        PreviewToday.plusDays(1) to mapOf(MealSlot.Lunch to listOf("gone")),
    ),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MealPlannerScreen(CookbookPreviewRecipes, plan = PreviewPlan, today = PreviewToday) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MealPlannerScreen(CookbookPreviewRecipes, plan = PreviewPlan, today = PreviewToday) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { MealPlannerScreen(CookbookPreviewRecipes, plan = PreviewPlan, today = PreviewToday) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MealPlannerScreen(CookbookPreviewRecipes, plan = PreviewPlan, today = PreviewToday) }
}
