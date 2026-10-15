package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.blobs.MediaKind
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.components.BlobImage
import uk.veritaslabs.vesta.ui.components.EmptyState
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.calendar_weekdays
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.meal_planner_day_heading
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.meal_planner_pick_empty_cookbook
import vesta.shared.generated.resources.meal_planner_pick_for
import vesta.shared.generated.resources.meal_planner_pick_no_matches
import vesta.shared.generated.resources.meal_planner_pick_search
import vesta.shared.generated.resources.meal_planner_pick_title

/** Stable hooks for UI tests to target, independent of displayed copy or recipe titles. */
internal object MealPlannerPickerTestTags {
    const val Screen = "meal_planner_picker_screen"
    const val BackButton = "meal_planner_picker_back"
    const val Search = "meal_planner_picker_search"
    const val Empty = "meal_planner_picker_empty"

    fun recipe(key: String) = "meal_planner_picker_recipe_$key"

    /** Present only on a recipe that has a photo. */
    fun thumbnail(key: String) = "meal_planner_picker_thumbnail_$key"
}

/**
 * Where a recipe is chosen for [slot] on [date] (ADR-028): a screen of its own, so it can grow suggestions and the like. Lists
 * the cookbook [recipes] that are not already in the slot ([planned], recipe keys), each with its photo, title and type, narrowed
 * by a search over title and tag. A tap on one calls [onPick]; the back arrow calls [onBack] having chosen nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlannerPickerScreen(
    recipes: List<Recipe>,
    date: CalendarDate,
    slot: MealSlot,
    modifier: Modifier = Modifier,
    planned: List<String> = emptyList(),
    onPick: (Recipe) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val pickable = pickableRecipes(recipes, planned, query)
    val weekday = stringArrayResource(Res.array.calendar_weekdays)[date.dayOfWeek]
    val subtitle = stringResource(
        Res.string.meal_planner_pick_for,
        mealSlotLabel(slot),
        stringResource(Res.string.meal_planner_day_heading, weekday, date.day),
    )
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(MealPlannerPickerTestTags.Screen)) {
        val contentPadding = 16.dp
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
                            stringResource(Res.string.meal_planner_pick_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(MealPlannerPickerTestTags.BackButton)) {
                        Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            if (recipes.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        EmptyStateArt.Cookbook,
                        title = stringResource(Res.string.meal_planner_pick_empty_cookbook),
                        modifier = Modifier.testTag(MealPlannerPickerTestTags.Empty),
                    )
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.meal_planner_pick_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = contentPadding).testTag(MealPlannerPickerTestTags.Search),
                )
                if (pickable.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding), contentAlignment = Alignment.Center) {
                        EmptyState(
                            EmptyStateArt.NoResults,
                            title = stringResource(Res.string.meal_planner_pick_no_matches),
                            modifier = Modifier.testTag(MealPlannerPickerTestTags.Empty),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = contentPadding, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(pickable, key = { it.key }) { recipe ->
                            PickableRecipeRow(recipe, onClick = { onPick(recipe) }, modifier = Modifier.testTag(MealPlannerPickerTestTags.recipe(recipe.key)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickableRecipeRow(recipe: Recipe, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            recipe.media.firstNotNullOfOrNull { if (it.kind == MediaKind.Photo) it.blob else null }?.let { photo ->
                BlobImage(photo, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).testTag(MealPlannerPickerTestTags.thumbnail(recipe.key)))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(recipe.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    listOfNotNull(recipeTypeLabel(recipe.type), formatMinutes(recipe.totalMinutes).ifEmpty { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val PreviewDate = CalendarDate(2026, 10, 14)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerPickerPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MealPlannerPickerScreen(CookbookPreviewRecipes, PreviewDate, MealSlot.Dinner, planned = listOf("porridge")) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MealPlannerPickerPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MealPlannerPickerScreen(CookbookPreviewRecipes, PreviewDate, MealSlot.Dinner, planned = listOf("porridge")) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerPickerTabletLightPreview() {
    VestaTheme(darkTheme = false) { MealPlannerPickerScreen(CookbookPreviewRecipes, PreviewDate, MealSlot.Dinner, planned = listOf("porridge")) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MealPlannerPickerTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MealPlannerPickerScreen(CookbookPreviewRecipes, PreviewDate, MealSlot.Dinner, planned = listOf("porridge")) }
}
