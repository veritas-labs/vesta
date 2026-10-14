package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.today
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.components.ActionCard
import uk.veritaslabs.vesta.ui.components.ActionCardOrientation
import uk.veritaslabs.vesta.ui.components.BlobImage
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.calendar_months
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_chevron_right
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.ic_cookbook
import vesta.shared.generated.resources.ic_dots_vertical
import vesta.shared.generated.resources.ic_copy
import vesta.shared.generated.resources.ic_meal_breakfast
import vesta.shared.generated.resources.ic_meal_dinner
import vesta.shared.generated.resources.ic_meal_lunch
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.meal_planner_add_recipe
import vesta.shared.generated.resources.meal_planner_day_heading
import vesta.shared.generated.resources.meal_planner_day_month
import vesta.shared.generated.resources.meal_planner_day_month_year
import vesta.shared.generated.resources.meal_planner_eaters_edit
import vesta.shared.generated.resources.meal_planner_eaters_everyone
import vesta.shared.generated.resources.meal_planner_eaters_nobody
import vesta.shared.generated.resources.meal_planner_many_planned
import vesta.shared.generated.resources.meal_planner_more_options
import vesta.shared.generated.resources.meal_planner_view_required_items
import vesta.shared.generated.resources.meal_planner_next_week
import vesta.shared.generated.resources.meal_planner_none_planned
import vesta.shared.generated.resources.meal_planner_one_planned
import vesta.shared.generated.resources.meal_planner_previous_week
import vesta.shared.generated.resources.meal_planner_recipe_removed
import vesta.shared.generated.resources.meal_planner_remove_recipe
import vesta.shared.generated.resources.meal_planner_sheet_copy
import vesta.shared.generated.resources.meal_planner_sheet_copy_body
import vesta.shared.generated.resources.meal_planner_sheet_view
import vesta.shared.generated.resources.meal_planner_sheet_view_body
import vesta.shared.generated.resources.meal_planner_slot_breakfast
import vesta.shared.generated.resources.meal_planner_slot_dinner
import vesta.shared.generated.resources.meal_planner_slot_lunch
import vesta.shared.generated.resources.meal_planner_this_week
import vesta.shared.generated.resources.meal_planner_title
import vesta.shared.generated.resources.meal_planner_today
import vesta.shared.generated.resources.meal_planner_week_range
import vesta.shared.generated.resources.meal_planner_weekdays
import vesta.shared.generated.resources.pantry_back

private val MaxContentWidth = 720.dp
private val ContentPadding = 16.dp
private val ChipSize = 40.dp
private val ListEdgeSpacing = 24.dp
private val PlusSize = 36.dp
private const val WeeklyShift = 7

/**
 * A colour for each day's bar, Monday first, whatever day the week starts on. Fixed rather than from the theme, which has too few
 * accents to tell seven days apart; muted enough to sit on both the light and the dark background.
 */
internal val DayAccents = listOf(
    Color(0xFFD9743A), // Monday: terracotta
    Color(0xFF8FA07A), // Tuesday: sage
    Color(0xFFD9A441), // Wednesday: mustard
    Color(0xFFD08A8A), // Thursday: rose
    Color(0xFFD9694F), // Friday: coral
    Color(0xFFE08B5C), // Saturday: amber
    Color(0xFFA58DB5), // Sunday: lavender
)

/** Stable hooks for UI tests to target, independent of displayed copy or recipe titles. */
internal object MealPlannerTestTags {
    const val BackButton = "meal_planner_back_button"
    const val PreviousWeek = "meal_planner_previous_week"
    const val NextWeek = "meal_planner_next_week"
    const val ThisWeek = "meal_planner_this_week"
    const val WeekRange = "meal_planner_week_range"
    const val Sheet = "meal_planner_sheet"
    const val MoreOptions = "meal_planner_more_options"
    const val ViewRequiredItems = "meal_planner_view_required_items"
    const val SheetView = "meal_planner_sheet_view"
    const val SheetCopy = "meal_planner_sheet_copy"

    private fun dateTag(date: CalendarDate) = "${date.year}-${date.month}-${date.day}"

    fun day(date: CalendarDate) = "meal_planner_day_${dateTag(date)}"

    fun addButton(date: CalendarDate, slot: MealSlot) = "meal_planner_add_${dateTag(date)}_${slot.name}"

    /** The row for [recipeKey] in a slot, whether or not the cookbook still has the recipe. */
    fun entry(date: CalendarDate, slot: MealSlot, recipeKey: String) = "meal_planner_entry_${dateTag(date)}_${slot.name}_$recipeKey"

