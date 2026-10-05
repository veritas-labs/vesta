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
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.pantry_back
import vesta.shared.generated.resources.shopping_add_item
import vesta.shared.generated.resources.shopping_empty_subtitle
import vesta.shared.generated.resources.shopping_empty_title
import vesta.shared.generated.resources.shopping_quantity
import vesta.shared.generated.resources.shopping_title

private val ShoppingListTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or item names. */
internal object ShoppingListTestTags {
    const val BackButton = "shopping_back_button"
    const val AddItemButton = "shopping_add_item_button"

    /** Tag for the row of the item with the given [key] (its dedup key — see ADR-008). */
    fun item(key: String) = "shopping_item_$key"
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        ShoppingListScreen(state.items, modifier, onItemSelected, onBack, onAddItem)
    }
}

/**
 * The shopping list: [items] grouped by primary category, then secondary category (an aisle-by-aisle view), each row
 * showing the product, its package size and the quantity to buy. A tap on a row calls [onItemSelected] (to edit
 * it); [onAddItem] is the floating action button; [onBack] the top-bar back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    items: List<ShoppingListItem>,
    modifier: Modifier = Modifier,
    onItemSelected: (ShoppingListItem) -> Unit = {},
    onBack: () -> Unit = {},
    onAddItem: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= ShoppingListTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.shopping_title)) },
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
                    val grouped = items
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
                                        ShoppingItemRow(
                                            item = item,
                                            onClick = { onItemSelected(item) },
                                            modifier = Modifier.testTag(ShoppingListTestTags.item(item.key)),
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
}

@Composable
private fun ShoppingItemRow(item: ShoppingListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
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
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(Res.string.shopping_quantity, item.quantity),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val ShoppingPreviewItems = listOf(
    ShoppingListItem("5000157024671", "Condiments", "Ketchup", "Heinz Ketchup", 2, "500ml", "5000157024671"),
    ShoppingListItem("whole milk", "Dairy", "Milk", "Whole Milk", 3, "2 pint"),
    ShoppingListItem("brie", "Dairy", "Cheese", "Brie", 1, "250g"),
    ShoppingListItem("bananas", "Fruit & Vegetables", null, "Bananas", 1, "6 pack"),
    ShoppingListItem("carrots", "Fruit & Vegetables", null, "Carrots", 2, "1kg"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListScreen(items = ShoppingPreviewItems) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListScreen(items = ShoppingPreviewItems) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListScreen(items = ShoppingPreviewItems) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListScreen(items = ShoppingPreviewItems) }
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
