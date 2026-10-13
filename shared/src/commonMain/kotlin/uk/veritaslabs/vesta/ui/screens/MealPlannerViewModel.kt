package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.WeekStart

private const val StopTimeoutMillis = 5_000L
private const val DaysPerWeek = 7

/** The meals of a day that can be planned (ADR-028). Stored by [name]. */
enum class MealSlot { Breakfast, Lunch, Dinner }

/**
 * What the Home plans to eat: for each day, the keys of the cookbook recipes in each [MealSlot], in the order they were
 * added. A key may name a recipe that has since left the cookbook; the planner shows it as removed.
 */
data class MealPlan(val days: Map<CalendarDate, Map<MealSlot, List<String>>> = emptyMap()) {
    /** The recipe keys planned for [slot] on [date], empty for none. */
    fun recipes(date: CalendarDate, slot: MealSlot): List<String> = days[date]?.get(slot).orEmpty()
}

/** Where [MealPlannerViewModel] gets a Home's meal plan from; implemented over the meal plan Automerge document (ADR-028). */
interface MealPlanSource {
    val plan: Flow<MealPlan>

    /** Plans the recipe with [recipeKey] for [slot] on [date]. Planning one that is already there changes nothing. */
    suspend fun add(date: CalendarDate, slot: MealSlot, recipeKey: String)

    /** Takes the recipe with [recipeKey] out of [slot] on [date]. No-op if it isn't there. */
    suspend fun remove(date: CalendarDate, slot: MealSlot, recipeKey: String)
}

/** The seven days of the week containing [anchor], starting on [weekStart]. */
fun weekDays(anchor: CalendarDate, weekStart: WeekStart): List<CalendarDate> {
    val first = anchor.plusDays(-((anchor.dayOfWeek - weekStart.dayOfWeek).mod(DaysPerWeek)))
    return List(DaysPerWeek) { first.plusDays(it) }
}

/**
 * The cookbook recipes that can still be planned into a slot already holding [planned] (recipe keys): all of them but
 * those, narrowed by [query] against the title or a tag, ignoring case. Sorted by title.
 */
fun pickableRecipes(recipes: List<Recipe>, planned: List<String>, query: String): List<Recipe> {
    val needle = query.trim().lowercase()
    return recipes
        .filter { it.key !in planned }
        .filter { needle.isEmpty() || it.title.lowercase().contains(needle) || it.tags.any { tag -> tag.lowercase().contains(needle) } }
        .sortedBy { it.title.lowercase() }
}

/** [MealPlannerScreen]'s state; [isLoading] is true until the plan and the cookbook have each emitted once. */
data class MealPlannerUiState(
    val plan: MealPlan = MealPlan(),
    /** Every recipe in the cookbook: the only things that can be planned. */
    val recipes: List<Recipe> = emptyList(),
    /** Weeks from the current one: 0 is this week, -1 last week, 1 next week. */
    val weekOffset: Int = 0,
    val isLoading: Boolean = true,
)

/** Backs the meal planner: exposes the plan from [source], the cookbook's [recipes], and which week is on show. */
class MealPlannerViewModel(private val source: MealPlanSource, recipes: Flow<List<Recipe>>) : ViewModel() {

    private val weekOffset = MutableStateFlow(0)

    val uiState: StateFlow<MealPlannerUiState> = combine(source.plan, recipes, weekOffset) { plan, cookbook, offset ->
        MealPlannerUiState(plan = plan, recipes = cookbook, weekOffset = offset, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), MealPlannerUiState())

    fun previousWeek() = weekOffset.update { it - 1 }

    fun nextWeek() = weekOffset.update { it + 1 }

    fun thisWeek() {
        weekOffset.value = 0
    }

    fun add(date: CalendarDate, slot: MealSlot, recipeKey: String) {
        viewModelScope.launch { source.add(date, slot, recipeKey) }
    }

    fun remove(date: CalendarDate, slot: MealSlot, recipeKey: String) {
        viewModelScope.launch { source.remove(date, slot, recipeKey) }
    }
}
