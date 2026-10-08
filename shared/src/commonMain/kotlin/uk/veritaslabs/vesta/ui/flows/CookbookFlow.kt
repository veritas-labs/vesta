package uk.veritaslabs.vesta.ui.flows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import uk.veritaslabs.vesta.ui.screens.CookbookScreen
import uk.veritaslabs.vesta.ui.screens.CookbookViewModel
import uk.veritaslabs.vesta.ui.screens.RecipeDetailScreen
import uk.veritaslabs.vesta.ui.screens.RecipeEditScreen

private enum class CookbookStep { List, Detail, Edit }

/**
 * The cookbook's three screens (ADR-022) and the moves between them: the recipe list, one recipe, and the form that
 * adds or edits one. [onBack] leaves the cookbook from the list. A recipe that disappears while it is open (deleted
 * here or on another device) returns to the list.
 */
@Composable
fun CookbookFlow(viewModel: CookbookViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // Not lifecycle-aware: the list screen collects the same state with the lifecycle, and two observers added at once race.
    val state by viewModel.uiState.collectAsState()
    var step by remember { mutableStateOf(CookbookStep.List) }
    // The recipe open on the detail screen, and the one being edited (null while adding); both by key.
    var viewingKey by remember { mutableStateOf<String?>(null) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    val viewing = state.recipes.firstOrNull { it.key == viewingKey }
    when (step) {
        CookbookStep.List -> CookbookScreen(
            viewModel,
            modifier,
            onRecipeSelected = {
                viewingKey = it.key
                step = CookbookStep.Detail
            },
            onBack = onBack,
            onAddRecipe = {
                editingKey = null
                step = CookbookStep.Edit
            },
        )
        CookbookStep.Detail -> viewing?.let { recipe ->
            RecipeDetailScreen(
                recipe,
                modifier,
                pantry = state.pantry,
                onBack = { step = CookbookStep.List },
                onEdit = {
                    editingKey = recipe.key
                    step = CookbookStep.Edit
                },
                onDelete = {
                    viewModel.remove(recipe.key)
                    step = CookbookStep.List
                },
                onRate = { viewModel.setRating(recipe.key, it) },
                onFavourite = { viewModel.setFavourite(recipe.key, it) },
            )
        } ?: LaunchedEffect(Unit) { step = CookbookStep.List }
        CookbookStep.Edit -> {
            val editing = state.recipes.firstOrNull { it.key == editingKey }
            val backTo = if (editing != null) CookbookStep.Detail else CookbookStep.List
            RecipeEditScreen(
                recipe = editing,
                modifier = modifier,
                onSave = {
                    viewModel.save(it)
                    step = backTo
                },
                onCancel = { step = backTo },
            )
        }
    }
}
