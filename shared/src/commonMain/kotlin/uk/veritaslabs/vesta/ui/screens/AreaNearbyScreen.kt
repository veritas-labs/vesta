package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import uk.veritaslabs.vesta.location.NearbyRelation
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_nearby_contains
import vesta.shared.generated.resources.area_nearby_distance_km
import vesta.shared.generated.resources.area_nearby_distance_m
import vesta.shared.generated.resources.area_nearby_home_locked
import vesta.shared.generated.resources.area_nearby_inside
import vesta.shared.generated.resources.area_nearby_next_door
import vesta.shared.generated.resources.area_nearby_no_match
import vesta.shared.generated.resources.area_nearby_prompt
import vesta.shared.generated.resources.area_nearby_relation_distance
import vesta.shared.generated.resources.area_nearby_save
import vesta.shared.generated.resources.area_nearby_search_label
import vesta.shared.generated.resources.area_nearby_show_more
import vesta.shared.generated.resources.area_nearby_ticked
import vesta.shared.generated.resources.area_nearby_title

private const val MetresPerKilometre = 1000.0
private const val TenthsPerKilometre = 10

/**
 * The "Also search nearby places" step (ADR-039 decision 3): the [home] locality ticked and locked on top, then the visible
 * [nearby] rows with a relation label and distance each, a search box ([nearbyQuery]), Show more when [hasMore], the count
 * of places [tickedCount] and Save. No merchant counts or download sizes are shown. Taps report through [onToggle],
 * [onQueryChange], [onShowMore] and [onSave].
 */
@Composable
fun AreaNearbyScreen(
    home: AreaPlace,
    nearby: List<AreaNearbyRow>,
    nearbyQuery: String,
    hasMore: Boolean,
    tickedCount: Int,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (String) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onShowMore: () -> Unit = {},
    onSave: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_nearby_title), onBack, modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.area_nearby_prompt), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = nearbyQuery,
                onValueChange = onQueryChange,
                label = { Text(stringResource(Res.string.area_nearby_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(MarketplaceAreaTestTags.NearbySearchField),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(MarketplaceAreaTestTags.NearbyList)) {
                item(key = "home") {
                    PlaceRow(
                        name = stringResource(Res.string.area_nearby_home_locked, home.name),
                        detail = home.chainText,
                        ticked = true,
                        enabled = false,
                        onToggle = {},
                        modifier = Modifier.testTag(MarketplaceAreaTestTags.NearbyHome),
                    )
                }
                items(nearby, key = { it.id }) { row ->
                    PlaceRow(
                        name = row.name,
                        detail = stringResource(
                            Res.string.area_nearby_relation_distance,
                            relationLabel(row.relation, home.name),
                            distanceLabel(row.distanceMetres),
                        ),
                        ticked = row.ticked,
                        enabled = true,
                        onToggle = { onToggle(row.id) },
                        modifier = Modifier.testTag(MarketplaceAreaTestTags.nearby(row.id)),
                    )
                }
                if (nearby.isEmpty() && nearbyQuery.isNotBlank()) {
                    item(key = "none") {
                        Text(
                            stringResource(Res.string.area_nearby_no_match),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 12.dp).testTag(MarketplaceAreaTestTags.NearbyNoMatch),
                        )
                    }
                }
                if (hasMore) {
                    item(key = "more") {
                        TextButton(onClick = onShowMore, modifier = Modifier.testTag(MarketplaceAreaTestTags.NearbyShowMore)) {
                            Text(stringResource(Res.string.area_nearby_show_more))
                        }
                    }
                }
            }
            Text(
                pluralStringResource(Res.plurals.area_nearby_ticked, tickedCount, tickedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(MarketplaceAreaTestTags.NearbyTicked),
            )
            PrimaryButton(
                pluralStringResource(Res.plurals.area_nearby_save, tickedCount, tickedCount),
                Modifier.testTag(MarketplaceAreaTestTags.NearbySave),
                enabled = !isSaving,
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun PlaceRow(
    name: String,
    detail: String,
    ticked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = ticked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = ticked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun relationLabel(relation: NearbyRelation, homeName: String): String = when (relation) {
    NearbyRelation.Neighbour, NearbyRelation.NeighbourAndNested -> stringResource(Res.string.area_nearby_next_door)
    // The enum is from the home's side: Contains = the home contains this place, so the place is inside the home.
    NearbyRelation.Contains -> stringResource(Res.string.area_nearby_inside, homeName)
    NearbyRelation.InsideOf -> stringResource(Res.string.area_nearby_contains, homeName)
}

/** "350 m" under a kilometre, otherwise kilometres to one decimal place, such as "1.2 km". */
@Composable
internal fun distanceLabel(metres: Double): String =
    if (metres < MetresPerKilometre) {
        stringResource(Res.string.area_nearby_distance_m, metres.roundToInt())
    } else {
        val tenths = (metres / MetresPerKilometre * TenthsPerKilometre).roundToInt()
        stringResource(Res.string.area_nearby_distance_km, "${tenths / TenthsPerKilometre}.${tenths % TenthsPerKilometre}")
    }

private val PreviewHome = AreaPlace("3", "Newport", "Newport, Isle of Wight, England")
private val PreviewRows = listOf(
    AreaNearbyRow("5", "Cowes", NearbyRelation.Neighbour, 6800.0, ticked = true),
    AreaNearbyRow("7", "Carisbrooke", NearbyRelation.Contains, 350.0, ticked = false),
    AreaNearbyRow("9", "Isle of Wight", NearbyRelation.InsideOf, 1200.0, ticked = false),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaNearbyScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaNearbyScreen(PreviewHome, PreviewRows, "", hasMore = true, tickedCount = 2, isSaving = false) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaNearbyScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaNearbyScreen(PreviewHome, PreviewRows, "", hasMore = true, tickedCount = 2, isSaving = false) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaNearbyScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaNearbyScreen(PreviewHome, PreviewRows, "", hasMore = false, tickedCount = 2, isSaving = false) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaNearbyScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaNearbyScreen(PreviewHome, emptyList(), "zz", hasMore = false, tickedCount = 1, isSaving = false) }
}