    /** The line saying who is eating a planned recipe; tapping it edits them. */
    fun eaters(date: CalendarDate, slot: MealSlot, recipeKey: String) = "meal_planner_eaters_${dateTag(date)}_${slot.name}_$recipeKey"

    /** Present only on a recipe that has a photo. */
    fun thumbnail(date: CalendarDate, slot: MealSlot, recipeKey: String) = "meal_planner_thumbnail_${dateTag(date)}_${slot.name}_$recipeKey"

    fun removeButton(date: CalendarDate, slot: MealSlot, recipeKey: String) = "meal_planner_remove_${dateTag(date)}_${slot.name}_$recipeKey"
}

@Composable
internal fun mealSlotLabel(slot: MealSlot): String = stringResource(
    when (slot) {
        MealSlot.Breakfast -> Res.string.meal_planner_slot_breakfast
        MealSlot.Lunch -> Res.string.meal_planner_slot_lunch
        MealSlot.Dinner -> Res.string.meal_planner_slot_dinner
    },
)

/**
 * One week of meals: a card per day of the week containing [today] shifted by [weekOffset] weeks, starting on [weekStart], each with a
 * coloured bar, the day's name and how many recipes it has, and a row for Breakfast, Lunch and Dinner. A row has the meal's icon, the
 * cookbook [recipes] [plan]ned in it as small cards (photo, title and who is eating, from [people]) and a plus that calls
 * [onAddRecipe] (the app opens the recipe picker). The names under a recipe call [onEditEaters], the cross on it [onRemove]. A planned
 * recipe that is no longer in [recipes] shows as removed. The days are a vertical list, each day's meals a vertical list inside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlannerScreen(
    recipes: List<Recipe>,
    modifier: Modifier = Modifier,
    plan: MealPlan = MealPlan(),
    today: CalendarDate = remember { CalendarDate.today() },
    weekStart: WeekStart = WeekStart.Monday,
    weekOffset: Int = 0,
    onBack: () -> Unit = {},
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onThisWeek: () -> Unit = {},
    onAddRecipe: (CalendarDate, MealSlot) -> Unit = { _, _ -> },
    people: List<MealPerson> = emptyList(),
    onEditEaters: (CalendarDate, MealSlot, recipeKey: String) -> Unit = { _, _, _ -> },
    onRemove: (CalendarDate, MealSlot, String) -> Unit = { _, _, _ -> },
    onViewRecipe: (recipeKey: String) -> Unit = {},
    onCopyRecipe: (CalendarDate, MealSlot, recipeKey: String) -> Unit = { _, _, _ -> },
    onViewRequiredItems: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<PlannedMeal?>(null) }
    val days = weekDays(today.plusDays(weekOffset * WeeklyShift), weekStart)
    val recipesByKey = remember(recipes) { recipes.associateBy { it.key } }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.meal_planner_title)) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(MealPlannerTestTags.BackButton)) {
                    Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.pantry_back))
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(MealPlannerTestTags.MoreOptions)) {
                        Icon(painterResource(Res.drawable.ic_dots_vertical), contentDescription = stringResource(Res.string.meal_planner_more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.meal_planner_view_required_items)) },
                            onClick = {
                                menuOpen = false
                                onViewRequiredItems()
                            },
                            modifier = Modifier.testTag(MealPlannerTestTags.ViewRequiredItems),
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
        // Only what is under the app bar is held to a readable width, so the bar spans the window like every other screen's.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.fillMaxHeight().widthIn(max = MaxContentWidth).fillMaxWidth()) {
                WeekHeader(
                    days = days,
                    showThisWeek = weekOffset != 0,
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    onThisWeek = onThisWeek,
                    modifier = Modifier.padding(horizontal = ContentPadding),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = ContentPadding, end = ContentPadding, top = ListEdgeSpacing, bottom = ListEdgeSpacing),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(days, key = { it.epochDay }) { date ->
                        DayCard(
                            date = date,
                            isToday = date == today,
                            plan = plan,
                            recipes = recipesByKey,
                            people = people,
                            onEditEaters = { slot, key -> onEditEaters(date, slot, key) },
                            onAdd = { slot -> onAddRecipe(date, slot) },
                            onRemove = onRemove,
                            onOpen = { slot, key -> sheet = PlannedMeal(date, slot, key) },
                        )
                    }
                }
            }
        }
    }

    // Looked up in the cookbook so the sheet closes if the recipe goes away (a removed recipe has nothing to view).
    sheet?.let { meal -> recipesByKey[meal.recipeKey]?.let { it to meal } }?.let { (recipe, meal) ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState()) {
            PlannedRecipeSheetContent(
                title = recipe.title,
                onView = {
                    sheet = null
                    onViewRecipe(meal.recipeKey)
                },
                onCopy = {
                    sheet = null
                    onCopyRecipe(meal.date, meal.slot, meal.recipeKey)
                },
            )
        }
    }
}

/** What can be done with a recipe planned in a meal: look at it, or copy it to other days; two action cards, one above the other. */
@Composable
private fun PlannedRecipeSheetContent(title: String, onView: () -> Unit, onCopy: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).testTag(MealPlannerTestTags.Sheet),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
        ActionCard(
            title = stringResource(Res.string.meal_planner_sheet_view),
            body = stringResource(Res.string.meal_planner_sheet_view_body),
            icon = { Icon(painterResource(Res.drawable.ic_cookbook), contentDescription = null) },
            orientation = ActionCardOrientation.Horizontal,
            onClick = onView,
            modifier = Modifier.testTag(MealPlannerTestTags.SheetView),
        )
        ActionCard(
            title = stringResource(Res.string.meal_planner_sheet_copy),
            body = stringResource(Res.string.meal_planner_sheet_copy_body),
            icon = { Icon(painterResource(Res.drawable.ic_copy), contentDescription = null) },
            orientation = ActionCardOrientation.Horizontal,
            onClick = onCopy,
            modifier = Modifier.testTag(MealPlannerTestTags.SheetCopy),
        )
    }
}

