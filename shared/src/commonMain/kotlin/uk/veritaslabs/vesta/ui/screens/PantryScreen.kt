package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.EmptyState
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.ic_merchants
import vesta.shared.generated.resources.ic_dots_vertical
import vesta.shared.generated.resources.ic_minus
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.pantry_add_item
import vesta.shared.generated.resources.pantry_back
import vesta.shared.generated.resources.pantry_categories_title
import vesta.shared.generated.resources.pantry_category_empty
import vesta.shared.generated.resources.pantry_empty_subtitle
import vesta.shared.generated.resources.pantry_empty_title
import vesta.shared.generated.resources.pantry_more_options
import vesta.shared.generated.resources.pantry_out_of_stock
import vesta.shared.generated.resources.pantry_select_cancel
import vesta.shared.generated.resources.pantry_select_count
import vesta.shared.generated.resources.pantry_select_merchants
import vesta.shared.generated.resources.pantry_stock_count
import vesta.shared.generated.resources.pantry_stock_decrease
import vesta.shared.generated.resources.pantry_stock_increase
import vesta.shared.generated.resources.pantry_title

private val PantryTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or item names. */
internal object PantryTestTags {
    const val BackButton = "pantry_back_button"
    const val AddItemButton = "pantry_add_item_button"
    const val OverflowButton = "pantry_overflow_button"
    const val CategoriesMenuItem = "pantry_categories_menu_item"
    const val SelectCancelButton = "pantry_select_cancel_button"
    const val SelectMerchantsButton = "pantry_select_merchants_button"
    const val SelectedCount = "pantry_selected_count"

