package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.blobs.BlobReference

private const val StopTimeoutMillis = 5_000L

/** What kind of meal or dish a [Recipe] is (ADR-022). Stored by [name]. */
enum class RecipeType { Breakfast, Lunch, Dinner, Starter, Side, Dessert, Baking, Snack, Drink, Other }

enum class MediaKind { Photo, Video }

/**
 * A photo or video attached to a recipe: a reference, not the bytes. A [blob] is a photo stored on the devices and moved between them (ADR-023),
 * with [uri] empty; an entry with no blob is a link to somewhere else ([uri]), as ADR-022 first had them.
 */
data class RecipeMedia(
    val uri: String,
    val kind: MediaKind = MediaKind.Photo,
    val caption: String = "",
    val blob: BlobReference? = null,
)

/** One line of a recipe's ingredients; [quantity] null means "to taste" or similar and is shown without a number. */
data class RecipeIngredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val note: String = "",
)

/** One step of the method, which is a list of these in the order they are done; [minutes] is optional. */
data class RecipeStep(
    val id: String,
    val text: String,
    val minutes: Int? = null,
)

/** A recipe in the Home's cookbook (ADR-022); a blank [key] is a recipe that hasn't been saved yet. */
data class Recipe(
    val key: String = "",
    val title: String = "",
    val description: String = "",
    val type: RecipeType = RecipeType.Dinner,
    val servings: Int = 4,
    val prepMinutes: Int = 0,
    val cookMinutes: Int = 0,
    val restMinutes: Int = 0,
    /** 0 is unrated, otherwise 1 to 5 stars. */
    val rating: Int = 0,
    val favourite: Boolean = false,
    val media: List<RecipeMedia> = emptyList(),
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val tags: List<String> = emptyList(),
    val source: String = "",
    val notes: String = "",
) {
    /** Prep, cook and rest time together. */
    val totalMinutes: Int get() = prepMinutes + cookMinutes + restMinutes
}

/** Where [CookbookViewModel] gets a Home's recipes from; implemented over the cookbook Automerge document (ADR-022). */
interface CookbookSource {
    val recipes: Flow<List<Recipe>>

    /** Adds [recipe] (blank key) or replaces the recipe at its key; returns the key. */
    suspend fun save(recipe: Recipe): String

    suspend fun remove(key: String)

    /** Sets the stars (0 to 5) of the recipe at [key], touching nothing else. */
    suspend fun setRating(key: String, rating: Int)

    suspend fun setFavourite(key: String, favourite: Boolean)
}

/** How the Pantry stands on an ingredient. */
enum class IngredientAvailability { InPantry, NotEnough, OutOfStock, NotInPantry }

/**
 * Whether the Pantry has [ingredient]: names are compared ignoring case, spacing and plurals (see [ingredientKey]).
 * With stock on hand, [quantity] (the amount the recipe needs at the servings being cooked) is checked against what the
 * matching entries hold: stock count times package size, so 2 x "400g" holds 800 g. Mass, volume and plain counts are
 * compared within their own kind; when the units can't be compared (a "pinch", a package size of "family pack") the
 * ingredient is given the benefit of the doubt and counts as [IngredientAvailability.InPantry].
 */
fun availability(
    ingredient: RecipeIngredient,
    pantry: List<PantryListItem>,
    quantity: Double? = ingredient.quantity,
): IngredientAvailability {
    val wanted = ingredientKey(ingredient.name)
    val matches = pantry.filter { ingredientKey(it.title) == wanted }
    val stocked = matches.filter { it.stockCount > 0 }
    if (matches.isEmpty()) return IngredientAvailability.NotInPantry
    if (stocked.isEmpty()) return IngredientAvailability.OutOfStock
    val need = quantity?.takeIf { it > 0 }?.let { measureOf(it, ingredient.unit) }
    return if (need != null && holdsEnough(need, stocked) == false) IngredientAvailability.NotEnough else IngredientAvailability.InPantry
}

/** [quantity] for [target] servings of a recipe written for [from]; unchanged if either is not positive. */
fun scaleQuantity(quantity: Double, from: Int, target: Int): Double =
    if (from <= 0 || target <= 0) quantity else quantity * target / from