/** Who is eating [recipeKey] in [slot] on [date] in words: "Everyone" for all the members and nobody else, "Nobody", or their names. */
@Composable
internal fun eaterSummary(plan: MealPlan, date: CalendarDate, slot: MealSlot, recipeKey: String, people: List<MealPerson>): String {
    val eaters = eatersOf(plan, date, slot, recipeKey, people)
    val members = people.filter { it.isMember }
    return when {
        eaters.isEmpty() && plan.chosenEaters(date, slot, recipeKey) != null -> stringResource(Res.string.meal_planner_eaters_nobody)
        plan.chosenEaters(date, slot, recipeKey) == null || eaters == members -> stringResource(Res.string.meal_planner_eaters_everyone)
        else -> eaters.joinToString(", ") { it.name }
    }
}

/** A week's dates in words, like "12 Oct – 18 Oct 2026". */
@Composable
internal fun weekRangeText(days: List<CalendarDate>): String {
    val months = stringArrayResource(Res.array.calendar_months)
    val first = days.first()
    val last = days.last()
    return stringResource(
        Res.string.meal_planner_week_range,
        stringResource(Res.string.meal_planner_day_month, first.day, months[first.month - 1].take(3)),
        stringResource(Res.string.meal_planner_day_month_year, last.day, months[last.month - 1].take(3), last.year),
    )
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
    val range = weekRangeText(days)
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        }
        if (showThisWeek) {
            TextButton(onClick = onThisWeek, modifier = Modifier.testTag(MealPlannerTestTags.ThisWeek)) {
                Text(stringResource(Res.string.meal_planner_this_week))
            }
        }
    }
}