    /** Tag for the merchant names of the merchants line of the row with the given [key]. */
    fun buyFrom(key: String) = "pantry_buy_from_$key"

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
    /** Keys of the merchants it is bought from, highest priority first (ADR-026); keys of deleted merchants may remain. */
    val merchantKeys: List<String> = emptyList(),
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
 * A long press on a row starts selecting: taps then pick or drop rows, the top bar shows how many are selected and a
 * merchants button, which opens [MerchantLinkDialog] over the Home's [merchants] and reports the chosen change for the
 * selected keys through [onChangeMerchants] (the batch update of ADR-026); the close button stops selecting. A row
 * with merchants lists their names, in priority order, under its title.
 *
 * UI only — the caller owns [items] and reacts to [onItemSelected], [onStockChange], [onBack]
 * (top-bar back arrow), [onManageCategories] (the "Categories" entry in the top-bar overflow menu) and [onAddItem] (floating action button); see the
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
    onManageCategories: () -> Unit = {},
    merchants: List<Merchant> = emptyList(),
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
            onManageCategories = onManageCategories,
            merchants = merchants,
            onChangeMerchants = viewModel::changeMerchants,
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
    onManageCategories: () -> Unit = {},
    merchants: List<Merchant> = emptyList(),
    onChangeMerchants: (Set<String>, MerchantLinkChange) -> Unit = { _, _ -> },
    initiallySelected: Set<String> = emptySet(),
) {
    // The keys of the rows picked for a batch update; empty means not selecting. Keys of rows that have gone are ignored.
    var picked by remember { mutableStateOf(initiallySelected) }
    val selected = picked.intersect(items.map { it.key }.toSet())
    val selecting = selected.isNotEmpty()
    var choosingMerchants by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= PantryTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                modifier = Modifier
                    .zIndex(1f)
                    .shadow(elevation = 16.dp, ambientColor = PantryAccent, spotColor = PantryAccent),
                title = {
                    if (selecting) {
                        Text(
                            stringResource(Res.string.pantry_select_count, selected.size),
                            modifier = Modifier.testTag(PantryTestTags.SelectedCount),
                        )
                    } else {
                        Text(stringResource(Res.string.pantry_title))
                    }
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { picked = emptySet() }, modifier = Modifier.testTag(PantryTestTags.SelectCancelButton)) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = stringResource(Res.string.pantry_select_cancel),
                            )
                        }
                    } else {
                        IconButton(onClick = onBack, modifier = Modifier.testTag(PantryTestTags.BackButton)) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_arrow_left),
                                contentDescription = stringResource(Res.string.pantry_back),
                            )
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(
                            onClick = { choosingMerchants = true },
                            modifier = Modifier.testTag(PantryTestTags.SelectMerchantsButton),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_merchants),
                                contentDescription = stringResource(Res.string.pantry_select_merchants),
                            )
                        }
                    } else {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag(PantryTestTags.OverflowButton),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_dots_vertical),
                                contentDescription = stringResource(Res.string.pantry_more_options),
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.pantry_categories_title)) },
                                onClick = {
                                    menuExpanded = false
                                    onManageCategories()
                                },
                                modifier = Modifier.testTag(PantryTestTags.CategoriesMenuItem),
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
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding)) {
            if (items.isEmpty() && emptyCategories.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        EmptyStateArt.Pantry,
                        title = stringResource(Res.string.pantry_empty_title),
                        body = stringResource(Res.string.pantry_empty_subtitle),
                    )
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
                                            onClick = {
                                                if (selecting) {
                                                    picked = if (pantryItem.key in selected) selected - pantryItem.key else selected + pantryItem.key
                                                } else {
                                                    onItemSelected(pantryItem)
                                                }
                                            },
                                            onLongClick = { picked = selected + pantryItem.key },
                                            onStockChange = { onStockChange(pantryItem, it) },
                                            selected = pantryItem.key in selected,
                                            buyFrom = linkedMerchants(pantryItem.merchantKeys, merchants).map { it.name },
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

        if (!selecting) FloatingActionButton(
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

    if (choosingMerchants && selecting) {
        MerchantLinkDialog(
            itemCount = selected.size,
            merchants = merchants,
            onApply = { change ->
                onChangeMerchants(selected, change)
                choosingMerchants = false
                picked = emptySet()
            },
            onDismiss = { choosingMerchants = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PantryItemRow(
    item: PantryListItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onStockChange: (Int) -> Unit = {},
    selected: Boolean = false,
    buyFrom: List<String> = emptyList(),
) {
    val shape = MaterialTheme.shapes.medium
    val fill = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.packageSize.isNotBlank()) {
                    Text(
                        text = " · ${item.packageSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (buyFrom.isNotEmpty()) {
                BuyFromLine(buyFrom, textModifier = Modifier.testTag(PantryTestTags.buyFrom(item.key)))
            }
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
        merchantKeys = listOf("tesco", "butcher"),
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
        PantryScreen(items = PantryPreviewItems, emptyCategories = PantryPreviewEmptyCategories, merchants = MerchantLinkPreviewMerchants)
    }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) {
        PantryScreen(items = PantryPreviewItems, emptyCategories = PantryPreviewEmptyCategories, merchants = MerchantLinkPreviewMerchants)
    }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) {
        PantryScreen(items = PantryPreviewItems, emptyCategories = PantryPreviewEmptyCategories, merchants = MerchantLinkPreviewMerchants)
    }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) {
        PantryScreen(items = PantryPreviewItems, emptyCategories = PantryPreviewEmptyCategories, merchants = MerchantLinkPreviewMerchants)
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

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryScreenSelectingPhoneLightPreview() {
    VestaTheme(darkTheme = false) {
        PantryScreen(items = PantryPreviewItems, merchants = MerchantLinkPreviewMerchants, initiallySelected = setOf("cheddar", "brie"))
    }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryScreenSelectingPhoneDarkPreview() {
    VestaTheme(darkTheme = true) {
        PantryScreen(items = PantryPreviewItems, merchants = MerchantLinkPreviewMerchants, initiallySelected = setOf("cheddar", "brie"))
    }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryScreenSelectingTabletLightPreview() {
    VestaTheme(darkTheme = false) {
        PantryScreen(items = PantryPreviewItems, merchants = MerchantLinkPreviewMerchants, initiallySelected = setOf("cheddar", "brie"))
    }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryScreenSelectingTabletDarkPreview() {
    VestaTheme(darkTheme = true) {
        PantryScreen(items = PantryPreviewItems, merchants = MerchantLinkPreviewMerchants, initiallySelected = setOf("cheddar", "brie"))
    }
}
