package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.blobs.MediaKind
import uk.veritaslabs.vesta.ui.components.FabClearance
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.kits.blobs.BlobReference
import uk.veritaslabs.kits.blobs.Placeholder
import uk.veritaslabs.vesta.ui.components.BlobImage
import uk.veritaslabs.vesta.ui.components.EmptyState
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.cookbook_add_recipe
import vesta.shared.generated.resources.cookbook_empty_subtitle
import vesta.shared.generated.resources.cookbook_empty_title
import vesta.shared.generated.resources.cookbook_favourite
import vesta.shared.generated.resources.cookbook_filter_favourites
import vesta.shared.generated.resources.cookbook_filter_label
import vesta.shared.generated.resources.cookbook_no_matches
import vesta.shared.generated.resources.cookbook_rated
import vesta.shared.generated.resources.cookbook_row_pantry
import vesta.shared.generated.resources.cookbook_row_serves
import vesta.shared.generated.resources.cookbook_search_label
import vesta.shared.generated.resources.cookbook_title
import vesta.shared.generated.resources.cookbook_unrated
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_heart_filled
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_star
import vesta.shared.generated.resources.ic_star_filled
import vesta.shared.generated.resources.ic_tag
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.recipe_rate_star
import vesta.shared.generated.resources.recipe_type_baking
import vesta.shared.generated.resources.recipe_type_breakfast
import vesta.shared.generated.resources.recipe_type_dessert
import vesta.shared.generated.resources.recipe_type_dinner
import vesta.shared.generated.resources.recipe_type_drink
import vesta.shared.generated.resources.recipe_type_lunch
import vesta.shared.generated.resources.recipe_type_other
import vesta.shared.generated.resources.recipe_type_side
import vesta.shared.generated.resources.recipe_type_snack
import vesta.shared.generated.resources.recipe_type_starter

private val CookbookTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or recipe titles. */
internal object CookbookTestTags {
    const val BackButton = "cookbook_back_button"
    const val AddRecipeButton = "cookbook_add_recipe_button"
    const val SearchField = "cookbook_search_field"
    const val FavouritesChip = "cookbook_favourites_chip"
    const val EmptyMessage = "cookbook_empty_message"

    fun typeChip(type: RecipeType) = "cookbook_type_chip_${type.name}"

    fun tagChip(name: String) = "cookbook_tag_chip_$name"

    /** Tag for the row of the recipe with the given [key]. */
    fun recipe(key: String) = "cookbook_recipe_$key"

    /** Tag for the pantry summary line of the recipe with the given [key]. */
    fun pantrySummary(key: String) = "cookbook_recipe_pantry_$key"

    /** Tag for the thumbnail of the recipe with the given [key]; present only when the recipe has a photo. */
    fun thumbnail(key: String) = "cookbook_recipe_thumbnail_$key"
}

@Composable
internal fun recipeTypeLabel(type: RecipeType): String = stringResource(
    when (type) {
        RecipeType.Breakfast -> Res.string.recipe_type_breakfast
        RecipeType.Lunch -> Res.string.recipe_type_lunch
        RecipeType.Dinner -> Res.string.recipe_type_dinner
        RecipeType.Starter -> Res.string.recipe_type_starter
        RecipeType.Side -> Res.string.recipe_type_side
        RecipeType.Dessert -> Res.string.recipe_type_dessert
        RecipeType.Baking -> Res.string.recipe_type_baking
        RecipeType.Snack -> Res.string.recipe_type_snack
        RecipeType.Drink -> Res.string.recipe_type_drink
        RecipeType.Other -> Res.string.recipe_type_other
    },
)

/**
 * Five stars showing [rating] (0 is none lit). With [onRate] each star is a button reporting its number, tagged
 * [starTag]; without it the row is a read-only picture described as "Rated N out of 5".
 */
