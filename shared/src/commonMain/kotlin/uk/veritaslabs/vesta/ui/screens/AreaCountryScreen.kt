package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_country_continue
import vesta.shared.generated.resources.area_country_more_coming
import vesta.shared.generated.resources.area_country_title

/**
 * The country step (ADR-039 decision 3): one row for each of [countries], the [selected] code highlighted, and Continue,
 * which stays disabled until a country is selected. [onSelect] reports a tapped code.
 */
@Composable
fun AreaCountryScreen(
    countries: List<AreaCountry>,
    selected: String?,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit = {},
    onContinue: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_country_title), onBack, modifier) {
        AreaScrollColumn {
            countries.forEach { country ->
                val isSelected = country.code == selected
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(country.code) })
                        .testTag(MarketplaceAreaTestTags.country(country.code)),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSelected, onClick = null)
                        Text(country.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            Text(
                stringResource(Res.string.area_country_more_coming),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(MarketplaceAreaTestTags.CountryMoreComing),
            )
            PrimaryButton(
                stringResource(Res.string.area_country_continue),
                Modifier.testTag(MarketplaceAreaTestTags.CountryContinue),
                enabled = selected != null,
                onClick = onContinue,
            )
        }
    }
}

private val PreviewCountries = listOf(AreaCountry("GB", "United Kingdom"))

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaCountryScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaCountryScreen(PreviewCountries, selected = "GB") }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaCountryScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaCountryScreen(PreviewCountries, selected = "GB") }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaCountryScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaCountryScreen(PreviewCountries, selected = null) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaCountryScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaCountryScreen(PreviewCountries, selected = null) }
}
