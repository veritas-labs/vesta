package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import vesta.shared.generated.resources.ic_building_store
import vesta.shared.generated.resources.ic_dots_vertical
import vesta.shared.generated.resources.ic_minus
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_adjustments
import vesta.shared.generated.resources.ic_tag
import vesta.shared.generated.resources.ic_trash
import vesta.shared.generated.resources.pantry_back
import vesta.shared.generated.resources.shopping_add_item
import vesta.shared.generated.resources.shopping_empty_subtitle
import vesta.shared.generated.resources.shopping_filter_merchants_label
import vesta.shared.generated.resources.shopping_filter_open
import vesta.shared.generated.resources.shopping_delete_item
import vesta.shared.generated.resources.shopping_edit_item
import vesta.shared.generated.resources.shopping_empty_title
import vesta.shared.generated.resources.shopping_got_count
import vesta.shared.generated.resources.shopping_got_decrease
import vesta.shared.generated.resources.shopping_got_increase
import vesta.shared.generated.resources.shopping_got_label
import vesta.shared.generated.resources.shopping_mark_in_stock
import vesta.shared.generated.resources.shopping_mark_out_of_stock
import vesta.shared.generated.resources.shopping_out_of_stock
import vesta.shared.generated.resources.shopping_quantity
import vesta.shared.generated.resources.shopping_hidden_by_filters
import vesta.shared.generated.resources.shopping_remaining
import vesta.shared.generated.resources.shopping_more_options
import vesta.shared.generated.resources.shopping_title
import vesta.shared.generated.resources.shopping_delete_all
import vesta.shared.generated.resources.shopping_delete_all_cancel
import vesta.shared.generated.resources.shopping_delete_all_confirm
import vesta.shared.generated.resources.shopping_delete_all_message
import vesta.shared.generated.resources.shopping_delete_all_title
import vesta.shared.generated.resources.shopping_uncheck_all
import vesta.shared.generated.resources.shopping_add_checked_to_pantry

private val ShoppingListTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or item names. */
internal object ShoppingListTestTags {
    const val BackButton = "shopping_back_button"
    const val AddItemButton = "shopping_add_item_button"
    const val RemainingCounter = "shopping_remaining_counter"
    const val HiddenNote = "shopping_hidden_note"
    const val OverflowButton = "shopping_overflow_button"
    const val FilterButton = "shopping_filter_button"
    const val AddCheckedToPantryItem = "shopping_add_checked_to_pantry_item"
    const val UncheckAllItem = "shopping_uncheck_all_item"
    const val DeleteAllItem = "shopping_delete_all_item"
    const val DeleteAllConfirmButton = "shopping_delete_all_confirm_button"
    const val DeleteAllCancelButton = "shopping_delete_all_cancel_button"

    /** Tag for the swipe-to-delete wrapper of the item with the given [key]; its semantic action `Dismiss` deletes it. */
    fun swipeToDelete(key: String) = "shopping_swipe_delete_$key"

    /** Tag for the filter chip of the tag [name] (on the filter screen). */
    fun filterChip(name: String) = "shopping_filter_chip_$name"

    /** Tag for the merchants line of the item with the given [key]. */
    fun buyFrom(key: String) = "shopping_buy_from_$key"

    /** Tag for the chip of the merchant with the given [key]. */
    fun merchantChip(key: String) = "shopping_merchant_chip_$key"

    /** Tag for the row of the item with the given [key] (its dedup key — see ADR-008). */
    fun item(key: String) = "shopping_item_$key"

    const val ItemSheet = "shopping_item_sheet"
    const val OutOfStockButton = "shopping_sheet_out_of_stock_button"
    const val EditItemButton = "shopping_sheet_edit_button"
    const val GotIncrease = "shopping_sheet_got_increase"
    const val GotDecrease = "shopping_sheet_got_decrease"
    const val GotCount = "shopping_sheet_got_count"
}

/**
 * The Home's shopping list (ADR-008), wired to [viewModel]. Blank until the list has loaded, so the empty message
 * doesn't flash on launch.
 */