/** A circle with a thin outline that holds one icon and is tapped, like the week arrows and the plus on a meal. */
@Composable
internal fun RoundButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = PlusSize, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceBright,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun DayCard(
    date: CalendarDate,
    isToday: Boolean,
    plan: MealPlan,
    recipes: Map<String, Recipe>,
    people: List<MealPerson>,
    onEditEaters: (MealSlot, recipeKey: String) -> Unit,
    onAdd: (MealSlot) -> Unit,
    onRemove: (CalendarDate, MealSlot, String) -> Unit,
    onOpen: (MealSlot, recipeKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = stringArrayResource(Res.array.calendar_months)
    val weekday = stringArrayResource(Res.array.meal_planner_weekdays)[date.dayOfWeek]
    val heading = stringResource(Res.string.meal_planner_day_heading, weekday.take(3), date.day)
    val accent = DayAccents[date.dayOfWeek]
    val planned = MealSlot.entries.sumOf { plan.recipes(date, it).size }
    val summary = when (planned) {
        0 -> stringResource(Res.string.meal_planner_none_planned)
        1 -> stringResource(Res.string.meal_planner_one_planned)
        else -> stringResource(Res.string.meal_planner_many_planned, planned)
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth().testTag(MealPlannerTestTags.day(date)),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(6.dp).background(accent))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = weekday,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = buildString {
                            if (isToday) append(stringResource(Res.string.meal_planner_today)).append(" · ")
                            append(stringResource(Res.string.meal_planner_day_month, date.day, months[date.month - 1].take(3)))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MealSlot.entries.forEach { slot ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
                    SlotRow(
                        date = date,
                        slot = slot,
                        dayHeading = heading,
                        recipeKeys = plan.recipes(date, slot),
                        recipes = recipes,
                        eaterSummary = { key -> eaterSummary(plan, date, slot, key, people) },
                        accent = accent,
                        onEditEaters = { onEditEaters(slot, it) },
                        onAdd = { onAdd(slot) },
                        onRemove = { onRemove(date, slot, it) },
                        onOpen = { onOpen(slot, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun mealSlotIcon(slot: MealSlot) = painterResource(
    when (slot) {
        MealSlot.Breakfast -> Res.drawable.ic_meal_breakfast
        MealSlot.Lunch -> Res.drawable.ic_meal_lunch
        MealSlot.Dinner -> Res.drawable.ic_meal_dinner
    },
)

/** One meal of a day: its icon, then its label (while empty) or its recipes as cards, and the plus that adds one. */
@Composable
private fun SlotRow(
    date: CalendarDate,
    slot: MealSlot,
    dayHeading: String,
    recipeKeys: List<String>,
    recipes: Map<String, Recipe>,
    eaterSummary: @Composable (recipeKey: String) -> String,
    accent: Color,
    onEditEaters: (recipeKey: String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val slotLabel = mealSlotLabel(slot)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ChipSize).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
        ) {
            Icon(mealSlotIcon(slot), contentDescription = slotLabel, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (recipeKeys.isEmpty()) {
                Text(slotLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            recipeKeys.forEach { key ->
                PlannedRecipeCard(
                    date = date,
                    slot = slot,
                    recipeKey = key,
                    recipe = recipes[key],
                    eaters = eaterSummary(key),
                    onEditEaters = { onEditEaters(key) },
                    onRemove = { onRemove(key) },
                    onOpen = { onOpen(key) },
                )
            }
        }
        RoundButton(onClick = onAdd, modifier = Modifier.testTag(MealPlannerTestTags.addButton(date, slot))) {
            Icon(
                painterResource(Res.drawable.ic_plus),
                contentDescription = stringResource(Res.string.meal_planner_add_recipe, slotLabel, dayHeading),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A recipe in a meal: its photo (or a plate of the cookbook's icon), title, who is eating, and a cross to take it out. */
@Composable
private fun PlannedRecipeCard(
    date: CalendarDate,
    slot: MealSlot,
    recipeKey: String,
    recipe: Recipe?,
    eaters: String,
    onEditEaters: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val title = recipe?.title
    val removedLabel = stringResource(Res.string.meal_planner_recipe_removed)
    val cardModifier = Modifier.fillMaxWidth().testTag(MealPlannerTestTags.entry(date, slot, recipeKey))
    val cardShape = RoundedCornerShape(10.dp)
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp)) {
            val photo = recipe?.media?.firstNotNullOfOrNull { if (it.kind == MediaKind.Photo) it.blob else null }
            val thumbnail = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            if (photo != null) {
                BlobImage(photo, thumbnail.testTag(MealPlannerTestTags.thumbnail(date, slot, recipeKey)))
            } else {
                Box(contentAlignment = Alignment.Center, modifier = thumbnail.background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Icon(
                        painterResource(Res.drawable.ic_cookbook),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title ?: removedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (title == null) FontStyle.Italic else FontStyle.Normal,
                    color = if (title == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (title != null) {
                    Text(
                        text = eaters,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clickable(onClickLabel = stringResource(Res.string.meal_planner_eaters_edit, title), onClick = onEditEaters)
                            .padding(vertical = 4.dp)
                            .testTag(MealPlannerTestTags.eaters(date, slot, recipeKey)),
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp).testTag(MealPlannerTestTags.removeButton(date, slot, recipeKey))) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.meal_planner_remove_recipe, title ?: removedLabel),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    // A recipe still in the cookbook opens the sheet when tapped; a removed one has nothing to offer.
    if (recipe != null) {
        Surface(onClick = onOpen, shape = cardShape, color = cardColor, shadowElevation = 1.dp, modifier = cardModifier) { content() }
    } else {
        Surface(shape = cardShape, color = cardColor, shadowElevation = 1.dp, modifier = cardModifier) { content() }
    }
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
