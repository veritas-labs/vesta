package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import uk.veritaslabs.vesta.blobs.BlobReference
import uk.veritaslabs.vesta.blobs.Placeholder
import uk.veritaslabs.vesta.ui.components.BlobImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.cookbook_favourite
import vesta.shared.generated.resources.cookbook_not_favourite
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_dots_vertical
import vesta.shared.generated.resources.ic_heart
import vesta.shared.generated.resources.ic_heart_filled
import vesta.shared.generated.resources.ic_minus
import vesta.shared.generated.resources.ic_photo
import vesta.shared.generated.resources.ic_player_play
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_video
import vesta.shared.generated.resources.pantry_back
import vesta.shared.generated.resources.recipe_availability_in_pantry
import vesta.shared.generated.resources.recipe_availability_not_enough
import vesta.shared.generated.resources.recipe_availability_not_in_pantry
import vesta.shared.generated.resources.recipe_availability_out_of_stock
import vesta.shared.generated.resources.recipe_cook_start
import vesta.shared.generated.resources.recipe_delete_cancel
import vesta.shared.generated.resources.recipe_delete_confirm
import vesta.shared.generated.resources.recipe_delete_confirm_body
import vesta.shared.generated.resources.recipe_delete_confirm_title
import vesta.shared.generated.resources.recipe_delete_menu
import vesta.shared.generated.resources.recipe_edit_menu
import vesta.shared.generated.resources.recipe_ingredients
import vesta.shared.generated.resources.recipe_media_photo
import vesta.shared.generated.resources.recipe_media_video
import vesta.shared.generated.resources.recipe_method
import vesta.shared.generated.resources.recipe_more_options
import vesta.shared.generated.resources.recipe_notes
import vesta.shared.generated.resources.recipe_servings
import vesta.shared.generated.resources.recipe_servings_decrease
import vesta.shared.generated.resources.recipe_servings_increase
import vesta.shared.generated.resources.recipe_servings_scaled
import vesta.shared.generated.resources.recipe_source
import vesta.shared.generated.resources.recipe_step_minutes
import vesta.shared.generated.resources.recipe_step_number
import vesta.shared.generated.resources.recipe_time_cook
import vesta.shared.generated.resources.recipe_time_prep
import vesta.shared.generated.resources.recipe_time_rest
import vesta.shared.generated.resources.recipe_time_total

private val RecipeTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object RecipeDetailTestTags {
    const val BackButton = "recipe_back_button"
    const val FavouriteButton = "recipe_favourite_button"
    const val OverflowButton = "recipe_overflow_button"
    const val EditMenuItem = "recipe_edit_menu_item"
    const val DeleteMenuItem = "recipe_delete_menu_item"
    const val ConfirmDelete = "recipe_delete_confirm"
    const val Title = "recipe_title"
    const val Description = "recipe_description"
    const val PrepTime = "recipe_time_prep"
    const val CookTime = "recipe_time_cook"
    const val RestTime = "recipe_time_rest"
    const val TotalTime = "recipe_time_total"
    const val Servings = "recipe_servings"
    const val ServingsIncrease = "recipe_servings_increase"
    const val ServingsDecrease = "recipe_servings_decrease"
    const val StartCookingButton = "recipe_start_cooking_button"

    fun star(n: Int) = "recipe_star_$n"

    fun media(index: Int) = "recipe_media_$index"

    /** Tag for the ingredient line at [index]; its availability label is tagged [availability]. */
    fun ingredient(index: Int) = "recipe_ingredient_$index"

    fun availability(index: Int) = "recipe_ingredient_availability_$index"

    /** Tag for the step with this 1-based [number]; its duration is tagged [stepMinutes]. */
    fun step(number: Int) = "recipe_step_$number"

    fun stepMinutes(number: Int) = "recipe_step_minutes_$number"
}