@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel,
    modifier: Modifier = Modifier,
    onItemSelected: (ShoppingListItem) -> Unit = {},
    onBack: () -> Unit = {},
    onAddItem: () -> Unit = {},
    onOpenFilter: () -> Unit = {},
    merchants: List<Merchant> = emptyList(),
    pantryItems: List<PantryListItem> = emptyList(),
    onAddedToPantry: (addedCount: Int) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        ShoppingListScreen(
            withMerchantKeys(state.items, pantryItems), modifier, onItemSelected, onBack, onAddItem,
            selectedTags = state.selectedTags, hideChecked = state.hideChecked, onOpenFilter = onOpenFilter,
            merchants = merchants, selectedMerchants = state.selectedMerchants, onToggleMerchant = viewModel::toggleMerchant,
            onToggleCrossedOff = viewModel::toggleCrossedOff, onSetProgress = viewModel::setProgress,
            onAddCheckedToPantry = { viewModel.addCheckedToPantry(onAddedToPantry) },
            onUncheckAll = viewModel::uncheckAll, onDeleteItem = viewModel::remove, onDeleteAll = viewModel::removeAll,
        )
    }
}

/**
 * The shopping list: [items] grouped by primary category, then secondary category (an aisle-by-aisle view), each row
 * showing the product, its package size and the quantity to buy. A tap on a row calls [onToggleCrossedOff]; a long
 * press opens a bottom sheet to mark the item out of stock or say how many were got ([onSetProgress]: key, got,
 * out of stock), with a button that calls [onItemSelected] (to edit it). Crossed-off, partly got and out-of-stock
 * rows look different (see [ShoppingListItem.isCrossedOff]). [onAddItem] is the floating action button; [onBack] the top-bar back arrow.
 *
 * Swiping a row either way (partway reveals a red background with a trash can) deletes the item via [onDeleteItem].
 *
 * The top bar's overflow menu has three entries: "Add checked items to Pantry", which calls [onAddCheckedToPantry]
 * (enabled while some item is [ShoppingListItem.isBought]; the wired screen then calls [onAddedToPantry] with how many
 * items were added, for the app to show [AddedToPantryScreen]), "Uncheck all items", which calls [onUncheckAll], and
 * "Delete all items", which asks for confirmation and then calls [onDeleteAll].
 *
 * Under the top bar (and under the filter chips, when shown) a stepped progress bar has one step per item on the
 * list, filled for each one checked off (crossed-off, partly got and out-of-stock items all count) and described to
 * accessibility as "4 of 12 items remaining". The filters do not change the bar; when they hide items, a note under it
 * says how many.
 *
 * When any item is bought from a merchant in [merchants], a row of chips (one per such merchant) sits under the top bar:
 * [selectedMerchants] (keys) are the chips switched on, [onToggleMerchant] is called with a chip's merchant key when it
 * is tapped, and with any on, only the items bought from one of them are shown (see [filterByMerchants]).
 *
 * The settings icon in the top bar calls [onOpenFilter] (the filter screen, [ShoppingListFilterScreen]); [selectedTags]
 * (lower-cased) and [hideChecked] are what it has set, and narrow the list too (see [applyShoppingFilters]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    items: List<ShoppingListItem>,
    modifier: Modifier = Modifier,
    onItemSelected: (ShoppingListItem) -> Unit = {},
    onBack: () -> Unit = {},
    onAddItem: () -> Unit = {},
    selectedTags: Set<String> = emptySet(),
    hideChecked: Boolean = false,
    onOpenFilter: () -> Unit = {},
    merchants: List<Merchant> = emptyList(),
    selectedMerchants: Set<String> = emptySet(),
    onToggleMerchant: (String) -> Unit = {},
    onToggleCrossedOff: (ShoppingListItem) -> Unit = {},
    onSetProgress: (key: String, got: Int, outOfStock: Boolean) -> Unit = { _, _, _ -> },
    onAddCheckedToPantry: () -> Unit = {},
    onUncheckAll: () -> Unit = {},
    onDeleteItem: (key: String) -> Unit = {},
    onDeleteAll: () -> Unit = {},
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var sheetKey by remember { mutableStateOf<String?>(null) }
    val filterDescription = stringResource(Res.string.shopping_filter_merchants_label)
    var menuOpen by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= ShoppingListTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.shopping_title)) },
                actions = {
                    IconButton(onClick = onOpenFilter, modifier = Modifier.testTag(ShoppingListTestTags.FilterButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_adjustments),
                            contentDescription = stringResource(Res.string.shopping_filter_open),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(ShoppingListTestTags.OverflowButton)) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_dots_vertical),
                                contentDescription = stringResource(Res.string.shopping_more_options),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.shopping_add_checked_to_pantry)) },
                                enabled = items.any { it.isBought },
                                onClick = {
                                    menuOpen = false
                                    onAddCheckedToPantry()
                                },
                                modifier = Modifier.testTag(ShoppingListTestTags.AddCheckedToPantryItem),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.shopping_uncheck_all)) },
                                onClick = {
                                    menuOpen = false
                                    onUncheckAll()
                                },
                                modifier = Modifier.testTag(ShoppingListTestTags.UncheckAllItem),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.shopping_delete_all)) },
                                onClick = {
                                    menuOpen = false
                                    confirmDeleteAll = true
                                },
                                modifier = Modifier.testTag(ShoppingListTestTags.DeleteAllItem),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(ShoppingListTestTags.BackButton)) {
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
            val shownMerchants = shoppingMerchants(items, merchants)
            if (shownMerchants.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = filterDescription },
                ) {
                    items(shownMerchants, key = { it.key }) { merchant ->
                        FilterChip(
                            selected = merchant.key in selectedMerchants,
                            onClick = { onToggleMerchant(merchant.key) },
                            label = { Text(merchant.name) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_building_store),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.testTag(ShoppingListTestTags.merchantChip(merchant.key)),
                        )
                    }
                }
            }
            val visibleItems = applyShoppingFilters(items, selectedTags, selectedMerchants, hideChecked)
            if (items.isNotEmpty()) {
                ShoppingProgress(
                    total = items.size,
                    remaining = shoppingRemaining(items),
                    hidden = items.size - visibleItems.size,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding)
                        .padding(top = 8.dp, bottom = 4.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding)) {
                if (items.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(Res.string.shopping_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(Res.string.shopping_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else {
                    val grouped = visibleItems
                        .sortedWith(compareBy({ it.primaryCategory }, { it.secondaryCategory }, { it.title }))
                        .groupBy { it.primaryCategory }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                    ) {
                        grouped.forEach { (category, categoryItems) ->
                            item(key = "header_$category") {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                                )
                            }
                            categoryItems.groupBy { it.secondaryCategory }.forEach { (secondary, secondaryItems) ->
                                if (secondary != null) {
                                    item(key = "subheader_${category}_$secondary") {
                                        Text(
                                            text = secondary,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        )
                                    }
                                }
                                items(secondaryItems, key = { it.key }) { item ->
                                    Column {
                                        SwipeToDeleteRow(
                                            onDelete = { onDeleteItem(item.key) },
                                            modifier = Modifier.testTag(ShoppingListTestTags.swipeToDelete(item.key)),
                                        ) {
                                            ShoppingItemRow(
                                                item = item,
                                                onClick = { onToggleCrossedOff(item) },
                                                onLongClick = { sheetKey = item.key },
                                                buyFrom = linkedMerchants(item.merchantKeys, merchants).map { it.name },
                                                modifier = Modifier.testTag(ShoppingListTestTags.item(item.key)),
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
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
                .testTag(ShoppingListTestTags.AddItemButton),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_plus),
                contentDescription = stringResource(Res.string.shopping_add_item),
            )
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(Res.string.shopping_delete_all_title)) },
            text = { Text(stringResource(Res.string.shopping_delete_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        onDeleteAll()
                    },
                    modifier = Modifier.testTag(ShoppingListTestTags.DeleteAllConfirmButton),
                ) { Text(stringResource(Res.string.shopping_delete_all_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDeleteAll = false },
                    modifier = Modifier.testTag(ShoppingListTestTags.DeleteAllCancelButton),
                ) { Text(stringResource(Res.string.shopping_delete_all_cancel)) }
            },
        )
    }

    // Looked up by key so the sheet follows the item as it changes; it closes if the item goes away.
    items.firstOrNull { it.key == sheetKey }?.let { sheetItem ->
        ModalBottomSheet(onDismissRequest = { sheetKey = null }, sheetState = rememberModalBottomSheetState()) {
            ShoppingItemSheetContent(
                item = sheetItem,
                onSetProgress = { got, outOfStock -> onSetProgress(sheetItem.key, got, outOfStock) },
                onEdit = {
                    sheetKey = null
                    onItemSelected(sheetItem)
                },
                onOutOfStockMarked = { sheetKey = null },
            )
        }
    }
}

/**
 * A stepped progress bar with one step per item on the list ([total], whatever the filters), filled for each item
 * checked off ([total] - [remaining]). When filters hide [hidden] items, a note under the bar says so. Its
 * accessibility description is the "x of y items remaining" count.
 */
