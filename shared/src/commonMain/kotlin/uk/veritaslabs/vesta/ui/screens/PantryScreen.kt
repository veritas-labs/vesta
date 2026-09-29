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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.pantry_category_empty
import vesta.shared.generated.resources.pantry_empty_subtitle
import vesta.shared.generated.resources.pantry_empty_title
import vesta.shared.generated.resources.pantry_out_of_stock
import vesta.shared.generated.resources.pantry_stock_count
import vesta.shared.generated.resources.pantry_title

private val PantryTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or item names. */
internal object PantryTestTags {
    /** Tag for the row of the Pantry entry with the given [key] (its dedup key — see ADR-008). */
    fun pantryItem(key: String) = "pantry_item_$key"
}

/**
 * A single Pantry entry, as shown by [PantryScreen] — the UI-facing mirror of
 * `uk.veritaslabs.vesta.automerge.PantryItem` (ADR-008), kept separate so this
 * screen doesn't depend on the jvm/android-only `automerge` document layer.
 */
data class PantryListItem(
    val key: String,
    val primaryCategory: String,
    val secondaryCategory: String,
    val title: String,
    val packageSize: String,
    val stockCount: Int,
)

/**
 * Read-only view of a Home's Pantry (ADR-008): [items] grouped by
 * [PantryListItem.primaryCategory] (an aisle-by-aisle view, matching how
 * shopping-list items are grouped), then by [PantryListItem.secondaryCategory]
 * within each aisle, each row showing the product and its current
 * [PantryListItem.stockCount]. [emptyCategories] are primary categories known
 * to the Home that currently have no items — they still get a header, with a
 * small empty-state message in place of any rows.
 *
 * UI only — the caller owns [items] and reacts to [onItemSelected]; no
 * view-model or persistence lives here.
 */
@Composable
fun PantryScreen(
    items: List<PantryListItem>,
    modifier: Modifier = Modifier,
    emptyCategories: List<String> = emptyList(),
    onItemSelected: (PantryListItem) -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= PantryTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = contentPadding),
        ) {
            Spacer(Modifier.height(contentPadding))
            Text(
                text = stringResource(Res.string.pantry_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(contentPadding))

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
                    contentPadding = PaddingValues(vertical = 4.dp),
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
                                item(key = "subheader_${category}_$secondaryCategory") {
                                    Text(
                                        text = secondaryCategory,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    )
                                }
                                items(secondaryItems, key = { it.key }) { pantryItem ->
                                    Column {
                                        PantryItemRow(
                                            item = pantryItem,
                                            onClick = { onItemSelected(pantryItem) },
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
}

@Composable
private fun PantryItemRow(
    item: PantryListItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
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
        )
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
        secondaryCategory = "Fruit",
        title = "Bananas",
        packageSize = "6 pack",
        stockCount = 1,
    ),
    PantryListItem(
        key = "carrots",
        primaryCategory = "Fruit & Vegetables",
        secondaryCategory = "Vegetables",
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
