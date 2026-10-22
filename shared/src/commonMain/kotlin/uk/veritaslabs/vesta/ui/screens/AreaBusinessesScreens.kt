package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.location.BusinessGroupCount
import uk.veritaslabs.vesta.location.BusinessRow
import uk.veritaslabs.vesta.location.BusinessSection
import uk.veritaslabs.vesta.location.CategoryCount
import uk.veritaslabs.vesta.location.NearbyBusinesses
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.SecondaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_places_active_area
import vesta.shared.generated.resources.area_places_area_with_nearby
import vesta.shared.generated.resources.area_places_categories
import vesta.shared.generated.resources.area_places_my_shops_count
import vesta.shared.generated.resources.area_places_directions
import vesta.shared.generated.resources.area_saved_change
import vesta.shared.generated.resources.ic_search
import vesta.shared.generated.resources.ic_meal_dinner
import vesta.shared.generated.resources.ic_shopping_cart
import vesta.shared.generated.resources.ic_building_store
import vesta.shared.generated.resources.ic_home_heart
import vesta.shared.generated.resources.ic_map_pin
import vesta.shared.generated.resources.ic_heart
import vesta.shared.generated.resources.ic_clipboard_text
import vesta.shared.generated.resources.ic_buildings
import vesta.shared.generated.resources.ic_home
import vesta.shared.generated.resources.ic_photo
import vesta.shared.generated.resources.ic_calendar
import vesta.shared.generated.resources.ic_tag
import vesta.shared.generated.resources.area_favourite_add
import vesta.shared.generated.resources.area_favourite_remove
import vesta.shared.generated.resources.area_my_shops_row
import vesta.shared.generated.resources.area_places_cancel
import vesta.shared.generated.resources.area_places_confirm_body
import vesta.shared.generated.resources.area_places_confirm_title
import vesta.shared.generated.resources.area_places_download
import vesta.shared.generated.resources.area_places_all
import vesta.shared.generated.resources.area_places_all_in_group
import vesta.shared.generated.resources.area_places_browse_search_label
import vesta.shared.generated.resources.area_places_category_search_label
import vesta.shared.generated.resources.area_places_group_other
import vesta.shared.generated.resources.area_places_no_category
import vesta.shared.generated.resources.area_places_search_all
import vesta.shared.generated.resources.area_places_header
import vesta.shared.generated.resources.area_places_loading
import vesta.shared.generated.resources.area_places_no_match
import vesta.shared.generated.resources.area_places_not_now
import vesta.shared.generated.resources.area_places_partial
import vesta.shared.generated.resources.area_places_progress
import vesta.shared.generated.resources.area_places_row_locality
import vesta.shared.generated.resources.area_places_search_label
import vesta.shared.generated.resources.area_places_show_more
import vesta.shared.generated.resources.area_places_summary_inside
import vesta.shared.generated.resources.area_places_summary_nearby
import vesta.shared.generated.resources.area_places_summary_none
import vesta.shared.generated.resources.area_places_summary_total
import vesta.shared.generated.resources.area_places_summary_with_nearby
import vesta.shared.generated.resources.area_places_title
import vesta.shared.generated.resources.area_places_toggle_nearby
import vesta.shared.generated.resources.area_places_try_again
import vesta.shared.generated.resources.area_places_unreachable_title

/**
 * What the area holds, from the places directory alone (nothing downloaded yet): the [state] total, the inside and
 * just-outside counts with their download sizes, and the switch for including places just outside. Download
 * ([onDownload]) fetches, after the size confirmation ([onConfirm] / [onNotNow]) when the download is over 1 MiB.
 */