@Composable
private fun ShoppingProgress(total: Int, remaining: Int, hidden: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.shopping_remaining, remaining, total)
    val gap = when {
        total > 100 -> 0.dp
        total > 40 -> 1.dp
        else -> 3.dp
    }
    val checked = total - remaining
    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .semantics { contentDescription = description }
                .testTag(ShoppingListTestTags.RemainingCounter),
        ) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            if (index < checked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        }
        if (hidden > 0) {
            Text(
                text = stringResource(Res.string.shopping_hidden_by_filters, hidden),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).testTag(ShoppingListTestTags.HiddenNote),
            )
        }
    }
}

@Composable
private fun ShoppingItemSheetContent(
    item: ShoppingListItem,
    onSetProgress: (got: Int, outOfStock: Boolean) -> Unit,
    onEdit: () -> Unit,
    onOutOfStockMarked: () -> Unit,
) {
    val got = item.got.coerceIn(0, item.quantity)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(ShoppingListTestTags.ItemSheet)) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.shopping_got_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onSetProgress(got - 1, false) },
                enabled = got > 0,
                modifier = Modifier.testTag(ShoppingListTestTags.GotDecrease),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_minus),
                    contentDescription = stringResource(Res.string.shopping_got_decrease),
                )
            }
            Text(
                text = stringResource(Res.string.shopping_got_count, got, item.quantity),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp).testTag(ShoppingListTestTags.GotCount),
            )
            IconButton(
                onClick = { onSetProgress(got + 1, false) },
                enabled = got < item.quantity,
                modifier = Modifier.testTag(ShoppingListTestTags.GotIncrease),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_plus),
                    contentDescription = stringResource(Res.string.shopping_got_increase),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (item.outOfStock) {
                    onSetProgress(0, false)
                } else {
                    onSetProgress(0, true)
                    onOutOfStockMarked()
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(ShoppingListTestTags.OutOfStockButton),
        ) {
            Text(
                stringResource(if (item.outOfStock) Res.string.shopping_mark_in_stock else Res.string.shopping_mark_out_of_stock),
            )
        }
        TextButton(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth().testTag(ShoppingListTestTags.EditItemButton),
        ) {
            Text(stringResource(Res.string.shopping_edit_item))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Calls [onDelete] when [content] is swiped away in either direction; the red trash background shows underneath as it slides. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onDelete()
            true
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val direction = state.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.error).padding(horizontal = 20.dp),
                    contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_trash),
                        contentDescription = stringResource(Res.string.shopping_delete_item),
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        },
        content = { content() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingItemRow(
    item: ShoppingListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    buyFrom: List<String> = emptyList(),
) {
    val shape = MaterialTheme.shapes.medium
    val faded = item.isCrossedOff || item.outOfStock
    val strike = if (item.isCrossedOff) TextDecoration.LineThrough else TextDecoration.None
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (faded) 0.5f else 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .pointerInput(onLongClick) {
                // A right mouse button press (desktop) does what a long press does.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) onLongClick()
                    }
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                    textDecoration = strike,
                )
                if (item.packageSize.isNotBlank()) {
                    Text(
                        text = " · ${item.packageSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = strike,
                    )
                }
            }
            if (buyFrom.isNotEmpty()) {
                BuyFromLine(buyFrom, textModifier = Modifier.testTag(ShoppingListTestTags.buyFrom(item.key)))
            }
            if (item.tags.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_tag),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        when {
            item.outOfStock -> Text(
                text = stringResource(Res.string.shopping_out_of_stock),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            item.isPartlyGot -> Text(
                text = stringResource(Res.string.shopping_got_count, item.got, item.quantity),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            else -> Text(
                text = stringResource(Res.string.shopping_quantity, item.quantity),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                textDecoration = strike,
            )
        }
    }
}