@Composable
internal fun RatingStars(
    rating: Int,
    modifier: Modifier = Modifier,
    onRate: ((Int) -> Unit)? = null,
    starTag: (Int) -> String = { "" },
    starSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val description = if (rating > 0) stringResource(Res.string.cookbook_rated, rating) else stringResource(Res.string.cookbook_unrated)
    Row(modifier = if (onRate == null) modifier.semantics { contentDescription = description } else modifier) {
        for (star in 1..5) {
            val lit = star <= rating
            val icon = painterResource(if (lit) Res.drawable.ic_star_filled else Res.drawable.ic_star)
            val tint = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            if (onRate == null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(starSize))
            } else {
                IconButton(onClick = { onRate(star) }, modifier = Modifier.testTag(starTag(star))) {
                    Icon(
                        icon,
                        contentDescription = stringResource(Res.string.recipe_rate_star, star),
                        tint = tint,
                        modifier = Modifier.size(starSize + 4.dp),
                    )
                }
            }
        }
    }
}

/** The cookbook (ADR-022) wired to [viewModel]; blank until the recipes have loaded, so the empty message doesn't flash. */
@Composable
fun CookbookScreen(
    viewModel: CookbookViewModel,
    modifier: Modifier = Modifier,
    onRecipeSelected: (Recipe) -> Unit = {},
    onBack: () -> Unit = {},
    onAddRecipe: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        CookbookScreen(
            recipes = state.recipes,
            pantry = state.pantry,
            filter = state.filter,
            modifier = modifier,
            onRecipeSelected = onRecipeSelected,
            onBack = onBack,
            onAddRecipe = onAddRecipe,
            onQueryChange = viewModel::setQuery,
            onToggleType = viewModel::toggleType,
            onToggleFavourites = viewModel::toggleFavouritesOnly,
            onToggleTag = viewModel::toggleTag,
        )
    }
}

