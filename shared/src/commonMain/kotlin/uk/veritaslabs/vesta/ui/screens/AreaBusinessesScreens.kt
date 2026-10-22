package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import uk.veritaslabs.vesta.location.NearbyBusinesses
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.SecondaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_places_cancel
import vesta.shared.generated.resources.area_places_confirm_body
import vesta.shared.generated.resources.area_places_confirm_title
import vesta.shared.generated.resources.area_places_download
import vesta.shared.generated.resources.area_places_group_all
import vesta.shared.generated.resources.area_places_group_other
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

/**
 * The businesses fetched ([state]): a count header, a search field, group chips, rows (name, category and distance from
 * home; never "in <place>"), Show more and an empty-results line. [AreaBusinessesState.partial] adds a banner with Try
 * again ([onRetry]). Rows are plain display.
 */
@Composable
fun AreaBusinessesListScreen(
    state: AreaBusinessesState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onGroup: (String?) -> Unit = {},
    onShowMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_places_title), onBack, modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                pluralStringResource(Res.plurals.area_places_header, state.loaded, groupedNumber(state.loaded)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesHeader),
            )
            if (state.partial) {
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
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(Res.string.area_places_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(MarketplaceAreaTestTags.BusinessesSearchField),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") {
                    FilterChip(
                        selected = state.group == null,
                        onClick = { onGroup(null) },
                        label = { Text(stringResource(Res.string.area_places_group_all)) },
                        modifier = Modifier.testTag(MarketplaceAreaTestTags.BusinessesGroupAll),
                    )
                }
                items(state.groups, key = { it.key }) { group ->
                    FilterChip(
                        selected = state.group == group.key,
                        onClick = { onGroup(group.key) },
                        label = { Text("${groupLabel(group)} (${groupedNumber(group.count)})") },
                        modifier = Modifier.testTag(MarketplaceAreaTestTags.businessesGroup(group.key)),
                    )
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(MarketplaceAreaTestTags.BusinessesList)) {
                items(state.items, key = { it.id }) { row -> BusinessItem(row) }
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
            }
        }
    }
}

@Composable
private fun groupLabel(group: BusinessGroupCount): String =
    if (group.key == NearbyBusinesses.UNKNOWN_GROUP) stringResource(Res.string.area_places_group_other) else group.name

@Composable
private fun BusinessItem(row: BusinessRow) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}
            .testTag(MarketplaceAreaTestTags.businessesRow(row.id)),
    ) {
        Text(row.name, style = MaterialTheme.typography.titleMedium)
        // The locality the business is listed under: the one it is in, or for a place outside every polygon the nearest.
        val where = if (row.localityName.isEmpty()) row.categoryName
        else stringResource(Res.string.area_places_row_locality, row.categoryName, row.localityName)
        Text(where, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val PreviewSummary = AreaBusinessesState(
    inside = 250, nearby = 74_050, bytesInsideOnly = 262_144, bytesWithNearby = 2_621_440, includeNearby = false,
)
private val PreviewLoading = PreviewSummary.copy(chunksDone = 3, chunksTotal = 10, bytesFetched = 800_000)
private val PreviewList = PreviewSummary.copy(
    partial = true,
    loaded = 250,
    matching = 250,
    hasMore = true,
    groups = listOf(BusinessGroupCount("food_and_drink", "Food and drink", 120), BusinessGroupCount("unknown", "Unknown", 4)),
    items = listOf(
        BusinessRow("1", "Café Nero", "Cafe", "Food and drink", 350.0, 3, false, "Hexham"),
        BusinessRow("2", "Nearby Pub", "Pub", "Food and drink", 1_200.0, 5, true, "Acomb"),
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
private fun AreaBusinessesListPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesListScreen(PreviewList) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaBusinessesListPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesListScreen(PreviewList) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessesListTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaBusinessesListScreen(PreviewList) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaBusinessesListTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaBusinessesListScreen(PreviewList.copy(partial = false)) }
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