private val ShoppingPreviewItems = listOf(
    ShoppingListItem("5000157024671", "Condiments", "Ketchup", "Heinz Ketchup", 2, "500ml", "5000157024671", listOf("Supermarket"), merchantKeys = listOf("tesco")),
    ShoppingListItem("whole milk", "Dairy", "Milk", "Whole Milk", 4, "2 pint", tags = listOf("Farm shop"), got = 2, merchantKeys = listOf("farm")),
    ShoppingListItem("brie", "Dairy", "Cheese", "Brie", 1, "250g", tags = listOf("Supermarket"), got = 1, merchantKeys = listOf("tesco")),
    ShoppingListItem("bananas", "Fruit & Vegetables", null, "Bananas", 1, "6 pack", tags = listOf("Supermarket"), got = 1),
    ShoppingListItem("carrots", "Fruit & Vegetables", null, "Carrots", 2, "1kg", tags = listOf("Farm shop"), merchantKeys = listOf("farm")),
    ShoppingListItem("sausages", "Meat", null, "Sausages", 1, "8 pack", tags = listOf("Butcher"), outOfStock = true, merchantKeys = listOf("butcher")),
)

private val ShoppingPreviewMerchants = listOf(
    Merchant(key = "tesco", name = "Tesco", type = MerchantType.NationalChain),
    Merchant(key = "farm", name = "Farm shop"),
    Merchant(key = "butcher", name = "Butcher"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListScreen(items = ShoppingPreviewItems, merchants = ShoppingPreviewMerchants) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListScreen(items = ShoppingPreviewItems, merchants = ShoppingPreviewMerchants) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListScreen(items = ShoppingPreviewItems, merchants = ShoppingPreviewMerchants) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListScreen(items = ShoppingPreviewItems, merchants = ShoppingPreviewMerchants) }
}

@Preview(name = "Empty Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListScreen(items = emptyList()) }
}

@Preview(name = "Empty Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListScreen(items = emptyList()) }
}

@Preview(name = "Empty Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListScreen(items = emptyList()) }
}

@Preview(name = "Empty Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListScreen(items = emptyList()) }
}

@Preview(name = "Filtered Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListScreenFilteredPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListScreen(items = ShoppingPreviewItems, merchants = ShoppingPreviewMerchants, selectedMerchants = setOf("farm"), hideChecked = true) }
}

@Preview(name = "Filtered Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListScreenFilteredPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListScreen(items = ShoppingPreviewItems, merchants = ShoppingPreviewMerchants, selectedMerchants = setOf("farm"), hideChecked = true) }
}

@Preview(name = "Filtered Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListScreenFilteredTabletLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListScreen(items = ShoppingPreviewItems, merchants = ShoppingPreviewMerchants, selectedMerchants = setOf("farm"), hideChecked = true) }
}

@Preview(name = "Filtered Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListScreenFilteredTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListScreen(items = ShoppingPreviewItems, merchants = ShoppingPreviewMerchants, selectedMerchants = setOf("farm"), hideChecked = true) }
}
