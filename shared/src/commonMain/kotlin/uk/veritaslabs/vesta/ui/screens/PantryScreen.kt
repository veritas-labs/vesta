package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_minus
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.pantry_add_item
import vesta.shared.generated.resources.pantry_back
import vesta.shared.generated.resources.pantry_category_empty
import vesta.shared.generated.resources.pantry_empty_subtitle
import vesta.shared.generated.resources.pantry_empty_title
import vesta.shared.generated.resources.pantry_out_of_stock
import vesta.shared.generated.resources.pantry_stock_count
import vesta.shared.generated.resources.pantry_stock_decrease
import vesta.shared.generated.resources.pantry_stock_increase
import vesta.shared.generated.resources.pantry_title

private val PantryTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or item names. */
internal object PantryTestTags {
    const val BackButton = "pantry_back_button"
    const val AddItemButton = "pantry_add_item_button"

    /** Tag for the row of the Pantry entry with the given [key] (its dedup key — see ADR-008). */
    fun pantryItem(key: String) = "pantry_item_$key"

    /** Tag for the "decrease stock" button on the row with the given [key]. */
    fun stockDecrease(key: String) = "pantry_stock_decrease_$key"

    /** Tag for the "increase stock" button on the row with the given [key]. */
    fun stockIncrease(key: String) = "pantry_stock_increase_$key"
}

/**
 * A single Pantry entry, as shown by [PantryScreen] — the UI-facing mirror of
 * `uk.veritaslabs.vesta.automerge.PantryItem` (ADR-008), kept separate so this
 * screen doesn't depend on the jvm/android-only `automerge` document layer.
 */
data class PantryListItem(
    val key: String,
    val primaryCategory: String,
    val secondaryCategory: String?,
    val title: String,
    val packageSize: String,
    val stockCount: Int,
    val barcode: String? = null,
)

/**
 * Read-only view of a Home's Pantry (ADR-008): [items] grouped by
 * [PantryListItem.primaryCategory] (an aisle-by-aisle view, matching how
 * shopping-list items are grouped), then by [PantryListItem.secondaryCategory]
 * within each aisle (when present — items with no secondary category are
 * listed directly under the aisle), each row showing the product and its current
 * [PantryListItem.stockCount]. [emptyCategories] are primary categories known
 * to the Home that currently have no items — they still get a header, with a
 * small empty-state message in place of any rows.
 *
 * Each row has minus/plus buttons either side of its stock count, reported through [onStockChange]
 * with the new count (never below zero).
 *
 * UI only — the caller owns [items] and reacts to [onItemSelected], [onStockChange], [onBack]
 * (top-bar back arrow) and [onAddItem] (floating action button); see the
 * [PantryViewModel] overload for the version wired to live Pantry state.
 */
