package uk.veritaslabs.vesta.recipes

import androidx.compose.runtime.staticCompositionLocalOf
import uk.veritaslabs.vesta.ui.screens.Recipe

/** Why a recipe couldn't be imported from a web address. */
enum class RecipeImportFailure {
    /** The address isn't a web link, or the page couldn't be downloaded. */
    Unreachable,

    /** The page downloaded but holds no recipe Vesta can read. */
    NoRecipe,
}

sealed interface RecipeImportResult {
    /** [recipe] is unsaved (blank key) with its [Recipe.source] set to the page; [photoUrl] is the address of the page's picture of the dish, if it has one. */
    class Imported(val recipe: Recipe, val photoUrl: String?) : RecipeImportResult

    class Failed(val reason: RecipeImportFailure) : RecipeImportResult
}

/** Reads the recipe on a web page, so the add-recipe form can be filled from a link. */
fun interface RecipeImporter {
    suspend fun import(url: String): RecipeImportResult
}

/** The [RecipeImporter] of the running app; null where there is none (previews, iOS), in which case the add-recipe form doesn't offer the import. */
val LocalRecipeImporter = staticCompositionLocalOf<RecipeImporter?> { null }
