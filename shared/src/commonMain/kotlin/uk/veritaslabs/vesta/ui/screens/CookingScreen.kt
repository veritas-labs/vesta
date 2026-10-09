package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_clipboard_text
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.recipe_cook_close
import vesta.shared.generated.resources.recipe_cook_done
import vesta.shared.generated.resources.recipe_cook_ingredients
import vesta.shared.generated.resources.recipe_cook_next
import vesta.shared.generated.resources.recipe_cook_previous
import vesta.shared.generated.resources.recipe_cook_progress
import vesta.shared.generated.resources.recipe_ingredients
import vesta.shared.generated.resources.recipe_step_minutes

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object CookingTestTags {
    const val Screen = "cooking_screen"
    const val Close = "cooking_close"
    const val Ingredients = "cooking_ingredients_button"
    const val IngredientsDialog = "cooking_ingredients_dialog"
    const val Progress = "cooking_progress"
    const val StepText = "cooking_step_text"
    const val StepMinutes = "cooking_step_minutes"
    const val Previous = "cooking_previous"
    const val Next = "cooking_next"
}

/**
 * Full-screen cooking slideshow for [recipe]: one method step at a time in large type, with large Previous / Next buttons at the
 * bottom (Next becomes Done on the last step, which calls [onClose]). The ingredients button in the top right opens the ingredient
 * list, scaled to [servings], from any step.
 */
@Composable
fun CookingScreen(recipe: Recipe, servings: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var index by remember(recipe.key) { mutableIntStateOf(0) }
    var showingIngredients by remember { mutableStateOf(false) }
    val steps = recipe.steps
    val last = index >= steps.lastIndex
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag(CookingTestTags.Screen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag(CookingTestTags.Close)) {
                Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.recipe_cook_close))
            }
            Text(
                stringResource(Res.string.recipe_cook_progress, index + 1, steps.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag(CookingTestTags.Progress),
            )
            IconButton(onClick = { showingIngredients = true }, modifier = Modifier.testTag(CookingTestTags.Ingredients)) {
                Icon(painterResource(Res.drawable.ic_clipboard_text), contentDescription = stringResource(Res.string.recipe_cook_ingredients))
            }
        }
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            steps.getOrNull(index)?.let { step ->
                Text(
                    step.text,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 46.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag(CookingTestTags.StepText),
                )
                step.minutes?.let {
                    Text(
                        stringResource(Res.string.recipe_step_minutes, it),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 24.dp).testTag(CookingTestTags.StepMinutes),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            OutlinedButton(
                onClick = { index = (index - 1).coerceAtLeast(0) },
                enabled = index > 0,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f).height(72.dp).testTag(CookingTestTags.Previous),
            ) {
                Text(stringResource(Res.string.recipe_cook_previous), style = MaterialTheme.typography.titleLarge)
            }
            Button(
                onClick = { if (last) onClose() else index++ },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f).height(72.dp).testTag(CookingTestTags.Next),
            ) {
                Text(stringResource(if (last) Res.string.recipe_cook_done else Res.string.recipe_cook_next), style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    if (showingIngredients) {
        AlertDialog(
            onDismissRequest = { showingIngredients = false },
            title = { Text(stringResource(Res.string.recipe_ingredients)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).testTag(CookingTestTags.IngredientsDialog)) {
                    recipe.ingredients.forEach { ingredient ->
                        Text(
                            ingredientLine(ingredient, recipe.servings, servings),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showingIngredients = false }) { Text(stringResource(Res.string.recipe_cook_done)) } },
        )
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun CookingScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { CookingScreen(RecipePreview, servings = 4, onClose = {}) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun CookingScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { CookingScreen(RecipePreview, servings = 4, onClose = {}) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CookingScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { CookingScreen(RecipePreview, servings = 4, onClose = {}) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CookingScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { CookingScreen(RecipePreview, servings = 4, onClose = {}) }
}
