package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.blobs.MediaKind
import uk.veritaslabs.vesta.ui.components.ContentMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.blobs.LocalBlobs
import uk.veritaslabs.vesta.blobs.PhotoAddResult
import uk.veritaslabs.vesta.blobs.PhotoRejection
import uk.veritaslabs.vesta.recipes.LocalRecipeImporter
import uk.veritaslabs.vesta.recipes.RecipeImportFailure
import uk.veritaslabs.vesta.recipes.RecipeImportResult
import uk.veritaslabs.vesta.ui.components.BlobImage
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.rememberPhotoPickerForScreen
import vesta.shared.generated.resources.recipe_edit_media_add_photo
import vesta.shared.generated.resources.recipe_edit_media_remove_photo
import vesta.shared.generated.resources.recipe_edit_media_photo_too_large
import vesta.shared.generated.resources.recipe_edit_media_photo_unreachable
import vesta.shared.generated.resources.recipe_edit_media_photo_unreadable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.ic_dots_vertical
import vesta.shared.generated.resources.ic_minus
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_trash
import vesta.shared.generated.resources.recipe_edit_add_title
import vesta.shared.generated.resources.common_cancel
import vesta.shared.generated.resources.recipe_edit_cook_label
import vesta.shared.generated.resources.recipe_edit_description_label
import vesta.shared.generated.resources.recipe_edit_edit_title
import vesta.shared.generated.resources.recipe_edit_import_from_url
import vesta.shared.generated.resources.recipe_edit_more_options
import vesta.shared.generated.resources.recipe_import_confirm
import vesta.shared.generated.resources.recipe_import_in_progress
import vesta.shared.generated.resources.recipe_import_no_recipe
import vesta.shared.generated.resources.recipe_import_title
import vesta.shared.generated.resources.recipe_import_unreachable
import vesta.shared.generated.resources.recipe_import_url_label
import vesta.shared.generated.resources.recipe_edit_ingredient_add
import vesta.shared.generated.resources.recipe_edit_ingredient_name
import vesta.shared.generated.resources.recipe_edit_ingredient_quantity
import vesta.shared.generated.resources.recipe_edit_ingredient_remove
import vesta.shared.generated.resources.recipe_edit_ingredient_unit
import vesta.shared.generated.resources.recipe_edit_ingredients_heading
import vesta.shared.generated.resources.recipe_edit_media_heading
import vesta.shared.generated.resources.recipe_edit_notes_label
import vesta.shared.generated.resources.recipe_edit_prep_label
import vesta.shared.generated.resources.recipe_edit_rating_label
import vesta.shared.generated.resources.recipe_edit_rest_label
import vesta.shared.generated.resources.recipe_edit_save
import vesta.shared.generated.resources.recipe_edit_servings_label
import vesta.shared.generated.resources.recipe_edit_source_label
import vesta.shared.generated.resources.recipe_edit_step_add
import vesta.shared.generated.resources.recipe_step_number
import vesta.shared.generated.resources.recipe_edit_step_minutes
import vesta.shared.generated.resources.recipe_edit_step_remove
import vesta.shared.generated.resources.recipe_edit_step_text
import vesta.shared.generated.resources.recipe_edit_steps_heading
import vesta.shared.generated.resources.recipe_edit_tags_label
import vesta.shared.generated.resources.recipe_edit_title_label
import vesta.shared.generated.resources.recipe_edit_type_label
import vesta.shared.generated.resources.recipe_servings_decrease
import vesta.shared.generated.resources.recipe_servings_increase

