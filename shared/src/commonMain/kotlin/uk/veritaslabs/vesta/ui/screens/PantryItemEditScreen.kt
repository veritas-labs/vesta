package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.BarcodeCameraView
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.isBarcodeScanningSupported
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.pantry_edit_add_title
import vesta.shared.generated.resources.pantry_edit_barcode_label
import vesta.shared.generated.resources.pantry_edit_barcode_scan
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_scan
import vesta.shared.generated.resources.ic_minus
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_tag
import vesta.shared.generated.resources.pantry_stock_decrease
import vesta.shared.generated.resources.pantry_stock_increase
import vesta.shared.generated.resources.pantry_back
import vesta.shared.generated.resources.pantry_edit_delete
import vesta.shared.generated.resources.ic_dots_vertical
import vesta.shared.generated.resources.pantry_edit_edit_categories
import vesta.shared.generated.resources.pantry_edit_edit_title
import vesta.shared.generated.resources.pantry_edit_no_subcategory
import vesta.shared.generated.resources.pantry_edit_more_options
import vesta.shared.generated.resources.pantry_edit_name_label
import vesta.shared.generated.resources.pantry_edit_package_loose
import vesta.shared.generated.resources.pantry_edit_package_size_label
import vesta.shared.generated.resources.pantry_edit_primary_category_label
import vesta.shared.generated.resources.pantry_edit_save
import vesta.shared.generated.resources.pantry_edit_secondary_category_label
import vesta.shared.generated.resources.pantry_edit_stock_count_label
import vesta.shared.generated.resources.shopping_edit_add_title
import vesta.shared.generated.resources.shopping_edit_edit_title
import vesta.shared.generated.resources.shopping_edit_quantity_label
import vesta.shared.generated.resources.shopping_quantity_decrease
import vesta.shared.generated.resources.shopping_quantity_increase
import vesta.shared.generated.resources.shopping_tag_remove
import vesta.shared.generated.resources.shopping_tags_add
import vesta.shared.generated.resources.shopping_tags_hint
import vesta.shared.generated.resources.shopping_tags_label
import vesta.shared.generated.resources.ic_close

private val PantryItemEditTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object PantryItemEditTestTags {
    const val NameField = "pantry_item_edit_name_field"
    const val PrimaryCategoryField = "pantry_item_edit_primary_category_field"
    const val SecondaryCategoryField = "pantry_item_edit_secondary_category_field"
    const val PackageSizeField = "pantry_item_edit_package_size_field"
    const val BarcodeField = "pantry_item_edit_barcode_field"
    const val BarcodeScanButton = "pantry_item_edit_barcode_scan_button"
    const val StockCountField = "pantry_item_edit_stock_count_field"
    const val StockDecreaseButton = "pantry_item_edit_stock_decrease_button"
    const val StockIncreaseButton = "pantry_item_edit_stock_increase_button"
    const val SaveButton = "pantry_item_edit_save_button"
    const val OverflowButton = "pantry_item_edit_overflow_button"
    const val DeleteMenuItem = "pantry_item_edit_delete_menu_item"
    const val BackButton = "pantry_item_edit_back_button"
    const val EditCategoriesMenuItem = "pantry_item_edit_edit_categories_menu_item"
    const val NoSecondaryOption = "pantry_item_edit_no_secondary_option"
    const val TagField = "pantry_item_edit_tag_field"
    const val TagAddButton = "pantry_item_edit_tag_add_button"

    /** Tag for the chip of the item's tag [name] (tapping it removes the tag). */
    fun tag(name: String) = "pantry_item_edit_tag_$name"

    /** Tag for the chip offering the known tag [name] (tapping it adds the tag). */
    fun tagSuggestion(name: String) = "pantry_item_edit_tag_suggestion_$name"

    /** Tag for the dropdown option with the given category [name], in whichever category dropdown is open. */
    fun categoryOption(name: String) = "pantry_item_edit_category_option_$name"

    /** Tag for the name suggestion for the Pantry entry with the given [key]. */
    fun suggestion(key: String) = "pantry_item_edit_suggestion_$key"
}

/** The most name suggestions the item form shows at once. */
internal const val MaxSuggestions = 5

/**
 * The [candidates] to suggest for what's been typed in the name field: those whose title contains [typed] (ignoring
 * case), titles that start with it first, then the order of [candidates]. None once [typed] is blank or is already
 * exactly one candidate's title (it has been picked).
 */