@Composable
fun PantryScreen(
    viewModel: PantryViewModel,
    modifier: Modifier = Modifier,
    onItemSelected: (PantryListItem) -> Unit = {},
    onStockChange: (PantryListItem, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onAddItem: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        // Blank until the first emission, so the empty-Pantry message doesn't flash on launch.
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        PantryScreen(
            items = state.items,
            modifier = modifier,
            emptyCategories = state.emptyCategories,
            onItemSelected = onItemSelected,
            onStockChange = viewModel::setStockCount,
            onBack = onBack,
            onAddItem = onAddItem,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(
    items: List<PantryListItem>,
    modifier: Modifier = Modifier,
    emptyCategories: List<String> = emptyList(),
    onItemSelected: (PantryListItem) -> Unit = {},
    onStockChange: (PantryListItem, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onAddItem: () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= PantryTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.pantry_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(PantryTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.pantry_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding)) {
            if (items.isEmpty() && emptyCategories.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.pantry_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(Res.string.pantry_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                val grouped = items
                    .sortedWith(compareBy({ it.primaryCategory }, { it.secondaryCategory }, { it.title }))
                    .groupBy { it.primaryCategory }
                val categories = (grouped.keys + emptyCategories).distinct().sorted()

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                ) {
                    categories.forEach { category ->
                        item(key = "header_$category") {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        val categoryItems = grouped[category]
                        if (categoryItems == null) {
                            item(key = "empty_$category") {
                                Text(
                                    text = stringResource(Res.string.pantry_category_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                )
                            }
                        } else {
                            val bySecondary = categoryItems.groupBy { it.secondaryCategory }
                            bySecondary.forEach { (secondaryCategory, secondaryItems) ->
                                if (secondaryCategory != null) {
                                    item(key = "subheader_${category}_$secondaryCategory") {
                                        Text(
                                            text = secondaryCategory,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        )
                                    }
                                }
                                items(secondaryItems, key = { it.key }) { pantryItem ->
                                    Column {
                                        PantryItemRow(
                                            item = pantryItem,
                                            onClick = { onItemSelected(pantryItem) },
                                            onStockChange = { onStockChange(pantryItem, it) },
                                            modifier = Modifier.testTag(PantryTestTags.pantryItem(pantryItem.key)),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        FloatingActionButton(
            onClick = onAddItem,
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(contentPadding)
                .testTag(PantryTestTags.AddItemButton),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_plus),
                contentDescription = stringResource(Res.string.pantry_add_item),
            )
        }
    }
}

@Composable
private fun PantryItemRow(
    item: PantryListItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onStockChange: (Int) -> Unit = {},
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = " · ${item.packageSize}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        IconButton(
            onClick = { onStockChange(item.stockCount - 1) },
            enabled = item.stockCount > 0,
            modifier = Modifier.size(32.dp).testTag(PantryTestTags.stockDecrease(item.key)),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_minus),
                contentDescription = stringResource(Res.string.pantry_stock_decrease),
            )
        }
        Text(
            text = if (item.stockCount > 0) {
                stringResource(Res.string.pantry_stock_count, item.stockCount)
            } else {
                stringResource(Res.string.pantry_out_of_stock)
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (item.stockCount > 0) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.width(88.dp),
        )
        IconButton(
            onClick = { onStockChange(item.stockCount + 1) },
            modifier = Modifier.size(32.dp).testTag(PantryTestTags.stockIncrease(item.key)),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_plus),
                contentDescription = stringResource(Res.string.pantry_stock_increase),
            )
        }
    }
}

private val PantryPreviewItems = listOf(
    PantryListItem(
        key = "5000157024671",
        primaryCategory = "Condiments",
        secondaryCategory = "Ketchup",
        title = "Heinz Ketchup",
        packageSize = "500ml",
        stockCount = 4,
    ),
    PantryListItem(
        key = "brown sauce",
        primaryCategory = "Condiments",
        secondaryCategory = "Brown Sauce",
        title = "HP Brown Sauce",
        packageSize = "500ml",
        stockCount = 0,
    ),
    PantryListItem(
        key = "whole milk",
        primaryCategory = "Dairy",
        secondaryCategory = "Milk",
        title = "Whole Milk",
        packageSize = "2 pint",
        stockCount = 1,
    ),
    PantryListItem(
        key = "cheddar",
        primaryCategory = "Dairy",
        secondaryCategory = "Cheese",
        title = "Mature Cheddar",
        packageSize = "400g",
        stockCount = 2,
    ),
    PantryListItem(
        key = "brie",
        primaryCategory = "Dairy",
        secondaryCategory = "Cheese",
        title = "Brie",
        packageSize = "250g",
        stockCount = 1,
    ),
    PantryListItem(
        key = "chicken breast",
        primaryCategory = "Meat",
        secondaryCategory = "Poultry",
        title = "Chicken Breast",
        packageSize = "500g",
        stockCount = 2,
    ),
    PantryListItem(
        key = "beef mince",
        primaryCategory = "Meat",
        secondaryCategory = "Beef",
        title = "Beef Mince",
        packageSize = "500g",
        stockCount = 0,
    ),
    PantryListItem(
        key = "bananas",
        primaryCategory = "Fruit & Vegetables",
        secondaryCategory = null,
        title = "Bananas",
        packageSize = "6 pack",
        stockCount = 1,
    ),
    PantryListItem(
        key = "carrots",
        primaryCategory = "Fruit & Vegetables",
        secondaryCategory = null,
        title = "Carrots",
        packageSize = "1kg",
        stockCount = 3,
    ),
)

/** No items yet — shown alongside [PantryPreviewItems] to preview an empty-category section. */
private val PantryPreviewEmptyCategories = listOf("Snacks")

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) {
        PantryScreen(items = PantryPreviewItems, emptyCategories = PantryPreviewEmptyCategories)
    }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) {
        PantryScreen(items = PantryPreviewItems, emptyCategories = PantryPreviewEmptyCategories)
    }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) {
        PantryScreen(items = PantryPreviewItems, emptyCategories = PantryPreviewEmptyCategories)
    }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) {
        PantryScreen(items = PantryPreviewItems, emptyCategories = PantryPreviewEmptyCategories)
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { PantryScreen(items = emptyList()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PantryScreen(items = emptyList()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { PantryScreen(items = emptyList()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PantryScreen(items = emptyList()) }
}
