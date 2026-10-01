package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.pantry.PackageContainer
import uk.veritaslabs.vesta.pantry.PackageContainers
import uk.veritaslabs.vesta.pantry.PackageSize
import uk.veritaslabs.vesta.pantry.PackageUnit
import uk.veritaslabs.vesta.pantry.PackageUnits
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.pantry_edit_package_amount_label
import vesta.shared.generated.resources.pantry_edit_package_container_label
import vesta.shared.generated.resources.pantry_edit_package_size_label
import vesta.shared.generated.resources.pantry_edit_package_type_it
import vesta.shared.generated.resources.pantry_edit_package_unit_label
import vesta.shared.generated.resources.pantry_edit_package_use_presets

/** Stable hooks for UI tests to target, independent of displayed copy. */
object PackageSizeFieldTestTags {
    const val AmountField = "package_size_amount_field"
    const val CustomField = "package_size_custom_field"
    const val CustomToggle = "package_size_custom_toggle"

    fun unit(symbol: String) = "package_size_unit_$symbol"
    fun container(name: String) = "package_size_container_$name"
}

/**
 * Picks a package size (ADR-011) from an amount, unit chips and container chips, reporting the canonical text
 * through [onValueChange] (the caller shows it) — blank until there is an amount and a unit or container. [initialValue] is parsed to
 * prefill the pickers; text it can't parse opens in "type it instead" mode so nothing is lost. The user can switch
 * to free text at any time, and back (which drops text that doesn't parse).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PackageSizeField(
    initialValue: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(initialValue) { PackageSize.parse(initialValue) }
    var custom by remember(initialValue) { mutableStateOf(initialValue.isNotBlank() && parsed == null) }
    var customText by remember(initialValue) { mutableStateOf(initialValue) }
    var amount by remember(initialValue) { mutableStateOf(parsed?.amount.orEmpty()) }
    var unit by remember(initialValue) { mutableStateOf(parsed?.unit) }
    var container by remember(initialValue) { mutableStateOf(parsed?.container) }

    fun structured(): String =
        if (amount.isBlank() || (unit == null && container == null)) "" else PackageSize(amount, unit, container).format()

    Column(modifier = modifier.fillMaxWidth()) {
        if (custom) {
            OutlinedTextField(
                value = customText,
                onValueChange = {
                    customText = it
                    onValueChange(it.trim())
                },
                label = { Text(stringResource(Res.string.pantry_edit_package_size_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(PackageSizeFieldTestTags.CustomField),
            )
        } else {
            OutlinedTextField(
                value = amount,
                onValueChange = { new ->
                    if (new.all { it.isDigit() || it == '.' } && new.count { it == '.' } <= 1) {
                        amount = new
                        onValueChange(structured())
                    }
                },
                label = { Text(stringResource(Res.string.pantry_edit_package_amount_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag(PackageSizeFieldTestTags.AmountField),
            )
            ChipGroup(label = stringResource(Res.string.pantry_edit_package_unit_label)) {
                PackageUnits.forEach { option ->
                    FilterChip(
                        colors = vestaFilterChipColors(),
                        selected = unit == option,
                        onClick = {
                            unit = if (unit == option) null else option
                            onValueChange(structured())
                        },
                        label = { Text(option.symbol) },
                        modifier = Modifier.testTag(PackageSizeFieldTestTags.unit(option.symbol)),
                    )
                }
            }
            ChipGroup(label = stringResource(Res.string.pantry_edit_package_container_label)) {
                PackageContainers.forEach { option ->
                    FilterChip(
                        colors = vestaFilterChipColors(),
                        selected = container == option,
                        onClick = {
                            container = if (container == option) null else option
                            onValueChange(structured())
                        },
                        label = { Text(option.name) },
                        modifier = Modifier.testTag(PackageSizeFieldTestTags.container(option.name)),
                    )
                }
            }
        }
        TextButton(
            onClick = {
                if (custom) {
                    val reparsed = PackageSize.parse(customText)
                    amount = reparsed?.amount.orEmpty()
                    unit = reparsed?.unit
                    container = reparsed?.container
                    custom = false
                    onValueChange(structured())
                } else {
                    customText = structured()
                    custom = true
                }
            },
            modifier = Modifier.testTag(PackageSizeFieldTestTags.CustomToggle),
        ) {
            Text(
                stringResource(
                    if (custom) Res.string.pantry_edit_package_use_presets else Res.string.pantry_edit_package_type_it,
                ),
            )
        }
    }
}

/** Selected chips use the app's primary colours instead of Material's default (purple) secondary container. */
@Composable
internal fun vestaFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(label: String, chips: @Composable () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PackageSizeFieldPhoneLightPreview() {
    VestaTheme(darkTheme = false) { PackageSizeField(initialValue = "15 oz tin", onValueChange = {}, modifier = Modifier.padding(16.dp)) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PackageSizeFieldPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PackageSizeField(initialValue = "15 oz tin", onValueChange = {}, modifier = Modifier.padding(16.dp)) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PackageSizeFieldTabletLightPreview() {
    VestaTheme(darkTheme = false) { PackageSizeField(initialValue = "15 oz tin", onValueChange = {}, modifier = Modifier.padding(24.dp)) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PackageSizeFieldTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PackageSizeField(initialValue = "15 oz tin", onValueChange = {}, modifier = Modifier.padding(24.dp)) }
}