/**
 * The Home's recipes: a search field, filter chips (favourites, the types and tags in use) and a row per recipe in
 * [recipes] that [filter] lets through, each showing its title, type, total time, servings, stars and how many of its
 * ingredients the [pantry] has. A tap on a row calls [onRecipeSelected]; [onAddRecipe] is the floating action button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookbookScreen(
    recipes: List<Recipe>,
    modifier: Modifier = Modifier,
    pantry: List<PantryListItem> = emptyList(),
    filter: CookbookFilter = CookbookFilter(),
    onRecipeSelected: (Recipe) -> Unit = {},
    onBack: () -> Unit = {},
    onAddRecipe: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onToggleType: (RecipeType) -> Unit = {},
    onToggleFavourites: () -> Unit = {},
    onToggleTag: (String) -> Unit = {},
) {
    val filterDescription = stringResource(Res.string.cookbook_filter_label)
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= CookbookTabletBreakpoint) 24.dp else 16.dp
        val visible = filterRecipes(recipes, filter)

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.cookbook_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(CookbookTestTags.BackButton)) {
                        Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            if (recipes.isNotEmpty()) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(Res.string.cookbook_search_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = contentPadding).testTag(CookbookTestTags.SearchField),
                )
                val types = RecipeType.entries.filter { type -> recipes.any { it.type == type } }
                val tags = recipeTags(recipes)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = contentPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = filterDescription },
                ) {
                    item(key = "favourites") {
                        FilterChip(
                            selected = filter.favouritesOnly,
                            onClick = onToggleFavourites,
                            label = { Text(stringResource(Res.string.cookbook_filter_favourites)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_heart_filled), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag(CookbookTestTags.FavouritesChip),
                        )
                    }
                    items(types, key = { "type_${it.name}" }) { type ->
                        FilterChip(
                            selected = filter.type == type,
                            onClick = { onToggleType(type) },
                            label = { Text(recipeTypeLabel(type)) },
                            modifier = Modifier.testTag(CookbookTestTags.typeChip(type)),
                        )
                    }
                    items(tags, key = { "tag_${it.lowercase()}" }) { tag ->
                        FilterChip(
                            selected = tag.lowercase() in filter.tags,
                            onClick = { onToggleTag(tag) },
                            label = { Text(tag) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_tag), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag(CookbookTestTags.tagChip(tag)),
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        if (recipes.isEmpty()) EmptyStateArt.Cookbook else EmptyStateArt.NoResults,
                        title = stringResource(if (recipes.isEmpty()) Res.string.cookbook_empty_title else Res.string.cookbook_no_matches),
                        body = if (recipes.isEmpty()) stringResource(Res.string.cookbook_empty_subtitle) else null,
                        modifier = Modifier.testTag(CookbookTestTags.EmptyMessage),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = contentPadding, end = contentPadding, top = 4.dp, bottom = FabClearance),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.key }) { recipe ->
                        RecipeRow(recipe, pantry, onClick = { onRecipeSelected(recipe) }, modifier = Modifier.testTag(CookbookTestTags.recipe(recipe.key)))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddRecipe,
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(contentPadding)
                .testTag(CookbookTestTags.AddRecipeButton),
        ) {
            Icon(painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.cookbook_add_recipe))
        }
    }
}

@Composable
private fun RecipeRow(recipe: Recipe, pantry: List<PantryListItem>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            recipe.media.firstNotNullOfOrNull { if (it.kind == MediaKind.Photo) it.blob else null }?.let { photo ->
                BlobImage(photo, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).testTag(CookbookTestTags.thumbnail(recipe.key)))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (recipe.favourite) {
                        Icon(
                            painterResource(Res.drawable.ic_heart_filled),
                            contentDescription = stringResource(Res.string.cookbook_favourite),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                val details = listOfNotNull(
                    recipeTypeLabel(recipe.type),
                    formatMinutes(recipe.totalMinutes).ifEmpty { null },
                    stringResource(Res.string.cookbook_row_serves, recipe.servings),
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (recipe.ingredients.isNotEmpty()) {
                    val have = recipe.ingredients.count { availability(it, pantry) == IngredientAvailability.InPantry }
                    Text(
                        text = stringResource(Res.string.cookbook_row_pantry, have, recipe.ingredients.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(CookbookTestTags.pantrySummary(recipe.key)),
                    )
                }
                RatingStars(recipe.rating, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private val CookbookPreviewPhoto = BlobReference(
    hash = "b".repeat(64), size = 90_000, mime = "image/webp", kind = MediaKind.Photo, w = 4, h = 3,
    blur = Placeholder.encode(IntArray(12) { if (it % 4 < 2) 0xFFE8A33D.toInt() else 0xFF7A4B2A.toInt() }, 4, 3),
)

internal val CookbookPreviewRecipes = listOf(
    Recipe(
        key = "pie", title = "Shepherd's Pie", type = RecipeType.Dinner, servings = 4, prepMinutes = 20, cookMinutes = 50,
        rating = 5, favourite = true, tags = listOf("Comfort"),
        media = listOf(RecipeMedia("", MediaKind.Photo, blob = CookbookPreviewPhoto)),
        ingredients = listOf(RecipeIngredient("Lamb mince", 500.0, "g"), RecipeIngredient("Potatoes", 800.0, "g"), RecipeIngredient("Onion", 1.0)),
    ),
    Recipe(key = "crumble", title = "Apple Crumble", type = RecipeType.Dessert, servings = 6, prepMinutes = 15, cookMinutes = 40, rating = 3),
    Recipe(key = "porridge", title = "Porridge", type = RecipeType.Breakfast, servings = 2, cookMinutes = 5),
)

internal val CookbookPreviewPantry = listOf(
    PantryListItem("potatoes", "Veg", null, "Potatoes", "", 3),
    PantryListItem("onion", "Veg", null, "Onion", "", 0),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun CookbookScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { CookbookScreen(CookbookPreviewRecipes, pantry = CookbookPreviewPantry) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun CookbookScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { CookbookScreen(CookbookPreviewRecipes, pantry = CookbookPreviewPantry) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CookbookScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { CookbookScreen(CookbookPreviewRecipes, pantry = CookbookPreviewPantry) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CookbookScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { CookbookScreen(CookbookPreviewRecipes, pantry = CookbookPreviewPantry) }
}

@Preview(name = "Empty Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun CookbookScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { CookbookScreen(recipes = emptyList()) }
}

@Preview(name = "Empty Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun CookbookScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { CookbookScreen(recipes = emptyList()) }
}

@Preview(name = "Empty Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CookbookScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { CookbookScreen(recipes = emptyList()) }
}

@Preview(name = "Empty Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun CookbookScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { CookbookScreen(recipes = emptyList()) }
}