@Composable
fun AreaBusinessesSummaryScreen(
    state: AreaBusinessesState,
    modifier: Modifier = Modifier,
    onToggleNearby: (Boolean) -> Unit = {},
    onDownload: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onNotNow: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_places_title), onBack, modifier) {
        AreaScrollColumn(Modifier.testTag(MarketplaceAreaTestTags.BusinessesSummary)) {
            if (state.inside + state.nearby == 0) {
                Text(
                    stringResource(Res.string.area_places_summary_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesNone),
                )
                return@AreaScrollColumn
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val total = state.inside + state.nearby
                    Text(
                        pluralStringResource(Res.plurals.area_places_summary_total, total, groupedNumber(total)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesTotal),
                    )
                    Text(
                        pluralStringResource(
                            Res.plurals.area_places_summary_inside,
                            state.inside,
                            groupedNumber(state.inside),
                            megabytesText(state.bytesInsideOnly),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesInside),
                    )
                    Text(
                        pluralStringResource(Res.plurals.area_places_summary_nearby, state.nearby, groupedNumber(state.nearby)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesNearby),
                    )
                    Text(
                        stringResource(Res.string.area_places_summary_with_nearby, megabytesText(state.bytesWithNearby)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesBoth),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = state.includeNearby, role = Role.Switch, onValueChange = onToggleNearby)
                    .testTag(MarketplaceAreaTestTags.BusinessesToggleNearby),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Switch(checked = state.includeNearby, onCheckedChange = null)
                Text(stringResource(Res.string.area_places_toggle_nearby), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
            PrimaryButton(
                stringResource(Res.string.area_places_download),
                Modifier.testTag(MarketplaceAreaTestTags.BusinessesDownload),
                onClick = onDownload,
            )
        }
    }
    if (state.confirming) {
        AlertDialog(
            onDismissRequest = onNotNow,
            title = { Text(stringResource(Res.string.area_places_confirm_title)) },
            text = { Text(stringResource(Res.string.area_places_confirm_body, megabytesText(state.chosenBytes))) },
            confirmButton = {
                TextButton(onClick = onConfirm, modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesConfirmDownload)) {
                    Text(stringResource(Res.string.area_places_download))
                }
            },
            dismissButton = {
                TextButton(onClick = onNotNow, modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesConfirmNotNow)) {
                    Text(stringResource(Res.string.area_places_not_now))
                }
            },
            modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesConfirmDialog),
        )
    }
}

/** Fetching, with parts done so far ([state]) and Cancel ([onCancel]), which keeps what was already downloaded. */
@Composable
fun AreaBusinessesLoadingScreen(
    state: AreaBusinessesState,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_places_title), onBack, modifier) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).testTag(MarketplaceAreaTestTags.BusinessesLoading),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(stringResource(Res.string.area_places_loading), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.area_places_progress, state.chunksDone, state.chunksTotal, megabytesText(state.bytesFetched)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesProgress),
            )
            SecondaryButton(
                stringResource(Res.string.area_places_cancel),
                Modifier.testTag(MarketplaceAreaTestTags.BusinessesCancel),
                onClick = onCancel,
            )
        }
    }
}

/** Nothing could be loaded: says so and offers Try again ([onTryAgain]). */
@Composable
fun AreaBusinessesUnreachableScreen(
    modifier: Modifier = Modifier,
    onTryAgain: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_places_title), onBack, modifier) {
        AreaScrollColumn {
            Text(
                stringResource(Res.string.area_places_unreachable_title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesUnreachable),
            )
            PrimaryButton(
                stringResource(Res.string.area_places_try_again),
                Modifier.testTag(MarketplaceAreaTestTags.BusinessesTryAgain),
                onClick = onTryAgain,
            )
        }
    }
}

/** The banner for [AreaBusinessesState.partial] with Try again ([onRetry]). */
@Composable
private fun PartialBanner(onRetry: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.area_places_partial),
                Modifier.weight(1f).testTag(MarketplaceAreaTestTags.BusinessesPartial),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry, modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesPartialRetry)) {
                Text(stringResource(Res.string.area_places_try_again))
            }
        }
    }
}