private val RecipeEditTabletBreakpoint = 600.dp
/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object RecipeEditTestTags {
    const val CancelButton = "recipe_edit_cancel_button"
    const val SaveButton = "recipe_edit_save_button"
    const val TitleField = "recipe_edit_title"
    const val DescriptionField = "recipe_edit_description"
    const val ServingsIncrease = "recipe_edit_servings_increase"
    const val ServingsDecrease = "recipe_edit_servings_decrease"
    const val PrepField = "recipe_edit_prep"
    const val CookField = "recipe_edit_cook"
    const val RestField = "recipe_edit_rest"
    const val TagsField = "recipe_edit_tags"
    const val SourceField = "recipe_edit_source"
    const val NotesField = "recipe_edit_notes"
    const val RemovePhotoButton = "recipe_edit_media_remove_photo"
    const val AddPhotoButton = "recipe_edit_media_add_photo"
    const val PhotoError = "recipe_edit_media_photo_error"
    const val AddIngredientButton = "recipe_edit_ingredient_add"
    const val OverflowButton = "recipe_edit_overflow"
    const val ImportMenuItem = "recipe_edit_import_menu_item"
    const val ImportUrlField = "recipe_edit_import_url"
    const val ImportConfirmButton = "recipe_edit_import_confirm"
    const val ImportCancelButton = "recipe_edit_import_cancel"
    const val ImportError = "recipe_edit_import_error"

    fun typeChip(type: RecipeType) = "recipe_edit_type_${type.name}"

    fun star(n: Int) = "recipe_edit_star_$n"

    fun ingredientQuantity(index: Int) = "recipe_edit_ingredient_quantity_$index"

    fun ingredientUnit(index: Int) = "recipe_edit_ingredient_unit_$index"

    fun ingredientName(index: Int) = "recipe_edit_ingredient_name_$index"

    fun removeIngredient(index: Int) = "recipe_edit_ingredient_remove_$index"

    const val AddStepButton = "recipe_edit_step_add"

    fun stepText(index: Int) = "recipe_edit_step_text_$index"

    fun stepMinutes(index: Int) = "recipe_edit_step_minutes_$index"

    fun removeStep(index: Int) = "recipe_edit_step_remove_$index"
}

private data class IngredientDraft(val quantity: String = "", val unit: String = "", val name: String = "", val note: String = "")

private data class StepDraft(val id: String, val text: String = "", val minutes: String = "")

private fun Recipe.ingredientDrafts() = ingredients.map { IngredientDraft(it.quantity?.let(::formatQuantity).orEmpty(), it.unit, it.name, it.note) }

private fun Recipe.stepDrafts() = steps.map { StepDraft(it.id, it.text, it.minutes?.toString().orEmpty()) }

