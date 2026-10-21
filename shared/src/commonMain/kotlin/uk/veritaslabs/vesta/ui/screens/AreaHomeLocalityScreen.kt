package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_home_hint
import vesta.shared.generated.resources.area_home_keep
import vesta.shared.generated.resources.area_home_no_match
import vesta.shared.generated.resources.area_home_search_label
import vesta.shared.generated.resources.area_home_title

/**
 * The home locality step (ADR-039 decision 3): a search box and what it found. An empty [query] shows a hint and no list; a
 * query that found nothing ([noMatch]) says so. Each of [results] shows its name and parent chain and reports a tap through
 * [onChoose]. While editing, [home] is the place already chosen and "Keep this place" calls [onChoose] with it.
 */
@Composable
fun AreaHomeLocalityScreen(
    query: String,
    results: List<AreaPlace>,
    noMatch: Boolean,
    home: AreaPlace?,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onChoose: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_home_title), onBack, modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(Res.string.area_home_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(MarketplaceAreaTestTags.HomeSearchField),
            )
            home?.let {
                TextButton(onClick = { onChoose(it.id) }, modifier = Modifier.testTag(MarketplaceAreaTestTags.HomeKeep)) {
                    Text(stringResource(Res.string.area_home_keep, it.name))
                }
            }
            when {
                query.isBlank() -> Text(
                    stringResource(Res.string.area_home_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(MarketplaceAreaTestTags.HomeHint),
                )
                noMatch -> Text(
                    stringResource(Res.string.area_home_no_match),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(MarketplaceAreaTestTags.HomeNoMatch),
                )
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(results, key = { it.id }) { place ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = { onChoose(place.id) })
                                .padding(vertical = 12.dp)
                                .testTag(MarketplaceAreaTestTags.place(place.id)),
                        ) {
                            Text(place.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                place.chainText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PreviewResults = listOf(
    AreaPlace("1", "Newport", "Newport, Isle of Wight, England"),
    AreaPlace("2", "Newport", "Newport, Wales"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaHomeLocalityScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaHomeLocalityScreen("newp", PreviewResults, noMatch = false, home = null) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaHomeLocalityScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaHomeLocalityScreen("newp", PreviewResults, noMatch = false, home = null) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaHomeLocalityScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaHomeLocalityScreen("", emptyList(), noMatch = false, home = PreviewResults[0]) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaHomeLocalityScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaHomeLocalityScreen("zzz", emptyList(), noMatch = true, home = null) }
}
