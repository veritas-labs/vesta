package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
data class MealPlan(
    val days: Map<CalendarDate, Map<MealSlot, List<String>>> = emptyMap(),
    /** People who eat with the Home but are not members of it, such as children. */
    val guests: List<MealGuest> = emptyList(),
    /** For the planned recipes whose eaters have been chosen, the [MealPerson.ref]s eating; one not in here has the default eaters. */
    val eaters: Map<PlannedMeal, Set<String>> = emptyMap(),
) {
    /** The recipe keys planned for [slot] on [date], empty for none. */
    fun recipes(date: CalendarDate, slot: MealSlot): List<String> = days[date]?.get(slot).orEmpty()

    /** The refs chosen as eating [recipeKey] in [slot] on [date], or null if nobody has chosen, which means every Home member. */
    fun chosenEaters(date: CalendarDate, slot: MealSlot, recipeKey: String): Set<String>? = eaters[PlannedMeal(date, slot, recipeKey)]
}

/** One recipe planned for one slot of one day: the thing people are tagged on. */
data class PlannedMeal(val date: CalendarDate, val slot: MealSlot, val recipeKey: String)

/** A person who eats with the Home without being one of its members; just a name. */
data class MealGuest(val id: String, val name: String)

/** Someone who can be tagged on a meal: a Home member or a [MealGuest]. [ref] is what the plan stores for them. */
data class MealPerson(val ref: String, val name: String, val isMember: Boolean)

fun memberRef(memberId: String): String = "m:$memberId"

fun guestRef(guestId: String): String = "p:$guestId"

/** Everyone who can be tagged: the Home's [members] in their order, then the plan's guests by name. */
fun mealPeople(members: List<MemberListItem>, guests: List<MealGuest>): List<MealPerson> =
    members.map { MealPerson(memberRef(it.memberId), it.name, isMember = true) } +
        guests.sortedBy { it.name.lowercase() }.map { MealPerson(guestRef(it.id), it.name, isMember = false) }

/**
 * Who is eating [recipeKey] in [slot] on [date]: when nobody has chosen, every member of [people]; otherwise those of [people] that were
 * chosen (a chosen person who has since left the Home or been removed is dropped).
 */
fun eatersOf(plan: MealPlan, date: CalendarDate, slot: MealSlot, recipeKey: String, people: List<MealPerson>): List<MealPerson> {
    val chosen = plan.chosenEaters(date, slot, recipeKey) ?: return people.filter { it.isMember }
    return people.filter { it.ref in chosen }
}

/** Where [MealPlannerViewModel] gets a Home's meal plan from; implemented over the meal plan Automerge document (ADR-028). */
interface MealPlanSource {
    val plan: Flow<MealPlan>

    /** Plans the recipe with [recipeKey] for [slot] on [date]. Planning one that is already there changes nothing. */
    suspend fun add(date: CalendarDate, slot: MealSlot, recipeKey: String)

    /** Takes the recipe with [recipeKey] out of [slot] on [date]. No-op if it isn't there. */
    suspend fun remove(date: CalendarDate, slot: MealSlot, recipeKey: String)

    /** Chooses exactly [refs] ([MealPerson.ref]s) as the eaters of [recipeKey] in [slot] on [date], even none. */
    suspend fun setEaters(date: CalendarDate, slot: MealSlot, recipeKey: String, refs: Set<String>)

    /** Adds a guest called [name] (trimmed; blank adds nothing) and returns its id, or null if nothing was added. */
    suspend fun addGuest(name: String): String?

    /** Renames the guest with [id] to [name] (trimmed; blank changes nothing). */
    suspend fun renameGuest(id: String, name: String)

    /** Removes the guest with [id], which un-tags them from every meal. */
    suspend fun removeGuest(id: String)
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
    /** Everyone who can be tagged on a meal: the Home's members, then the guests. */
    val people: List<MealPerson> = emptyList(),
    /** Weeks from the current one: 0 is this week, -1 last week, 1 next week. */
    val weekOffset: Int = 0,
    val isLoading: Boolean = true,
)

/** Backs the meal planner: exposes the plan from [source], the cookbook's [recipes], and which week is on show. */
class MealPlannerViewModel(
    private val source: MealPlanSource,
    recipes: Flow<List<Recipe>>,
    members: Flow<List<MemberListItem>> = flowOf(emptyList()),
) : ViewModel() {

    private val weekOffset = MutableStateFlow(0)

    val uiState: StateFlow<MealPlannerUiState> = combine(source.plan, recipes, members, weekOffset) { plan, cookbook, homeMembers, offset ->
        MealPlannerUiState(plan = plan, recipes = cookbook, people = mealPeople(homeMembers, plan.guests), weekOffset = offset, isLoading = false)
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

    /**
     * Plans [meal]'s recipe for the same slot on each of [dates], with the same chosen eaters if it has any. A day that already has
     * the recipe in that slot is left as it is.
     */
    fun copyTo(meal: PlannedMeal, dates: Set<CalendarDate>) {
        viewModelScope.launch {
            val plan = source.plan.first()
            val eaters = plan.chosenEaters(meal.date, meal.slot, meal.recipeKey)
            dates.sorted().filter { meal.recipeKey !in plan.recipes(it, meal.slot) }.forEach { date ->
                source.add(date, meal.slot, meal.recipeKey)
                eaters?.let { source.setEaters(date, meal.slot, meal.recipeKey, it) }
            }
        }
    }

    fun setEaters(date: CalendarDate, slot: MealSlot, recipeKey: String, refs: Set<String>) {
        viewModelScope.launch { source.setEaters(date, slot, recipeKey, refs) }
    }

    fun addGuest(name: String) {
        viewModelScope.launch { source.addGuest(name) }
    }

    fun renameGuest(id: String, name: String) {
        viewModelScope.launch { source.renameGuest(id, name) }
    }

    fun removeGuest(id: String) {
        viewModelScope.launch { source.removeGuest(id) }
    }
}