/** A card container for one list row (ADR-039 WP-M5): rounded, surfaceVariant. */
@Composable
internal fun BusinessCardRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** One tappable browse row: a [label] and its [count], in a card. */
@Composable
private fun BrowseRow(label: String, count: Int, tag: String, onClick: () -> Unit, bold: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(tag),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                label,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold) FontWeight.SemiBold else null,
            )
            Text(groupedNumber(count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A rounded search field with a leading search icon. */
@Composable
private fun RoundedSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}

/** The icon for a top-level business group, from the existing set; a tag for any group without a closer match. */
private fun groupIcon(key: String): DrawableResource = when (key) {
    "food_and_drink" -> Res.drawable.ic_meal_dinner
    "shopping" -> Res.drawable.ic_shopping_cart
    "services_and_business" -> Res.drawable.ic_building_store
    "lifestyle_services" -> Res.drawable.ic_home_heart
    "travel_and_transportation", "geographic_entities" -> Res.drawable.ic_map_pin
    "health_care" -> Res.drawable.ic_heart
    "education" -> Res.drawable.ic_clipboard_text
    "community_and_government" -> Res.drawable.ic_buildings
    "lodging" -> Res.drawable.ic_home
    "arts_and_entertainment" -> Res.drawable.ic_photo
    "cultural_and_historic" -> Res.drawable.ic_calendar
    else -> Res.drawable.ic_tag
}

/** A category tile: [icon] in a tinted square, the [label] and a [detail] line. */
@Composable
private fun CategoryTile(icon: DrawableResource, label: String, detail: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(tag),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The area summary at the top of the browse: "ACTIVE AREA", the [areaName] with its nearby places, the [total] and Change. */
@Composable
private fun AreaSummaryCard(areaName: String, nearbyCount: Int, total: Int, onChange: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().testTag(MarketplaceAreaTestTags.BusinessesAreaCard),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.area_places_active_area),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (nearbyCount > 0) pluralStringResource(Res.plurals.area_places_area_with_nearby, nearbyCount, areaName, nearbyCount) else areaName,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    pluralStringResource(Res.plurals.area_places_header, total, groupedNumber(total)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onChange, modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesChangeArea)) {
                Text(stringResource(Res.string.area_saved_change))
            }
        }
    }
}

/**
 * Level one of the browse ([state]): the area card ([areaName], [nearbyCount], and Change through [onChangeArea]), a search
 * field ([onQueryChange]; the keyboard's search action or the "Search all businesses for" row calls [onSearchAll]) and a grid
 * of tiles: My Shops ([myShopsCount], [onMyShops]), "All businesses" ([onAll]) and the groups with counts ([onGroup]), the
 * unknown group last as "Other". [AreaBusinessesState.partial] adds a banner with Try again ([onRetry]).
 */