/**
 * The form for adding a recipe ([recipe] null) or editing one: title, description, type, servings, the three times,
 * rating, tags, source, notes, photos and videos, ingredients and the method, a list of steps done in order. Save is enabled once there is a title; blank ingredients and steps are dropped.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditScreen(
    recipe: Recipe?,
    modifier: Modifier = Modifier,
    onSave: (Recipe) -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val original = recipe ?: Recipe()
    var title by remember(recipe) { mutableStateOf(original.title) }
    var description by remember(recipe) { mutableStateOf(original.description) }
    var type by remember(recipe) { mutableStateOf(original.type) }
    var servings by remember(recipe) { mutableIntStateOf(original.servings) }
    var prep by remember(recipe) { mutableStateOf(original.prepMinutes.takeIf { it > 0 }?.toString().orEmpty()) }
    var cook by remember(recipe) { mutableStateOf(original.cookMinutes.takeIf { it > 0 }?.toString().orEmpty()) }
    var rest by remember(recipe) { mutableStateOf(original.restMinutes.takeIf { it > 0 }?.toString().orEmpty()) }
    var rating by remember(recipe) { mutableIntStateOf(original.rating) }
    var tags by remember(recipe) { mutableStateOf(original.tags.joinToString(", ")) }
    var source by remember(recipe) { mutableStateOf(original.source) }
    var notes by remember(recipe) { mutableStateOf(original.notes) }
    var media by remember(recipe) { mutableStateOf(original.media) }
    // With the blob machinery present (ADR-023) a photo can be picked from the device.
    val blobs = LocalBlobs.current
    val scope = rememberCoroutineScope()
    var photoRejection by remember(recipe) { mutableStateOf<PhotoRejection?>(null) }
    val photoPicker = rememberPhotoPickerForScreen { source ->
        blobs?.let { blobs ->
            scope.launch {
                when (val result = blobs.addPhoto(source)) {
                    is PhotoAddResult.Added -> {
                        photoRejection = null
                        media = media + RecipeMedia("", result.reference.kind, "", result.reference)
                    }
                    is PhotoAddResult.Rejected -> photoRejection = result.reason
                }
            }
        }
    }
    var ingredients by remember(recipe) { mutableStateOf(original.ingredientDrafts()) }
    var steps by remember(recipe) { mutableStateOf(original.stepDrafts()) }
    var nextStepNumber by remember(recipe) { mutableIntStateOf(1) }

    // Filling the form from a web link: offered when adding a recipe, where the app can download pages.
    val importer = LocalRecipeImporter.current.takeIf { recipe == null }
    var importOpen by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var importFailure by remember { mutableStateOf<RecipeImportFailure?>(null) }
    fun runImport() {
        val importer = importer ?: return
        importing = true
        importFailure = null
        scope.launch {
            when (val result = importer.import(importUrl)) {
                is RecipeImportResult.Failed -> importFailure = result.reason
                is RecipeImportResult.Imported -> {
                    val imported = result.recipe
                    title = imported.title
                    description = imported.description
                    type = imported.type
                    servings = imported.servings
                    prep = imported.prepMinutes.takeIf { it > 0 }?.toString().orEmpty()
                    cook = imported.cookMinutes.takeIf { it > 0 }?.toString().orEmpty()
                    rest = imported.restMinutes.takeIf { it > 0 }?.toString().orEmpty()
                    tags = imported.tags.joinToString(", ")
                    source = imported.source
                    ingredients = imported.ingredientDrafts()
                    steps = imported.stepDrafts()
                    importOpen = false
                    // The page's picture is a bonus: if it can't be downloaded the recipe is still imported.
                    result.photoUrl?.let { link ->
                        blobs?.let { blobs ->
                            (blobs.addPhotoFromUrl(link) as? PhotoAddResult.Added)?.let { added ->
                                media = media.filter { it.kind != MediaKind.Photo } + RecipeMedia("", added.reference.kind, "", added.reference)
                            }
                        }
                    }
                }
            }
            importing = false
        }
    }

    if (importOpen) {
        AlertDialog(
            onDismissRequest = { if (!importing) importOpen = false },
            title = { Text(stringResource(Res.string.recipe_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { importUrl = it },
                        label = { Text(stringResource(Res.string.recipe_import_url_label)) },
                        singleLine = true,
                        enabled = !importing,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().testTag(RecipeEditTestTags.ImportUrlField),
                    )
                    if (importing) Text(stringResource(Res.string.recipe_import_in_progress), style = MaterialTheme.typography.bodyMedium)
                    importFailure?.let {
                        Text(
                            stringResource(if (it == RecipeImportFailure.Unreachable) Res.string.recipe_import_unreachable else Res.string.recipe_import_no_recipe),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(RecipeEditTestTags.ImportError),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = ::runImport,
                    enabled = importUrl.isNotBlank() && !importing,
                    modifier = Modifier.testTag(RecipeEditTestTags.ImportConfirmButton),
                ) { Text(stringResource(Res.string.recipe_import_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { importOpen = false },
                    enabled = !importing,
                    modifier = Modifier.testTag(RecipeEditTestTags.ImportCancelButton),
                ) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= RecipeEditTabletBreakpoint) 24.dp else 16.dp
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(if (recipe == null) Res.string.recipe_edit_add_title else Res.string.recipe_edit_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag(RecipeEditTestTags.CancelButton)) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.common_cancel))
                    }
                },
                actions = {
                    importer?.let {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag(RecipeEditTestTags.OverflowButton)) {
                                Icon(painterResource(Res.drawable.ic_dots_vertical), contentDescription = stringResource(Res.string.recipe_edit_more_options))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.recipe_edit_import_from_url)) },
                                    onClick = {
                                        menuExpanded = false
                                        importFailure = null
                                        importOpen = true
                                    },
                                    modifier = Modifier.testTag(RecipeEditTestTags.ImportMenuItem),
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
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
                Column(modifier = Modifier.widthIn(max = ContentMaxWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(Res.string.recipe_edit_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(RecipeEditTestTags.TitleField),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(Res.string.recipe_edit_description_label)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag(RecipeEditTestTags.DescriptionField),
                    )
                    Heading(stringResource(Res.string.recipe_edit_type_label))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecipeType.entries.forEach { option ->
                            FilterChip(
                                selected = type == option,
                                onClick = { type = option },
                                label = { Text(recipeTypeLabel(option)) },
                                modifier = Modifier.testTag(RecipeEditTestTags.typeChip(option)),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MinutesField(prep, { prep = it }, stringResource(Res.string.recipe_edit_prep_label), RecipeEditTestTags.PrepField, Modifier.weight(1f))
                        MinutesField(cook, { cook = it }, stringResource(Res.string.recipe_edit_cook_label), RecipeEditTestTags.CookField, Modifier.weight(1f))
                        MinutesField(rest, { rest = it }, stringResource(Res.string.recipe_edit_rest_label), RecipeEditTestTags.RestField, Modifier.weight(1f))
                    }
                    Heading(stringResource(Res.string.recipe_edit_media_heading))
                    media.forEach { item ->
                        item.blob?.let { BlobImage(it, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) }
                    }
                    if (blobs != null) {
                        // A recipe has at most one photo: the button adds it, or (once there is one) removes it.
                        if (media.any { it.kind == MediaKind.Photo }) {
                            OutlinedButton(
                                onClick = { media = media.filter { it.kind != MediaKind.Photo } },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag(RecipeEditTestTags.RemovePhotoButton),
                            ) {
                                Text(stringResource(Res.string.recipe_edit_media_remove_photo))
                            }
                        } else {
                            OutlinedButton(onClick = { photoPicker.launch() }, modifier = Modifier.testTag(RecipeEditTestTags.AddPhotoButton)) {
                                Text(stringResource(Res.string.recipe_edit_media_add_photo))
                            }
                        }
                        photoRejection?.let {
                            Text(
                                stringResource(
                                    when (it) {
                                        PhotoRejection.TooLarge -> Res.string.recipe_edit_media_photo_too_large
                                        PhotoRejection.Unreadable -> Res.string.recipe_edit_media_photo_unreadable
                                        PhotoRejection.Unreachable -> Res.string.recipe_edit_media_photo_unreachable
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag(RecipeEditTestTags.PhotoError),
                            )
                        }
                    }
                    Heading(stringResource(Res.string.recipe_edit_rating_label))
                    RatingStars(rating, onRate = { star -> rating = if (star == rating) 0 else star }, starTag = RecipeEditTestTags::star)
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text(stringResource(Res.string.recipe_edit_tags_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(RecipeEditTestTags.TagsField),
                    )

                    Heading(stringResource(Res.string.recipe_edit_ingredients_heading))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.recipe_edit_servings_label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { servings = (servings - 1).coerceAtLeast(1) }, modifier = Modifier.testTag(RecipeEditTestTags.ServingsDecrease)) {
                            Icon(painterResource(Res.drawable.ic_minus), contentDescription = stringResource(Res.string.recipe_servings_decrease))
                        }
                        Text("$servings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp).testTag("recipe_edit_servings"))
                        IconButton(onClick = { servings += 1 }, modifier = Modifier.testTag(RecipeEditTestTags.ServingsIncrease)) {
                            Icon(painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.recipe_servings_increase))
                        }
                    }
                    ingredients.forEachIndexed { index, ingredient ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = ingredient.quantity,
                                onValueChange = { value -> ingredients = ingredients.update(index) { it.copy(quantity = value) } },
                                label = { Text(stringResource(Res.string.recipe_edit_ingredient_quantity)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(0.22f).testTag(RecipeEditTestTags.ingredientQuantity(index)),
                            )
                            OutlinedTextField(
                                value = ingredient.unit,
                                onValueChange = { value -> ingredients = ingredients.update(index) { it.copy(unit = value) } },
                                label = { Text(stringResource(Res.string.recipe_edit_ingredient_unit)) },
                                singleLine = true,
                                modifier = Modifier.weight(0.22f).testTag(RecipeEditTestTags.ingredientUnit(index)),
                            )
                            OutlinedTextField(
                                value = ingredient.name,
                                onValueChange = { value -> ingredients = ingredients.update(index) { it.copy(name = value) } },
                                label = { Text(stringResource(Res.string.recipe_edit_ingredient_name)) },
                                singleLine = true,
                                modifier = Modifier.weight(0.56f).testTag(RecipeEditTestTags.ingredientName(index)),
                            )
                            IconButton(
                                onClick = { ingredients = ingredients.filterIndexed { i, _ -> i != index } },
                                modifier = Modifier.testTag(RecipeEditTestTags.removeIngredient(index)),
                            ) {
                                Icon(painterResource(Res.drawable.ic_trash), contentDescription = stringResource(Res.string.recipe_edit_ingredient_remove))
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { ingredients = ingredients + IngredientDraft() },
                        modifier = Modifier.testTag(RecipeEditTestTags.AddIngredientButton),
                    ) { Text(stringResource(Res.string.recipe_edit_ingredient_add)) }

                    Heading(stringResource(Res.string.recipe_edit_steps_heading))
                    steps.forEachIndexed { index, step ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(Res.string.recipe_step_number, index + 1),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = { steps = steps.filterIndexed { i, _ -> i != index } },
                                        modifier = Modifier.testTag(RecipeEditTestTags.removeStep(index)),
                                    ) {
                                        Icon(painterResource(Res.drawable.ic_trash), contentDescription = stringResource(Res.string.recipe_edit_step_remove))
                                    }
                                }
                                OutlinedTextField(
                                    value = step.text,
                                    onValueChange = { value -> steps = steps.update(index) { it.copy(text = value) } },
                                    label = { Text(stringResource(Res.string.recipe_edit_step_text)) },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth().testTag(RecipeEditTestTags.stepText(index)),
                                )
                                MinutesField(
                                    step.minutes,
                                    { value -> steps = steps.update(index) { it.copy(minutes = value) } },
                                    stringResource(Res.string.recipe_edit_step_minutes),
                                    RecipeEditTestTags.stepMinutes(index),
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val taken = steps.map { it.id }.toSet()
                            while ("s$nextStepNumber" in taken) nextStepNumber++
                            steps = steps + StepDraft("s$nextStepNumber")
                            nextStepNumber++
                        },
                        modifier = Modifier.testTag(RecipeEditTestTags.AddStepButton),
                    ) { Text(stringResource(Res.string.recipe_edit_step_add)) }

                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = { Text(stringResource(Res.string.recipe_edit_source_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(RecipeEditTestTags.SourceField),
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(Res.string.recipe_edit_notes_label)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag(RecipeEditTestTags.NotesField),
                    )
                    PrimaryButton(
                        label = stringResource(Res.string.recipe_edit_save),
                        enabled = title.isNotBlank(),
                        onClick = {
                            onSave(
                                original.copy(
                                    title = title.trim(),
                                    description = description.trim(),
                                    type = type,
                                    servings = servings,
                                    prepMinutes = prep.toIntOrNull() ?: 0,
                                    cookMinutes = cook.toIntOrNull() ?: 0,
                                    restMinutes = rest.toIntOrNull() ?: 0,
                                    rating = rating,
                                    tags = normalizeRecipeTags(tags),
                                    source = source.trim(),
                                    notes = notes.trim(),
                                    media = media,
                                    ingredients = ingredients.filter { it.name.isNotBlank() }.map {
                                        RecipeIngredient(it.name.trim(), it.quantity.trim().toDoubleOrNull(), it.unit.trim(), it.note)
                                    },
                                    steps = steps.filter { it.text.isNotBlank() }.map { RecipeStep(it.id, it.text.trim(), it.minutes.toIntOrNull()) },
                                ),
                            )
                        },
                        modifier = Modifier.padding(top = 12.dp).testTag(RecipeEditTestTags.SaveButton),
                    )
                }
            }
        }
    }
}

/** [tags] as typed (comma separated), trimmed, without blanks or repeats (ignoring case). */
internal fun normalizeRecipeTags(tags: String): List<String> =
    tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

private fun <T> List<T>.update(index: Int, change: (T) -> T): List<T> = mapIndexed { i, item -> if (i == index) change(item) else item }

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun MinutesField(value: String, onChange: (String) -> Unit, label: String, tag: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.testTag(tag),
    )
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun RecipeEditScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { RecipeEditScreen(RecipePreview) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun RecipeEditScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { RecipeEditScreen(RecipePreview) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun RecipeEditScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { RecipeEditScreen(RecipePreview) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun RecipeEditScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { RecipeEditScreen(RecipePreview) }
}
