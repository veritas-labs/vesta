package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_down
import vesta.shared.generated.resources.ic_arrow_up
import vesta.shared.generated.resources.ic_building_store
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.pantry_buy_from_add
import vesta.shared.generated.resources.pantry_buy_from_label
import vesta.shared.generated.resources.pantry_buy_from_move_down
import vesta.shared.generated.resources.pantry_buy_from_move_up
import vesta.shared.generated.resources.pantry_buy_from_none
import vesta.shared.generated.resources.pantry_buy_from_remove
import vesta.shared.generated.resources.pantry_merchants_apply
import vesta.shared.generated.resources.pantry_merchants_cancel
import vesta.shared.generated.resources.pantry_merchants_dialog_title
import vesta.shared.generated.resources.pantry_merchants_mode_add
import vesta.shared.generated.resources.pantry_merchants_mode_add_hint
import vesta.shared.generated.resources.pantry_merchants_mode_remove
import vesta.shared.generated.resources.pantry_merchants_mode_remove_hint
import vesta.shared.generated.resources.pantry_merchants_mode_replace
import vesta.shared.generated.resources.pantry_merchants_mode_replace_hint

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MerchantLinkTestTags {
    const val Section = "merchant_links_section"
    const val None = "merchant_links_none"
    const val Dialog = "merchant_links_dialog"
    const val ModeAdd = "merchant_links_mode_add"
    const val ModeRemove = "merchant_links_mode_remove"
    const val ModeReplace = "merchant_links_mode_replace"
    const val Apply = "merchant_links_apply"
    const val Cancel = "merchant_links_cancel"

    /** The row of the linked merchant with [key]. */
    fun linked(key: String) = "merchant_links_linked_$key"
    fun moveUp(key: String) = "merchant_links_up_$key"
    fun moveDown(key: String) = "merchant_links_down_$key"
    fun remove(key: String) = "merchant_links_remove_$key"

    /** The chip that adds the merchant with [key]. */
    fun add(key: String) = "merchant_links_add_$key"
}