internal fun suggestionsFor(typed: String, candidates: List<PantryListItem>): List<PantryListItem> {
    val query = typed.trim()
    if (query.isEmpty() || candidates.any { it.title.equals(query, ignoreCase = true) }) return emptyList()
    return candidates
        .filter { it.title.contains(query, ignoreCase = true) }
        .sortedByDescending { it.title.startsWith(query, ignoreCase = true) }
        .take(MaxSuggestions)
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
    val tags: List<String> = emptyList(),
)

/**
 * The wording of [PantryItemEditScreen]'s title and its count stepper, so the one form can edit a Pantry entry's
 * stock count (the default) or a shopping-list item's quantity ([ShoppingItemFormLabels], never below [minCount]).
 */
data class ItemFormLabels(
    val addTitle: StringResource,
    val editTitle: StringResource,
    val countLabel: StringResource,
    val decreaseDescription: StringResource,
    val increaseDescription: StringResource,
    val minCount: Int = 0,
)

/** [PantryItemEditScreen]'s wording for a Pantry entry's stock count. */
val PantryItemFormLabels = ItemFormLabels(
    Res.string.pantry_edit_add_title,
    Res.string.pantry_edit_edit_title,
    Res.string.pantry_edit_stock_count_label,
    Res.string.pantry_stock_decrease,
    Res.string.pantry_stock_increase,
)

