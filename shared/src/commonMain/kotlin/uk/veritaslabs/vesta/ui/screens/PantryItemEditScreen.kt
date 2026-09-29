package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.SecondaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.pantry_edit_add_title
import vesta.shared.generated.resources.pantry_edit_barcode_label
import vesta.shared.generated.resources.pantry_edit_cancel
import vesta.shared.generated.resources.pantry_edit_delete
import vesta.shared.generated.resources.ic_dots_vertical
import vesta.shared.generated.resources.pantry_edit_edit_title
import vesta.shared.generated.resources.pantry_edit_more_options
import vesta.shared.generated.resources.pantry_edit_name_label
import vesta.shared.generated.resources.pantry_edit_package_size_label
import vesta.shared.generated.resources.pantry_edit_primary_category_label
import vesta.shared.generated.resources.pantry_edit_save
import vesta.shared.generated.resources.pantry_edit_secondary_category_label
import vesta.shared.generated.resources.pantry_edit_stock_count_label

private val PantryItemEditTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object PantryItemEditTestTags {
    const val NameField = "pantry_item_edit_name_field"
    const val PrimaryCategoryField = "pantry_item_edit_primary_category_field"
    const val SecondaryCategoryField = "pantry_item_edit_secondary_category_field"
    const val PackageSizeField = "pantry_item_edit_package_size_field"
    const val BarcodeField = "pantry_item_edit_barcode_field"
    const val StockCountField = "pantry_item_edit_stock_count_field"
    const val SaveButton = "pantry_item_edit_save_button"
    const val OverflowButton = "pantry_item_edit_overflow_button"
    const val DeleteMenuItem = "pantry_item_edit_delete_menu_item"
    const val CancelButton = "pantry_item_edit_cancel_button"
}

/**
 * The editable fields of a Pantry entry (ADR-008) — the UI-facing mirror of
 * `uk.veritaslabs.vesta.automerge.PantryItem`, minus `useCount`/`lastUsedAt`
 * (managed automatically, not user-editable). [key] is null while creating a
 * new item — its dedup key is derived from [barcode]/[title] on save — and
 * set to the existing dedup key while editing one.
 */
data class PantryItemDetails(
    val key: String? = null,
    val primaryCategory: String = "",
    val secondaryCategory: String? = null,
    val title: String = "",
    val packageSize: String = "",
    val barcode: String? = null,
    val stockCount: Int = 0,
)

/**
 * Create/update/delete form for a single Pantry entry (ADR-008). Pass
 * [item] to edit an existing entry, or leave it null to create a new one —
 * the Delete action only appears once [item] (and its [PantryItemDetails.key])
 * is present.
 *
 * UI only — the caller owns persistence: [onSave] receives the edited
 * [PantryItemDetails] as typed (trimmed, blank optional fields turned into
 * null), and it's up to the caller to upsert/correct stock/delete it via the
 * automerge document layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryItemEditScreen(
    modifier: Modifier = Modifier,
    item: PantryItemDetails? = null,
    onSave: (PantryItemDetails) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onCancel: () -> Unit = {},
) {
    var title by remember(item) { mutableStateOf(item?.title.orEmpty()) }
    var primaryCategory by remember(item) { mutableStateOf(item?.primaryCategory.orEmpty()) }
    var secondaryCategory by remember(item) { mutableStateOf(item?.secondaryCategory.orEmpty()) }
    var packageSize by remember(item) { mutableStateOf(item?.packageSize.orEmpty()) }
    var barcode by remember(item) { mutableStateOf(item?.barcode.orEmpty()) }
    var stockCountText by remember(item) { mutableStateOf((item?.stockCount ?: 0).toString()) }

    val isValid = title.isNotBlank() && primaryCategory.isNotBlank() && packageSize.isNotBlank()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= PantryItemEditTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Text(
                        text = if (item == null) {
                            stringResource(Res.string.pantry_edit_add_title)
                        } else {
                            stringResource(Res.string.pantry_edit_edit_title)
                        },
                    )
                },
                actions = {
                    item?.key?.let { key ->
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag(PantryItemEditTestTags.OverflowButton),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_dots_vertical),
                                    contentDescription = stringResource(Res.string.pantry_edit_more_options),
                                )
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.pantry_edit_delete)) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete(key)
                                    },
                                    modifier = Modifier.testTag(PantryItemEditTestTags.DeleteMenuItem),
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.pantry_edit_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag(PantryItemEditTestTags.NameField),
                )
                OutlinedTextField(
                    value = primaryCategory,
                    onValueChange = { primaryCategory = it },
                    label = { Text(stringResource(Res.string.pantry_edit_primary_category_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        .testTag(PantryItemEditTestTags.PrimaryCategoryField),
                )
                OutlinedTextField(
                    value = secondaryCategory,
                    onValueChange = { secondaryCategory = it },
                    label = { Text(stringResource(Res.string.pantry_edit_secondary_category_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        .testTag(PantryItemEditTestTags.SecondaryCategoryField),
                )
                OutlinedTextField(
                    value = packageSize,
                    onValueChange = { packageSize = it },
                    label = { Text(stringResource(Res.string.pantry_edit_package_size_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        .testTag(PantryItemEditTestTags.PackageSizeField),
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(stringResource(Res.string.pantry_edit_barcode_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag(PantryItemEditTestTags.BarcodeField),
                )
                OutlinedTextField(
                    value = stockCountText,
                    onValueChange = { new -> if (new.all { it.isDigit() }) stockCountText = new },
                    label = { Text(stringResource(Res.string.pantry_edit_stock_count_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        .testTag(PantryItemEditTestTags.StockCountField),
                )

                SecondaryButton(
                    label = stringResource(Res.string.pantry_edit_cancel),
                    onClick = onCancel,
                    modifier = Modifier.padding(top = 24.dp).testTag(PantryItemEditTestTags.CancelButton),
                )
                PrimaryButton(
                    label = stringResource(Res.string.pantry_edit_save),
                    enabled = isValid,
                    onClick = {
                        onSave(
                            PantryItemDetails(
                                key = item?.key,
                                primaryCategory = primaryCategory.trim(),
                                secondaryCategory = secondaryCategory.trim().ifBlank { null },
                                title = title.trim(),
                                packageSize = packageSize.trim(),
                                barcode = barcode.trim().ifBlank { null },
                                stockCount = stockCountText.toIntOrNull() ?: 0,
                            ),
                        )
                    },
                    modifier = Modifier.padding(top = 16.dp).testTag(PantryItemEditTestTags.SaveButton),
                )
            }
        }
    }
}

private val PantryItemEditPreviewItem = PantryItemDetails(
    key = "5000157024671",
    primaryCategory = "Condiments",
    secondaryCategory = "Ketchup",
    title = "Heinz Ketchup",
    packageSize = "500ml",
    barcode = "5000157024671",
    stockCount = 4,
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditScreenAddPhoneLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditScreenAddPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditScreenAddTabletLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditScreenAddTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditScreen() }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditScreenEditPhoneLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditScreen(item = PantryItemEditPreviewItem) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditScreenEditPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditScreen(item = PantryItemEditPreviewItem) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditScreenEditTabletLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditScreen(item = PantryItemEditPreviewItem) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditScreenEditTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditScreen(item = PantryItemEditPreviewItem) }
}