@Composable
fun AreaBusinessGroupsScreen(
    state: AreaBusinessesState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onSearchAll: () -> Unit = {},
    onAll: () -> Unit = {},
    onGroup: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
    myShopsCount: Int? = null,
    onMyShops: () -> Unit = {},
    areaName: String? = null,
    nearbyCount: Int = 0,
    onChangeArea: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_places_title), onBack, modifier) {
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            Modifier.fillMaxSize().testTag(MarketplaceAreaTestTags.BusinessesGroups),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.partial) item(key = "partial", span = { GridItemSpan(maxLineSpan) }) { PartialBanner(onRetry) }
            areaName?.let { name ->
                item(key = "area", span = { GridItemSpan(maxLineSpan) }) { AreaSummaryCard(name, nearbyCount, state.loaded, onChangeArea) }
            }
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                RoundedSearchField(
                    value = state.browseQuery,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(Res.string.area_places_browse_search_label),
                    tag = MarketplaceAreaTestTags.BusinessesBrowseSearch,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (state.browseQuery.isNotBlank()) onSearchAll() }),
                )
            }
            if (state.browseQuery.isNotBlank()) {
                item(key = "search-all", span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = onSearchAll, modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesSearchAll)) {
                        Text(stringResource(Res.string.area_places_search_all, state.browseQuery.trim()))
                    }
                }
            }
            item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.area_places_categories), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Text(groupedNumber(state.groups.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            myShopsCount?.let { count ->
                item(key = "my-shops") {
                    CategoryTile(
                        Res.drawable.ic_heart,
                        stringResource(Res.string.area_my_shops_row),
                        pluralStringResource(Res.plurals.area_places_my_shops_count, count, count),
                        MarketplaceAreaTestTags.MyShopsRow,
                        onMyShops,
                    )
                }
            }
            item(key = "all") {
                CategoryTile(
                    Res.drawable.ic_building_store,
                    stringResource(Res.string.area_places_all),
                    pluralStringResource(Res.plurals.area_places_header, state.loaded, groupedNumber(state.loaded)),
                    MarketplaceAreaTestTags.BusinessesAll,
                    onAll,
                )
            }
            items(state.groups, key = { it.key }) { group ->
                CategoryTile(
                    groupIcon(group.key),
                    groupLabel(group),
                    pluralStringResource(Res.plurals.area_places_header, group.count, groupedNumber(group.count)),
                    MarketplaceAreaTestTags.businessesGroup(group.key),
                    { onGroup(group.key) },
                )
            }
            item(key = "missing", span = { GridItemSpan(maxLineSpan) }) { MissingBusinessNote(Modifier.padding(vertical = 4.dp)) }
        }
    }
}

/**
 * Level two of the browse ([state]): the open group's categories with counts ([onCategory]), a search field over their
 * names ([onQueryChange]) and an "All <group>" row ([onAllInGroup]) at the top.
 */
