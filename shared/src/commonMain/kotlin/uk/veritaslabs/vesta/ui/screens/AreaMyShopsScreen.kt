package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_favourite_cancel
import vesta.shared.generated.resources.area_favourite_confirm
import vesta.shared.generated.resources.area_favourite_in_use
import vesta.shared.generated.resources.area_favourite_remove
import vesta.shared.generated.resources.area_favourite_remove_title
import vesta.shared.generated.resources.area_my_shops
import vesta.shared.generated.resources.area_my_shops_empty
import vesta.shared.generated.resources.ic_heart
import vesta.shared.generated.resources.ic_heart_filled

/** A heart toggle: filled when [filled], described by [description], with test tag [tag]. */
@Composable
internal fun HeartButton(filled: Boolean, description: String, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(
            painterResource(if (filled) Res.drawable.ic_heart_filled else Res.drawable.ic_heart),
            contentDescription = description,
            tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Used by N items in Pantry or the shopping list" before a favourite in use is removed: Cancel or Remove. */
@Composable
internal fun RemoveShopDialog(pending: PendingShopRemoval, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.area_favourite_remove_title)) },
        text = { Text(stringResource(Res.string.area_favourite_in_use, pending.name, pending.usageCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(MarketplaceAreaTestTags.RemoveShopConfirm)) {
                Text(stringResource(Res.string.area_favourite_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(MarketplaceAreaTestTags.RemoveShopCancel)) {
                Text(stringResource(Res.string.area_favourite_cancel))
            }
        },
        modifier = Modifier.testTag(MarketplaceAreaTestTags.RemoveShopDialog),
    )
}

/**
 * My Shops (ADR-039): every saved [shops] entry A to Z, each with a filled heart ([onRemove] by key) that removes it. Reads
 * the merchants document only, so it works offline. Hand-made merchants appear too; nothing here adds or edits.
 */
@Composable
fun AreaMyShopsScreen(
    shops: List<Merchant>,
    modifier: Modifier = Modifier,
    pendingRemoval: PendingShopRemoval? = null,
    onRemove: (String) -> Unit = {},
    onConfirmRemoval: () -> Unit = {},
    onCancelRemoval: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    pendingRemoval?.let { RemoveShopDialog(it, onConfirmRemoval, onCancelRemoval) }
    AreaScaffold(stringResource(Res.string.area_my_shops), onBack, modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shops.isEmpty()) {
                Text(
                    stringResource(Res.string.area_my_shops_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 12.dp).testTag(MarketplaceAreaTestTags.MyShopsEmpty),
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(MarketplaceAreaTestTags.MyShopsList)) {
                items(shops, key = { it.key }) { shop ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(MarketplaceAreaTestTags.myShopsItem(shop.key)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
                            Text(shop.name, style = MaterialTheme.typography.titleMedium)
                            val where = (shop.customServices + shop.address).filter { it.isNotBlank() }.joinToString(", ")
                            if (where.isNotEmpty()) {
                                Text(where, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HeartButton(
                            filled = true,
                            description = stringResource(Res.string.area_favourite_remove, shop.name),
                            tag = MarketplaceAreaTestTags.myShopsHeart(shop.key),
                            onClick = { onRemove(shop.key) },
                        )
                    }
                }
            }
        }
    }
}

private val PreviewShops = listOf(
    Merchant(key = "a", name = "Cafe Nero", customServices = listOf("Cafe"), address = "Hexham", gersId = "1"),
    Merchant(key = "b", name = "Dave's Veg Stall"),
    Merchant(key = "c", name = "Riverside Cafe", customServices = listOf("Cafe"), address = "Hexham", gersId = "3"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaMyShopsPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaMyShopsScreen(PreviewShops) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaMyShopsPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaMyShopsScreen(PreviewShops) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaMyShopsTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaMyShopsScreen(PreviewShops) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaMyShopsTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaMyShopsScreen(PreviewShops) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaMyShopsEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaMyShopsScreen(emptyList()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaMyShopsEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaMyShopsScreen(emptyList()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaMyShopsEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaMyShopsScreen(emptyList()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaMyShopsEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaMyShopsScreen(emptyList()) }
}