/**
 * One recipe (ADR-022): media, title, [onRate] stars (tapping the star that is already lit clears the rating), description, the
 * prep/cook/rest/total times, a servings stepper that rescales the ingredient quantities on screen only, the ingredients each
 * labelled by whether the [pantry] has them, and the method as a numbered list of steps.
 * The top bar has the favourite toggle ([onFavourite]) and an overflow menu with Edit ([onEdit]) and Delete ([onDelete], after confirming).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    modifier: Modifier = Modifier,
    pantry: List<PantryListItem> = emptyList(),
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRate: (Int) -> Unit = {},
    onFavourite: (Boolean) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var cooking by remember(recipe.key) { mutableStateOf(false) }
    var servings by remember(recipe.key) { mutableIntStateOf(recipe.servings.coerceAtLeast(1)) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= RecipeTabletBreakpoint) 24.dp else 16.dp
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            recipe.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(RecipeDetailTestTags.Title),
                        )
                        Text(
                            text = listOf(recipeTypeLabel(recipe.type)).plus(recipe.tags).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(RecipeDetailTestTags.BackButton)) {
                        Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.pantry_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onFavourite(!recipe.favourite) }, modifier = Modifier.testTag(RecipeDetailTestTags.FavouriteButton)) {
                        Icon(
                            painterResource(if (recipe.favourite) Res.drawable.ic_heart_filled else Res.drawable.ic_heart),
                            contentDescription = stringResource(if (recipe.favourite) Res.string.cookbook_favourite else Res.string.cookbook_not_favourite),
                            tint = if (recipe.favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(RecipeDetailTestTags.OverflowButton)) {
                            Icon(painterResource(Res.drawable.ic_dots_vertical), contentDescription = stringResource(Res.string.recipe_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.recipe_edit_menu)) },
                                onClick = { menuOpen = false; onEdit() },
                                modifier = Modifier.testTag(RecipeDetailTestTags.EditMenuItem),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.recipe_delete_menu), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmingDelete = true },
                                modifier = Modifier.testTag(RecipeDetailTestTags.DeleteMenuItem),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = contentPadding)
                    .padding(bottom = 32.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // The recipe shows one photo (its first); the description and rating sit beside it.
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        recipe.media.firstOrNull()?.let { MediaCard(it) }
                        Column(modifier = Modifier.weight(1f)) {
                            if (recipe.description.isNotBlank()) {
                                Text(
                                    recipe.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag(RecipeDetailTestTags.Description),
                                )
                            }
                            // The star buttons carry 12dp of touch padding each side; pull the row back so the first star lines up with the text.
                            RatingStars(
                                recipe.rating,
                                modifier = Modifier.offset(x = (-12).dp),
                                onRate = { star -> onRate(if (star == recipe.rating) 0 else star) },
                                starTag = RecipeDetailTestTags::star,
                            )
                        }
                    }
                    TimesRow(recipe, modifier = Modifier.padding(top = 16.dp))
                    IngredientsSection(recipe, servings, pantry, onServingsChange = { servings = it.coerceAtLeast(1) })
                    MethodSection(recipe.steps)
                    if (recipe.notes.isNotBlank()) {
                        SectionHeading(stringResource(Res.string.recipe_notes), divided = true)
                        Text(recipe.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (recipe.source.isNotBlank()) {
                        SectionHeading(stringResource(Res.string.recipe_source), divided = true)
                        Text(recipe.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        if (recipe.steps.isNotEmpty()) {
            FloatingActionButton(
                onClick = { cooking = true },
                shape = MaterialTheme.shapes.small,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(contentPadding)
                    .testTag(RecipeDetailTestTags.StartCookingButton),
            ) {
                Icon(painterResource(Res.drawable.ic_player_play), contentDescription = stringResource(Res.string.recipe_cook_start))
            }
        }
        if (cooking) {
            CookingScreen(recipe, servings, onClose = { cooking = false })
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(Res.string.recipe_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.recipe_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }, modifier = Modifier.testTag(RecipeDetailTestTags.ConfirmDelete)) {
                    Text(stringResource(Res.string.recipe_delete_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(Res.string.recipe_delete_cancel)) } },
        )
    }
}

/**
 * A section title; [divided] puts a rule above it so a major section (ingredients, method) stands apart from the one before.
 * [trailing] sits at the right end of the title's line.
 */
