package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import uk.veritaslabs.vesta.ui.components.FabClearance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.EmptyState
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_minus
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.pantry_categories_add_category
import vesta.shared.generated.resources.pantry_categories_add_confirm
import vesta.shared.generated.resources.pantry_categories_add_subcategory
import vesta.shared.generated.resources.common_cancel
import vesta.shared.generated.resources.pantry_categories_empty_subtitle
import vesta.shared.generated.resources.pantry_categories_empty_title
import vesta.shared.generated.resources.pantry_categories_name_label
import vesta.shared.generated.resources.pantry_categories_no_subcategories
import vesta.shared.generated.resources.pantry_categories_remove_category
import vesta.shared.generated.resources.pantry_categories_remove_confirm
import vesta.shared.generated.resources.pantry_categories_remove_confirm_body
import vesta.shared.generated.resources.pantry_categories_remove_confirm_title
import vesta.shared.generated.resources.pantry_categories_remove_subcategory
import vesta.shared.generated.resources.pantry_categories_title

private val CategoriesTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or category names. */
internal object PantryCategoriesTestTags {
    const val BackButton = "pantry_categories_back_button"
    const val AddCategoryButton = "pantry_categories_add_category_button"
    const val NameField = "pantry_categories_name_field"
    const val ConfirmAdd = "pantry_categories_confirm_add"
    const val ConfirmRemove = "pantry_categories_confirm_remove"

    fun category(name: String) = "pantry_category_$name"
    fun addSecondary(primary: String) = "pantry_category_add_secondary_$primary"
    fun removePrimary(primary: String) = "pantry_category_remove_$primary"
    fun removeSecondary(primary: String, secondary: String) = "pantry_category_remove_${primary}_$secondary"
}

/** What the name dialog is adding: a primary category (`primary == null`) or a secondary under [primary]. */
private data class AddTarget(val primary: String?)

/**
 * A Home's categories (ADR-010): every primary category as a card listing its secondary categories, with
 * controls to add and remove either. UI only — the caller owns [categories] and reacts to the callbacks; see
 * the [PantryCategoriesViewModel] overload for the version wired to live state.
 */
@Composable
fun PantryCategoriesScreen(
    viewModel: PantryCategoriesViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        PantryCategoriesScreen(
            categories = state.categories,
            modifier = modifier,
            onAddPrimary = viewModel::addPrimary,
            onAddSecondary = viewModel::addSecondary,
            onDeletePrimary = viewModel::deletePrimary,
            onDeleteSecondary = viewModel::deleteSecondary,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PantryCategoriesScreen(
    categories: List<PantryCategoryGroup>,
    modifier: Modifier = Modifier,
    onAddPrimary: (String) -> Unit = {},
    onAddSecondary: (primary: String, name: String) -> Unit = { _, _ -> },
    onDeletePrimary: (String) -> Unit = {},
    onDeleteSecondary: (primary: String, name: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    var adding by remember { mutableStateOf<AddTarget?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= CategoriesTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.pantry_categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(PantryCategoriesTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            if (categories.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        EmptyStateArt.PantryCategories,
                        title = stringResource(Res.string.pantry_categories_empty_title),
                        body = stringResource(Res.string.pantry_categories_empty_subtitle),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding),
                    contentPadding = PaddingValues(top = 16.dp, bottom = FabClearance),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories, key = { it.name }) { category ->
                        val shape = MaterialTheme.shapes.medium
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag(PantryCategoriesTestTags.category(category.name)),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { removing = category.name },
                                    modifier = Modifier.size(32.dp)
                                        .testTag(PantryCategoriesTestTags.removePrimary(category.name)),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_minus),
                                        contentDescription = stringResource(
                                            Res.string.pantry_categories_remove_category,
                                            category.name,
                                        ),
                                    )
                                }
                            }
                            if (category.secondary.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.pantry_categories_no_subcategories),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            } else {
                                FlowRow(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    category.secondary.forEach { secondary ->
                                        InputChip(
                                            selected = false,
                                            onClick = { onDeleteSecondary(category.name, secondary) },
                                            label = { Text(secondary) },
                                            trailingIcon = {
                                                Icon(
                                                    painter = painterResource(Res.drawable.ic_minus),
                                                    contentDescription = stringResource(
                                                        Res.string.pantry_categories_remove_subcategory,
                                                        secondary,
                                                    ),
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            },
                                            modifier = Modifier.testTag(
                                                PantryCategoriesTestTags.removeSecondary(category.name, secondary),
                                            ),
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = { adding = AddTarget(category.name) },
                                modifier = Modifier.testTag(PantryCategoriesTestTags.addSecondary(category.name)),
                            ) {
                                Text(stringResource(Res.string.pantry_categories_add_subcategory))
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { adding = AddTarget(null) },
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(contentPadding)
                .testTag(PantryCategoriesTestTags.AddCategoryButton),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_plus),
                contentDescription = stringResource(Res.string.pantry_categories_add_category),
            )
        }
    }

    adding?.let { target ->
        CategoryNameDialog(
            title = stringResource(
                if (target.primary == null) {
                    Res.string.pantry_categories_add_category
                } else {
                    Res.string.pantry_categories_add_subcategory
                },
            ),
            onConfirm = { name ->
                target.primary?.let { onAddSecondary(it, name) } ?: onAddPrimary(name)
                adding = null
            },
            onDismiss = { adding = null },
        )
    }
    removing?.let { name ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.pantry_categories_remove_confirm_title, name)) },
            text = { Text(stringResource(Res.string.pantry_categories_remove_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePrimary(name)
                        removing = null
                    },
                    modifier = Modifier.testTag(PantryCategoriesTestTags.ConfirmRemove),
                ) { Text(stringResource(Res.string.pantry_categories_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun CategoryNameDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.pantry_categories_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.testTag(PantryCategoriesTestTags.NameField),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag(PantryCategoriesTestTags.ConfirmAdd),
            ) { Text(stringResource(Res.string.pantry_categories_add_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

private val PantryCategoriesPreviewData = listOf(
    PantryCategoryGroup("Condiments", listOf("Brown Sauce", "Ketchup", "Mayonnaise")),
    PantryCategoryGroup("Dairy", listOf("Cheese", "Milk", "Yoghurt")),
    PantryCategoryGroup("Fruit & Vegetables", listOf("Fruit", "Vegetables")),
    PantryCategoryGroup("Snacks"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryCategoriesScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { PantryCategoriesScreen(categories = PantryCategoriesPreviewData) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryCategoriesScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PantryCategoriesScreen(categories = PantryCategoriesPreviewData) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryCategoriesScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { PantryCategoriesScreen(categories = PantryCategoriesPreviewData) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryCategoriesScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PantryCategoriesScreen(categories = PantryCategoriesPreviewData) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryCategoriesScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { PantryCategoriesScreen(categories = emptyList()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryCategoriesScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PantryCategoriesScreen(categories = emptyList()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryCategoriesScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { PantryCategoriesScreen(categories = emptyList()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryCategoriesScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PantryCategoriesScreen(categories = emptyList()) }
}