/** [PantryItemEditScreen]'s wording for a shopping-list item's quantity. */
val ShoppingItemFormLabels = ItemFormLabels(
    Res.string.shopping_edit_add_title,
    Res.string.shopping_edit_edit_title,
    Res.string.shopping_edit_quantity_label,
    Res.string.shopping_quantity_decrease,
    Res.string.shopping_quantity_increase,
    minCount = 1,
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
 *
 * The category fields are dropdowns over [categories] (the Home's registered ones); the secondary one lists the
 * chosen primary's subcategories. Both end with an "Edit categories" entry that calls [onEditCategories] with the
 * form as typed so far, so the caller can bring the user back to it (as [item]) afterwards. A category the item
 * already has that isn't registered any more stays selectable.
 *
 * The package size is a read-only row that calls [onEditPackage] with the form as typed so far (like
 * [onEditCategories]); the caller shows [PantryItemEditPackageScreen] and returns here with the new size in [item].
 * A blank size means a loose item, with no packaging.
 *
 * While adding an item (no [PantryItemDetails.key]), typing in the name field lists the [suggestions] whose title
 * contains what's typed (best matches first, at most [MaxSuggestions]); picking one fills in its name, categories,
 * package size and barcode.
 *
 * When [knownTags] is given (the shopping-list form) the form also edits the item's tags, offering [knownTags] as
 * one-tap additions.
 *
 * Next to the barcode field a scan icon button (shown when [scanSupported]) opens a full-screen [BarcodeScannerScreen] over
 * the form, which keeps what's been typed; the scanned code fills the barcode field. [barcodeCamera] is the preview
 * the scanner shows, a parameter so tests can stand in for real hardware.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryItemEditScreen(
    modifier: Modifier = Modifier,
    item: PantryItemDetails? = null,
    categories: List<PantryCategoryGroup> = emptyList(),
    onSave: (PantryItemDetails) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onCancel: () -> Unit = {},
    onEditCategories: (PantryItemDetails) -> Unit = {},
    onEditPackage: (PantryItemDetails) -> Unit = {},
    labels: ItemFormLabels = PantryItemFormLabels,
    suggestions: List<PantryListItem> = emptyList(),
    knownTags: List<String>? = null,
    scanSupported: Boolean = isBarcodeScanningSupported,
    barcodeCamera: @Composable (onBarcode: (String) -> Unit, modifier: Modifier) -> Unit = { onBarcode, cameraModifier ->
        BarcodeCameraView(onBarcode, cameraModifier)
    },
) {
    var title by remember(item) { mutableStateOf(item?.title.orEmpty()) }
    var primaryCategory by remember(item) { mutableStateOf(item?.primaryCategory.orEmpty()) }
    var secondaryCategory by remember(item) { mutableStateOf(item?.secondaryCategory.orEmpty()) }
    var packageSize by remember(item) { mutableStateOf(item?.packageSize.orEmpty()) }
    var barcode by remember(item) { mutableStateOf(item?.barcode.orEmpty()) }
    var stockCount by remember(item) { mutableIntStateOf(item?.stockCount ?: labels.minCount) }
    var tags by remember(item) { mutableStateOf(item?.tags.orEmpty()) }
    var scanning by remember { mutableStateOf(false) }

    val details = {
        PantryItemDetails(
            key = item?.key,
            primaryCategory = primaryCategory.trim(),
            secondaryCategory = secondaryCategory.trim().ifBlank { null },
            title = title.trim(),
            packageSize = packageSize.trim(),
            barcode = barcode.trim().ifBlank { null },
            stockCount = stockCount,
            tags = tags,
        )
    }
    val primaryOptions = (categories.map { it.name } + primaryCategory).filter { it.isNotBlank() }.distinct()
    val secondaryOptions = (categories.firstOrNull { it.name == primaryCategory }?.secondary.orEmpty() + secondaryCategory)
        .filter { it.isNotBlank() }.distinct()

    val isValid = title.isNotBlank() && primaryCategory.isNotBlank()

    if (scanning) {
        BarcodeScannerScreen(
            onScanned = {
                barcode = it
                scanning = false
            },
            onBack = { scanning = false },
            modifier = modifier,
            camera = barcodeCamera,
        )
        return
    }

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
                        text = stringResource(if (item?.key == null) labels.addTitle else labels.editTitle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag(PantryItemEditTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.pantry_back),
                        )
                    }
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
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
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
                val matches = if (item?.key == null) suggestionsFor(title, suggestions) else emptyList()
                if (matches.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MaterialTheme.shapes.small),
                    ) {
                        matches.forEach { match ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        title = match.title
                                        primaryCategory = match.primaryCategory
                                        secondaryCategory = match.secondaryCategory.orEmpty()
                                        packageSize = match.packageSize
                                        barcode = match.barcode.orEmpty()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag(PantryItemEditTestTags.suggestion(match.key)),
                            ) {
                                Text(match.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = listOfNotNull(match.primaryCategory, match.secondaryCategory, match.packageSize.ifBlank { null })
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                CategoryDropdown(
                    label = stringResource(Res.string.pantry_edit_primary_category_label),
                    value = primaryCategory,
                    options = primaryOptions,
                    onSelect = {
                        if (it != primaryCategory) secondaryCategory = ""
                        primaryCategory = it
                    },
                    onEditCategories = { onEditCategories(details()) },
                    modifier = Modifier.padding(top = 16.dp),
                    fieldTag = PantryItemEditTestTags.PrimaryCategoryField,
                )
                CategoryDropdown(
                    label = stringResource(Res.string.pantry_edit_secondary_category_label),
                    value = secondaryCategory,
                    options = secondaryOptions,
                    onSelect = { secondaryCategory = it },
                    onEditCategories = { onEditCategories(details()) },
                    modifier = Modifier.padding(top = 16.dp),
                    fieldTag = PantryItemEditTestTags.SecondaryCategoryField,
                    enabled = primaryCategory.isNotBlank(),
                    clearLabel = stringResource(Res.string.pantry_edit_no_subcategory),
                    onClear = { secondaryCategory = "" },
                )
                Box(modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedTextField(
                        value = packageSize.ifBlank { stringResource(Res.string.pantry_edit_package_loose) },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.pantry_edit_package_size_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Covers the field so a tap anywhere on it (not just the text) opens the package screen.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { onEditPackage(details()) }
                            .testTag(PantryItemEditTestTags.PackageSizeField),
                    )
                }
                // Bottom-aligned, not centred: the field's label overhangs its 56dp border box, which would throw
                // centring off. The button is then inset to sit in the middle of that box.
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text(stringResource(Res.string.pantry_edit_barcode_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag(PantryItemEditTestTags.BarcodeField),
                    )
                    if (scanSupported) {
                        Spacer(Modifier.width(12.dp))
                        IconButton(
                            onClick = { scanning = true },
                            modifier = Modifier.padding(bottom = 6.dp).size(44.dp).testTag(PantryItemEditTestTags.BarcodeScanButton),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_scan),
                                contentDescription = stringResource(Res.string.pantry_edit_barcode_scan),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text(
                        text = stringResource(labels.countLabel),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { stockCount-- },
                        enabled = stockCount > labels.minCount,
                        modifier = Modifier.testTag(PantryItemEditTestTags.StockDecreaseButton),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_minus),
                            contentDescription = stringResource(labels.decreaseDescription),
                        )
                    }
                    Text(
                        text = stockCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 40.dp).testTag(PantryItemEditTestTags.StockCountField),
                    )
                    IconButton(
                        onClick = { stockCount++ },
                        modifier = Modifier.testTag(PantryItemEditTestTags.StockIncreaseButton),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_plus),
                            contentDescription = stringResource(labels.increaseDescription),
                        )
                    }
                }

                knownTags?.let {
                    TagsEditor(
                        tags = tags,
                        knownTags = it,
                        onTagsChange = { tags = it },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                PrimaryButton(
                    label = stringResource(Res.string.pantry_edit_save),
                    enabled = isValid,
                    onClick = { onSave(details()) },
                    modifier = Modifier.padding(top = 24.dp).testTag(PantryItemEditTestTags.SaveButton),
                )
            }
        }
    }
}