@Composable
fun AreaBusinessCategoriesScreen(
    state: AreaBusinessesState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onAllInGroup: () -> Unit = {},
    onCategory: (Int) -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(state.groupName, onBack, modifier) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).testTag(MarketplaceAreaTestTags.BusinessesCategories),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundedSearchField(
                value = state.categoryQuery,
                onValueChange = onQueryChange,
                placeholder = stringResource(Res.string.area_places_category_search_label),
                tag = MarketplaceAreaTestTags.BusinessesCategorySearch,
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item(key = "all") {
                    BrowseRow(
                        stringResource(Res.string.area_places_all_in_group, state.groupName),
                        state.groupCount,
                        MarketplaceAreaTestTags.BusinessesAllInGroup,
                        onAllInGroup,
                        bold = true,
                    )
                }
                items(state.categories, key = { it.index }) { category ->
                    BrowseRow(category.name, category.count, MarketplaceAreaTestTags.businessesCategory(category.index), { onCategory(category.index) })
                }
                if (state.categories.isEmpty()) {
                    item(key = "none") {
                        Text(
                            stringResource(Res.string.area_places_no_category),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 12.dp).testTag(MarketplaceAreaTestTags.BusinessesCategoriesNoMatch),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The businesses of the chosen category, group or all ([state]): a header with that name and the count, a search field,
 * rows (name, then category and locality; never a distance and never "in <place>") A to Z under a header for each locality,
 * Show more and an empty-results line. [AreaBusinessesState.partial] adds a banner with Try again ([onRetry]).
 * Each row has a heart for My Shops while [favouriteIds] is not null (filled when the row's id is in it);.
 */
@Composable
fun AreaBusinessesListScreen(
    state: AreaBusinessesState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onShowMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
    favouriteIds: Set<String>? = null,
    onToggleFavourite: (BusinessRow) -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_places_title), onBack, modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                val title = when {
                    state.category != null -> state.categoryName
                    state.group == NearbyBusinesses.UNKNOWN_GROUP -> stringResource(Res.string.area_places_group_other)
                    state.group != null -> state.groupName
                    else -> stringResource(Res.string.area_places_all)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesListTitle),
                )
                Text(
                    pluralStringResource(Res.plurals.area_places_header, state.matching, groupedNumber(state.matching)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesHeader),
                )
            }
            if (state.partial) PartialBanner(onRetry)
            RoundedSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(Res.string.area_places_search_label),
                tag = MarketplaceAreaTestTags.BusinessesSearchField,
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(MarketplaceAreaTestTags.BusinessesList)) {
                state.sections.forEach { section ->
                    item(key = "section-${section.localityRow}-${section.items.first().id}") { SectionHeader(section) }
                    items(section.items, key = { it.id }) { row ->
                        BusinessItem(row, favouriteIds?.let { row.id in it }) { onToggleFavourite(row) }
                    }
                }
                if (state.items.isEmpty()) {
                    item(key = "none") {
                        Text(
                            stringResource(Res.string.area_places_no_match),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 12.dp).testTag(MarketplaceAreaTestTags.BusinessesNoMatch),
                        )
                    }
                }
                if (state.hasMore) {
                    item(key = "more") {
                        TextButton(onClick = onShowMore, modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesShowMore)) {
                            Text(stringResource(Res.string.area_places_show_more))
                        }
                    }
                }
                item(key = "missing") { MissingBusinessNote(Modifier.padding(vertical = 16.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: BusinessSection) {
    if (section.localityName.isEmpty()) return
    Text(
        section.localityName,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).testTag(MarketplaceAreaTestTags.businessesSection(section.localityRow)),
    )
}

@Composable
private fun groupLabel(group: BusinessGroupCount): String =
    if (group.key == NearbyBusinesses.UNKNOWN_GROUP) stringResource(Res.string.area_places_group_other) else group.name

/** One business; [favourite] is null when hearts are hidden, else whether it is in My Shops ([onToggleFavourite] flips it). */
@Composable
private fun BusinessItem(row: BusinessRow, favourite: Boolean?, onToggleFavourite: () -> Unit) {
    val handler = LocalUriHandler.current
    BusinessCardRow {
        Column(
            Modifier.weight(1f).padding(vertical = 4.dp).semantics(mergeDescendants = true) {}
                .testTag(MarketplaceAreaTestTags.businessesRow(row.id)),
        ) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            // The locality the business is listed under: the one it is in, or for a place outside every polygon the nearest.
            val where = if (row.localityName.isEmpty()) row.categoryName
            else stringResource(Res.string.area_places_row_locality, row.categoryName, row.localityName)
            Text(where, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val latitude = row.latitude
        val longitude = row.longitude
        if (latitude != null && longitude != null) {
            IconButton(
                onClick = { handler.openUri(directionsUrl(latitude, longitude)) },
                modifier = Modifier.testTag(MarketplaceAreaTestTags.directions(row.id)),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_map_pin),
                    contentDescription = stringResource(Res.string.area_places_directions, row.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        favourite?.let { filled ->
            HeartButton(
                filled,
                if (filled) stringResource(Res.string.area_favourite_remove, row.name) else stringResource(Res.string.area_favourite_add, row.name),
                MarketplaceAreaTestTags.favouriteHeart(row.id),
                onToggleFavourite,
            )
        }
    }
}

/** An OpenStreetMap link pinned on the place; built from the pack's own coordinates, so nothing about the member is in it. */
internal fun directionsUrl(latitude: Double, longitude: Double): String =
    "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=18/$latitude/$longitude"

private val PreviewSummary = AreaBusinessesState(
    inside = 250, nearby = 74_050, bytesInsideOnly = 262_144, bytesWithNearby = 2_621_440, includeNearby = false,
)
private val PreviewLoading = PreviewSummary.copy(chunksDone = 3, chunksTotal = 10, bytesFetched = 800_000)
private val PreviewGroups = PreviewSummary.copy(
    partial = true,
    loaded = 250,
    groups = listOf(
        BusinessGroupCount("food_and_drink", "Food and drink", 120),
        BusinessGroupCount("services", "Services", 80),
        BusinessGroupCount("unknown", "Unknown", 4),
    ),
)
private val PreviewCategories = PreviewGroups.copy(
    partial = false,
    group = "food_and_drink",
    groupName = "Food and drink",
    groupCount = 120,
    categories = listOf(CategoryCount(0, "Cafe", 70), CategoryCount(2, "Pub", 50)),
)
private val PreviewList = PreviewGroups.copy(
    matching = 3,
    hasMore = true,
    group = "food_and_drink",
    groupName = "Food and drink",
    category = 0,
    categoryName = "Cafe",
    sections = listOf(
        BusinessSection(
            3, "Hexham",
            listOf(
                BusinessRow("1", "Cafe Nero", "Cafe", "Food and drink", 350.0, 3, false, "Hexham"),
                BusinessRow("3", "Riverside Cafe", "Cafe", "Food and drink", 380.0, 3, false, "Hexham"),
            ),
        ),
        BusinessSection(5, "Acomb", listOf(BusinessRow("2", "Nearby Cafe", "Cafe", "Food and drink", 1_200.0, 5, true, "Acomb"))),
    ),
    items = listOf(
        BusinessRow("1", "Cafe Nero", "Cafe", "Food and drink", 350.0, 3, false, "Hexham"),
        BusinessRow("3", "Riverside Cafe", "Cafe", "Food and drink", 380.0, 3, false, "Hexham"),
        BusinessRow("2", "Nearby Cafe", "Cafe", "Food and drink", 1_200.0, 5, true, "Acomb"),
    ),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessesSummaryPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesSummaryScreen(PreviewSummary) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessesSummaryPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesSummaryScreen(PreviewSummary) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessesSummaryTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesSummaryScreen(PreviewSummary.copy(includeNearby = true)) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessesSummaryTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesSummaryScreen(PreviewSummary.copy(includeNearby = true)) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessesLoadingPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesLoadingScreen(PreviewLoading) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessesLoadingPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesLoadingScreen(PreviewLoading) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessesLoadingTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesLoadingScreen(PreviewLoading) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessesLoadingTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesLoadingScreen(PreviewLoading) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessGroupsPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessGroupsScreen(PreviewGroups, myShopsCount = 2, areaName = "Orlestone", nearbyCount = 4) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessGroupsPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessGroupsScreen(PreviewGroups, myShopsCount = 2, areaName = "Orlestone", nearbyCount = 4) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessGroupsTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessGroupsScreen(PreviewGroups.copy(partial = false, browseQuery = "caf"), myShopsCount = 0, areaName = "Orlestone", nearbyCount = 0) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessGroupsTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessGroupsScreen(PreviewGroups.copy(partial = false, browseQuery = "caf"), myShopsCount = 0, areaName = "Orlestone", nearbyCount = 0) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessCategoriesPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessCategoriesScreen(PreviewCategories) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessCategoriesPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessCategoriesScreen(PreviewCategories) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessCategoriesTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessCategoriesScreen(PreviewCategories.copy(categoryQuery = "pu", categories = PreviewCategories.categories.drop(1))) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessCategoriesTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessCategoriesScreen(PreviewCategories.copy(categoryQuery = "pu", categories = PreviewCategories.categories.drop(1))) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessListPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesListScreen(PreviewList, favouriteIds = setOf("1")) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessListPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesListScreen(PreviewList, favouriteIds = setOf("1")) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessListTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesListScreen(PreviewList.copy(partial = false), favouriteIds = setOf("1")) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessListTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesListScreen(PreviewList.copy(partial = false), favouriteIds = setOf("1")) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessesUnreachablePhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesUnreachableScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessesUnreachablePhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesUnreachableScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessesUnreachableTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesUnreachableScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessesUnreachableTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesUnreachableScreen() }
}