/**
 * Edits an ordered list of merchant [keys], first = highest priority: the merchants of [merchants] that are in it, each
 * with move-up, move-down and remove buttons, then a chip for each other merchant that adds it at the end. Keys no
 * merchant has (deleted ones) are kept in [keys] and passed through [onChange] but not shown. With no [merchants] at
 * all it says where to add some. [title] heads the section.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MerchantPriorityEditor(
    keys: List<String>,
    merchants: List<Merchant>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val known = merchants.map { it.key }.toSet()
    val linked = linkedMerchants(keys, merchants)
    val others = merchants.filter { it.key !in keys }
    Column(modifier = modifier.fillMaxWidth().testTag(MerchantLinkTestTags.Section)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp)) }
        if (merchants.isEmpty()) {
            Text(
                stringResource(Res.string.pantry_buy_from_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(MerchantLinkTestTags.None),
            )
            return@Column
        }
        linked.forEachIndexed { index, merchant ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().testTag(MerchantLinkTestTags.linked(merchant.key)),
            ) {
                Text("${index + 1}. ${merchant.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onChange(moveMerchantKey(keys, merchant.key, up = true) { it in known }) },
                    enabled = index > 0,
                    modifier = Modifier.size(40.dp).testTag(MerchantLinkTestTags.moveUp(merchant.key)),
                ) {
                    Icon(painterResource(Res.drawable.ic_arrow_up), stringResource(Res.string.pantry_buy_from_move_up, merchant.name))
                }
                IconButton(
                    onClick = { onChange(moveMerchantKey(keys, merchant.key, up = false) { it in known }) },
                    enabled = index < linked.lastIndex,
                    modifier = Modifier.size(40.dp).testTag(MerchantLinkTestTags.moveDown(merchant.key)),
                ) {
                    Icon(painterResource(Res.drawable.ic_arrow_down), stringResource(Res.string.pantry_buy_from_move_down, merchant.name))
                }
                IconButton(
                    onClick = { onChange(keys.filter { it != merchant.key }) },
                    modifier = Modifier.size(40.dp).testTag(MerchantLinkTestTags.remove(merchant.key)),
                ) {
                    Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.pantry_buy_from_remove, merchant.name))
                }
            }
        }
        if (others.isNotEmpty()) {
            Text(
                stringResource(Res.string.pantry_buy_from_add),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { merchant ->
                    SuggestionChip(
                        onClick = { onChange(normalizeMerchantKeys(keys + merchant.key)) },
                        label = { Text(merchant.name) },
                        modifier = Modifier.testTag(MerchantLinkTestTags.add(merchant.key)),
                    )
                }
            }
        }
    }
}

private enum class LinkMode { Add, Remove, Replace }

/**
 * The batch update of merchants for [itemCount] selected Pantry items: choose Add (appended at the lowest priority),
 * Remove or Replace (exactly this ordered list), pick the merchants with [MerchantPriorityEditor], and Apply calls
 * [onApply] with the matching [MerchantLinkChange]. Add and Remove need at least one merchant; Replace may be empty,
 * which clears the items' merchants.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MerchantLinkDialog(
    itemCount: Int,
    merchants: List<Merchant>,
    onApply: (MerchantLinkChange) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(LinkMode.Add) }
    var keys by remember { mutableStateOf(emptyList<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(MerchantLinkTestTags.Dialog),
        title = { Text(stringResource(Res.string.pantry_merchants_dialog_title, itemCount)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(mode == LinkMode.Add, stringResource(Res.string.pantry_merchants_mode_add), MerchantLinkTestTags.ModeAdd) { mode = LinkMode.Add }
                    ModeChip(mode == LinkMode.Remove, stringResource(Res.string.pantry_merchants_mode_remove), MerchantLinkTestTags.ModeRemove) { mode = LinkMode.Remove }
                    ModeChip(mode == LinkMode.Replace, stringResource(Res.string.pantry_merchants_mode_replace), MerchantLinkTestTags.ModeReplace) { mode = LinkMode.Replace }
                }
                Text(
                    stringResource(
                        when (mode) {
                            LinkMode.Add -> Res.string.pantry_merchants_mode_add_hint
                            LinkMode.Remove -> Res.string.pantry_merchants_mode_remove_hint
                            LinkMode.Replace -> Res.string.pantry_merchants_mode_replace_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                MerchantPriorityEditor(keys, merchants, onChange = { keys = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        when (mode) {
                            LinkMode.Add -> MerchantLinkChange.Add(keys)
                            LinkMode.Remove -> MerchantLinkChange.Remove(keys.toSet())
                            LinkMode.Replace -> MerchantLinkChange.Replace(keys)
                        },
                    )
                },
                enabled = keys.isNotEmpty() || mode == LinkMode.Replace,
                modifier = Modifier.testTag(MerchantLinkTestTags.Apply),
            ) { Text(stringResource(Res.string.pantry_merchants_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(MerchantLinkTestTags.Cancel)) {
                Text(stringResource(Res.string.pantry_merchants_cancel))
            }
        },
    )
}

@Composable
private fun ModeChip(selected: Boolean, label: String, tag: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.testTag(tag))
}

internal val MerchantLinkPreviewMerchants = listOf(
    Merchant(key = "butcher", name = "Hargreaves & Son"),
    Merchant(key = "tesco", name = "Tesco Extra"),
    Merchant(key = "stall", name = "Dave's Veg Stall"),
)

@Composable
private fun EditorPreview(dark: Boolean) {
    VestaTheme(darkTheme = dark) {
        MerchantPriorityEditor(
            keys = listOf("tesco", "butcher"),
            merchants = MerchantLinkPreviewMerchants,
            onChange = {},
            title = stringResource(Res.string.pantry_buy_from_label),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantPriorityEditorPhoneLightPreview() = EditorPreview(dark = false)

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantPriorityEditorPhoneDarkPreview() = EditorPreview(dark = true)

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantPriorityEditorTabletLightPreview() = EditorPreview(dark = false)

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantPriorityEditorTabletDarkPreview() = EditorPreview(dark = true)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantLinkDialogPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MerchantLinkDialog(3, MerchantLinkPreviewMerchants, {}, {}) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantLinkDialogPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantLinkDialog(3, MerchantLinkPreviewMerchants, {}, {}) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantLinkDialogTabletLightPreview() {
    VestaTheme(darkTheme = false) { MerchantLinkDialog(3, MerchantLinkPreviewMerchants, {}, {}) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantLinkDialogTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantLinkDialog(3, MerchantLinkPreviewMerchants, {}, {}) }
}

/** A row's "where to buy it" line: a shopfront icon, then the merchants' [names] (highest priority first); [textModifier] is for the names. */
@Composable
internal fun BuyFromLine(names: List<String>, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            painter = painterResource(Res.drawable.ic_building_store),
            contentDescription = stringResource(Res.string.pantry_buy_from_label),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = names.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = textModifier,
        )
    }
}