/** [quantity] for display: up to two decimals, no trailing zeros ("1.5", "2", "0.33"). */
fun formatQuantity(quantity: Double): String {
    val hundredths = (quantity * 100).roundToInt()
    val whole = hundredths / 100
    val fraction = hundredths % 100
    return when {
        fraction == 0 -> "$whole"
        fraction % 10 == 0 -> "$whole.${fraction / 10}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}

/** "1 h 5 min", "45 min", or "" for no time. */
fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** The distinct tags used by [recipes] (ignoring case, first spelling wins), sorted for display. */
fun recipeTags(recipes: List<Recipe>): List<String> =
    recipes.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

/** What the user has chosen to narrow the cookbook to; the empty filter shows everything. */
data class CookbookFilter(
    val query: String = "",
    val type: RecipeType? = null,
    val favouritesOnly: Boolean = false,
    /** Lower-cased tags; a recipe with any of them shows. */
    val tags: Set<String> = emptySet(),
) {
    val isActive: Boolean get() = query.isNotBlank() || type != null || favouritesOnly || tags.isNotEmpty()
}

/** The [recipes] matching [filter]; the query matches the title, an ingredient's name or a tag, ignoring case. */
fun filterRecipes(recipes: List<Recipe>, filter: CookbookFilter): List<Recipe> {
    val query = filter.query.trim().lowercase()
    return recipes.filter { recipe ->
        (filter.type == null || recipe.type == filter.type) &&
            (!filter.favouritesOnly || recipe.favourite) &&
            (filter.tags.isEmpty() || recipe.tags.any { it.lowercase() in filter.tags }) &&
            (
                query.isEmpty() ||
                    recipe.title.lowercase().contains(query) ||
                    recipe.ingredients.any { it.name.lowercase().contains(query) } ||
                    recipe.tags.any { it.lowercase().contains(query) }
                )
    }
}

/** [CookbookScreen]'s state; [isLoading] is true until the source has emitted for the first time. */
data class CookbookUiState(
    /** Every recipe in the cookbook, sorted by title. */
    val recipes: List<Recipe> = emptyList(),
    /** The Pantry, for each recipe's ingredient availability. */
    val pantry: List<PantryListItem> = emptyList(),
    val filter: CookbookFilter = CookbookFilter(),
    val isLoading: Boolean = true,
) {
    /** The [recipes] the [filter] lets through. */
    val visible: List<Recipe> get() = filterRecipes(recipes, filter)
}

/** Backs the cookbook screens: exposes the recipes from [source], the Pantry from [pantry], and the user's filter. */
class CookbookViewModel(private val source: CookbookSource, pantry: Flow<List<PantryListItem>>) : ViewModel() {

    private val filter = MutableStateFlow(CookbookFilter())

    val uiState: StateFlow<CookbookUiState> = combine(source.recipes, pantry, filter) { recipes, pantryItems, current ->
        CookbookUiState(
            recipes = recipes.sortedBy { it.title.lowercase() },
            pantry = pantryItems,
            filter = current,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), CookbookUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    /** Filters to [type], or clears the type filter if it is already [type]. */
    fun toggleType(type: RecipeType) = filter.update { it.copy(type = if (it.type == type) null else type) }

    fun toggleFavouritesOnly() = filter.update { it.copy(favouritesOnly = !it.favouritesOnly) }

    fun toggleTag(tag: String) {
        val key = tag.lowercase()
        filter.update { it.copy(tags = if (key in it.tags) it.tags - key else it.tags + key) }
    }

    fun save(recipe: Recipe) {
        viewModelScope.launch { source.save(recipe) }
    }

    fun remove(key: String) {
        viewModelScope.launch { source.remove(key) }
    }

    fun setRating(key: String, rating: Int) {
        viewModelScope.launch { source.setRating(key, rating.coerceIn(0, 5)) }
    }

    fun setFavourite(key: String, favourite: Boolean) {
        viewModelScope.launch { source.setFavourite(key, favourite) }
    }
}