@Composable
private fun SectionHeading(text: String, divided: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    if (divided) {
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), modifier = Modifier.padding(top = 40.dp))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = if (divided) 28.dp else 24.dp, bottom = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun MediaCard(item: RecipeMedia) {
    OutlinedCard(modifier = Modifier.width(160.dp).height(112.dp).testTag(RecipeDetailTestTags.media(0))) {
        val blob = item.blob
        if (blob != null) {
            Box(Modifier.fillMaxSize()) {
                BlobImage(blob, Modifier.fillMaxSize(), contentDescription = item.caption.ifBlank { stringResource(Res.string.recipe_media_photo) })
                if (item.caption.isNotBlank()) {
                    Text(
                        item.caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        } else Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(if (item.kind == MediaKind.Video) Res.drawable.ic_video else Res.drawable.ic_photo),
                contentDescription = stringResource(if (item.kind == MediaKind.Video) Res.string.recipe_media_video else Res.string.recipe_media_photo),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Text(
                item.caption.ifBlank { item.uri.substringAfterLast('/') },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun TimesRow(recipe: Recipe, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val times = listOf(
            Triple(Res.string.recipe_time_prep, recipe.prepMinutes, RecipeDetailTestTags.PrepTime),
            Triple(Res.string.recipe_time_cook, recipe.cookMinutes, RecipeDetailTestTags.CookTime),
            Triple(Res.string.recipe_time_rest, recipe.restMinutes, RecipeDetailTestTags.RestTime),
            Triple(Res.string.recipe_time_total, recipe.totalMinutes, RecipeDetailTestTags.TotalTime),
        )
        times.forEach { (label, minutes, tag) ->
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatMinutes(minutes).ifEmpty { "-" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServingsStepper(servings: Int, original: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(servings - 1) }, enabled = servings > 1, modifier = Modifier.testTag(RecipeDetailTestTags.ServingsDecrease)) {
            Icon(painterResource(Res.drawable.ic_minus), contentDescription = stringResource(Res.string.recipe_servings_decrease))
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                stringResource(Res.string.recipe_servings, servings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(RecipeDetailTestTags.Servings),
            )
            if (servings != original) {
                Text(
                    stringResource(Res.string.recipe_servings_scaled, original),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onChange(servings + 1) }, modifier = Modifier.testTag(RecipeDetailTestTags.ServingsIncrease)) {
            Icon(painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.recipe_servings_increase))
        }
    }
}

@Composable
private fun IngredientsSection(recipe: Recipe, servings: Int, pantry: List<PantryListItem>, onServingsChange: (Int) -> Unit) {
    SectionHeading(stringResource(Res.string.recipe_ingredients), divided = true) {
        ServingsStepper(servings, recipe.servings, onChange = onServingsChange)
    }
    val states = recipe.ingredients.map { ingredient ->
        availability(ingredient, pantry, ingredient.quantity?.let { scaleQuantity(it, recipe.servings, servings) })
    }
    recipe.ingredients.forEachIndexed { index, ingredient ->
        val line = ingredientLine(ingredient, recipe.servings, servings)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(RecipeDetailTestTags.ingredient(index)),
        ) {
            Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            val state = states[index]
            Text(
                stringResource(
                    when (state) {
                        IngredientAvailability.InPantry -> Res.string.recipe_availability_in_pantry
                        IngredientAvailability.NotEnough -> Res.string.recipe_availability_not_enough
                        IngredientAvailability.OutOfStock -> Res.string.recipe_availability_out_of_stock
                        IngredientAvailability.NotInPantry -> Res.string.recipe_availability_not_in_pantry
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (state == IngredientAvailability.InPantry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).testTag(RecipeDetailTestTags.availability(index)),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** One ingredient as text, e.g. "500 g Lamb mince, diced", with its quantity rescaled from [original] to [servings]. */
internal fun ingredientLine(ingredient: RecipeIngredient, original: Int, servings: Int): String {
    val amount = listOfNotNull(
        ingredient.quantity?.let { formatQuantity(scaleQuantity(it, original, servings)) },
        ingredient.unit.ifBlank { null },
    ).joinToString(" ")
    return listOf(amount, ingredient.name).filter { it.isNotEmpty() }.joinToString(" ") +
        if (ingredient.note.isNotBlank()) ", ${ingredient.note}" else ""
}

@Composable
private fun MethodSection(steps: List<RecipeStep>) {
    if (steps.isEmpty()) return
    SectionHeading(stringResource(Res.string.recipe_method), divided = true)
    steps.forEachIndexed { index, step -> StepCard(step, index + 1, Modifier.fillMaxWidth().padding(bottom = 6.dp)) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StepCard(step: RecipeStep, number: Int, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.testTag(RecipeDetailTestTags.step(number))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(Res.string.recipe_step_number, number),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                step.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Always composed, so every step reserves the same bottom line (at any font scale) whether or not it has minutes.
            val minutes = step.minutes
            Text(
                minutes?.let { stringResource(Res.string.recipe_step_minutes, it) }.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (minutes != null) Modifier.testTag(RecipeDetailTestTags.stepMinutes(number)) else Modifier,
            )
        }
    }
}

internal val RecipePreview = Recipe(
    key = "pie",
    title = "Shepherd's Pie",
    description = "Lamb mince under a golden mash.",
    type = RecipeType.Dinner,
    servings = 4,
    prepMinutes = 20,
    cookMinutes = 50,
    restMinutes = 10,
    rating = 4,
    favourite = true,
    media = listOf(
        RecipeMedia(
            "", MediaKind.Photo, "Fresh from the oven",
            // Not on this device in a preview, so it shows the placeholder state.
            BlobReference("a".repeat(64), 90_000, "image/webp", MediaKind.Photo, 4, 3, Placeholder.encode(IntArray(12) { if (it % 4 < 2) 0xFFE8A33D.toInt() else 0xFF8A4B1F.toInt() }, 4, 3)),
        ),
        RecipeMedia("file:///photos/pie.jpg", MediaKind.Photo, "Old link"),
        RecipeMedia("https://example.com/pie.mp4", MediaKind.Video),
    ),
    ingredients = listOf(
        RecipeIngredient("Lamb mince", 500.0, "g"),
        RecipeIngredient("Potatoes", 800.0, "g"),
        RecipeIngredient("Onion", 1.0, note = "diced"),
        RecipeIngredient("Thyme"),
    ),
    steps = listOf(
        RecipeStep("a", "Peel and boil the potatoes.", minutes = 20),
        RecipeStep("b", "Brown the mince with the onion.", minutes = 15),
        RecipeStep("c", "Mash the potatoes."),
        RecipeStep("d", "Assemble and bake.", minutes = 30),
    ),
    tags = listOf("Comfort"),
    notes = "Better the next day.",
    source = "Gran",
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun RecipeDetailScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { RecipeDetailScreen(RecipePreview, pantry = CookbookPreviewPantry) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun RecipeDetailScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { RecipeDetailScreen(RecipePreview, pantry = CookbookPreviewPantry) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun RecipeDetailScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { RecipeDetailScreen(RecipePreview, pantry = CookbookPreviewPantry) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun RecipeDetailScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { RecipeDetailScreen(RecipePreview, pantry = CookbookPreviewPantry) }
}
