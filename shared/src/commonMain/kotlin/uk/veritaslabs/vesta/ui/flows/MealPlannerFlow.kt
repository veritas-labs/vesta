package uk.veritaslabs.vesta.ui.flows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.LocalCalendarSettings
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.screens.MealPlannerPickerScreen
import uk.veritaslabs.vesta.ui.screens.MealPlannerScreen
import uk.veritaslabs.vesta.ui.screens.MealPlannerViewModel
import uk.veritaslabs.vesta.ui.screens.MealSlot

/**
 * The meal planner's two screens (ADR-028) and the move between them: the week, and the recipe picker that its plus buttons open
 * for one meal of one day. Picking a recipe plans it and returns to the week; back returns without planning anything. [onBack]
 * leaves the planner from the week. Blank until the plan and the cookbook have loaded.
 */
@Composable
fun MealPlannerFlow(
    viewModel: MealPlannerViewModel,
    modifier: Modifier = Modifier,
    today: CalendarDate = remember { CalendarDate.todayUtc() },
    weekStart: WeekStart = LocalCalendarSettings.current.settings.weekStart,
    onBack: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    var picking by remember { mutableStateOf<Pair<CalendarDate, MealSlot>?>(null) }
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val target = picking
    if (target == null) {
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
            onAddRecipe = { date, slot -> picking = date to slot },
            onRemove = viewModel::remove,
        )
    } else {
        val (date, slot) = target
        MealPlannerPickerScreen(
            recipes = state.recipes,
            date = date,
            slot = slot,
            modifier = modifier,
            planned = state.plan.recipes(date, slot),
            onPick = {
                viewModel.add(date, slot, it.key)
                picking = null
            },
            onBack = { picking = null },
        )
    }
}