/**
 * Edits an item's [tags]: its current ones as chips (tap to remove), a field to type a new one, and the [knownTags]
 * it doesn't have yet as chips (tap to add). Tags are compared ignoring case.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    tags: List<String>,
    knownTags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by remember { mutableStateOf("") }
    val add = { tag: String ->
        val clean = tag.trim()
        if (clean.isNotEmpty() && tags.none { it.equals(clean, ignoreCase = true) }) onTagsChange(tags + clean)
        typed = ""
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(Res.string.shopping_tags_label)) },
                placeholder = { Text(stringResource(Res.string.shopping_tags_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(typed) }),
                modifier = Modifier.weight(1f).testTag(PantryItemEditTestTags.TagField),
            )
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { add(typed) },
                enabled = typed.isNotBlank(),
                modifier = Modifier.padding(bottom = 6.dp).size(44.dp).testTag(PantryItemEditTestTags.TagAddButton),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_plus),
                    contentDescription = stringResource(Res.string.shopping_tags_add),
                )
            }
        }
        val offered = knownTags.filter { known -> tags.none { it.equals(known, ignoreCase = true) } }
        if (tags.isNotEmpty() || offered.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onTagsChange(tags - tag) },
                        label = { Text(tag) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_tag),
                                contentDescription = null,
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        trailingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = stringResource(Res.string.shopping_tag_remove, tag),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.testTag(PantryItemEditTestTags.tag(tag)),
                    )
                }
                offered.forEach { tag ->
                    SuggestionChip(
                        onClick = { add(tag) },
                        label = { Text(tag) },
                        icon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_tag),
                                contentDescription = null,
                                modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.testTag(PantryItemEditTestTags.tagSuggestion(tag)),
                    )
                }
            }
        }
    }
}

/**
 * A read-only field that opens a menu of [options] (selecting one calls [onSelect]), plus — when [clearLabel] is
 * given — an entry that calls [onClear], and always an "Edit categories" entry that calls [onEditCategories].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onEditCategories: () -> Unit,
    fieldTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    clearLabel: String? = null,
    onClear: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(fieldTag),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            clearLabel?.let {
                DropdownMenuItem(
                    text = { Text(it) },
                    onClick = {
                        expanded = false
                        onClear()
                    },
                    modifier = Modifier.testTag(PantryItemEditTestTags.NoSecondaryOption),
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag(PantryItemEditTestTags.categoryOption(option)),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.pantry_edit_edit_categories)) },
                onClick = {
                    expanded = false
                    onEditCategories()
                },
                modifier = Modifier.testTag(PantryItemEditTestTags.EditCategoriesMenuItem),
            )
        }
    }
}

private val PantryItemEditPreviewCategories = listOf(
    PantryCategoryGroup("Condiments", listOf("Brown Sauce", "Ketchup", "Mayonnaise")),
    PantryCategoryGroup("Dairy", listOf("Cheese", "Milk")),
)

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
    VestaTheme(darkTheme = false) { PantryItemEditScreen(categories = PantryItemEditPreviewCategories) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditScreenAddPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditScreen(categories = PantryItemEditPreviewCategories) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditScreenAddTabletLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditScreen(categories = PantryItemEditPreviewCategories) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditScreenAddTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditScreen(categories = PantryItemEditPreviewCategories) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditScreenEditPhoneLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditScreen(item = PantryItemEditPreviewItem, categories = PantryItemEditPreviewCategories) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditScreenEditPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditScreen(item = PantryItemEditPreviewItem, categories = PantryItemEditPreviewCategories) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditScreenEditTabletLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditScreen(item = PantryItemEditPreviewItem, categories = PantryItemEditPreviewCategories) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditScreenEditTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditScreen(item = PantryItemEditPreviewItem, categories = PantryItemEditPreviewCategories) }
}
