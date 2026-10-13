package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PackageSizeField
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.vestaFilterChipColors
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.pantry_back
import vesta.shared.generated.resources.pantry_edit_package_done
import vesta.shared.generated.resources.pantry_edit_package_loose
import vesta.shared.generated.resources.pantry_edit_package_packaged
import vesta.shared.generated.resources.pantry_edit_package_size_label

private val PantryItemEditPackageTabletBreakpoint = 600.dp
private val PackageSummaryFontSize = 24.sp
private val PackageSummaryLineHeight = 32.sp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object PantryItemEditPackageTestTags {
    const val BackButton = "pantry_item_edit_package_back_button"
    const val PackagedChip = "pantry_item_edit_package_packaged_chip"
    const val LooseChip = "pantry_item_edit_package_loose_chip"
    const val Summary = "pantry_item_edit_package_summary"
    const val DoneButton = "pantry_item_edit_package_done_button"
}

/**
 * Where an item's package size is chosen (ADR-011), opened from [PantryItemEditScreen]. The item is either
 * "Packaged" — an amount plus unit and/or container via [PackageSizeField] — or "Loose" (an apple, a bunch of
 * bananas), which is stored as a blank size. [onDone] receives the canonical size, or "" for loose; [onBack] discards.
 *
 * [initialValue] prefills the pickers. A blank [initialValue] starts as Loose only when [startLoose] (editing an
 * existing item); a new item starts as Packaged, since that's the usual case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryItemEditPackageScreen(
    modifier: Modifier = Modifier,
    itemName: String = "",
    initialValue: String = "",
    startLoose: Boolean = false,
    onDone: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var loose by remember(initialValue, startLoose) { mutableStateOf(initialValue.isBlank() && startLoose) }
    var packaged by remember(initialValue) { mutableStateOf(initialValue) }
    val result = if (loose) "" else packaged
    val canFinish = loose || packaged.isNotBlank()

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= PantryItemEditPackageTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                modifier = Modifier
                    .zIndex(1f)
                    .shadow(elevation = 16.dp, ambientColor = PantryAccent, spotColor = PantryAccent),
                title = {
                    Column {
                        Text(stringResource(Res.string.pantry_edit_package_size_label))
                        if (itemName.isNotBlank()) {
                            Text(
                                text = itemName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(PantryItemEditPackageTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.pantry_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = contentPadding),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text(
                        text = when {
                            loose -> stringResource(Res.string.pantry_edit_package_loose)
                            packaged.isBlank() -> "–"
                            else -> packaged
                        },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = PackageSummaryFontSize,
                            lineHeight = PackageSummaryLineHeight,
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp).testTag(PantryItemEditPackageTestTags.Summary),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    FilterChip(
                        colors = vestaFilterChipColors(),
                        selected = !loose,
                        onClick = { loose = false },
                        label = { Text(stringResource(Res.string.pantry_edit_package_packaged)) },
                        modifier = Modifier.testTag(PantryItemEditPackageTestTags.PackagedChip),
                    )
                    FilterChip(
                        colors = vestaFilterChipColors(),
                        selected = loose,
                        onClick = { loose = true },
                        label = { Text(stringResource(Res.string.pantry_edit_package_loose)) },
                        modifier = Modifier.testTag(PantryItemEditPackageTestTags.LooseChip),
                    )
                }
                if (!loose) {
                    PackageSizeField(
                        initialValue = initialValue,
                        onValueChange = { packaged = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            PrimaryButton(
                label = stringResource(Res.string.pantry_edit_package_done),
                enabled = canFinish,
                onClick = { onDone(result) },
                modifier = Modifier.padding(horizontal = contentPadding, vertical = 16.dp)
                    .testTag(PantryItemEditPackageTestTags.DoneButton),
            )
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditPackageScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditPackageScreen(itemName = "Tomato Sauce", initialValue = "15 oz tin") }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditPackageScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditPackageScreen(itemName = "Tomato Sauce", initialValue = "15 oz tin") }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditPackageScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditPackageScreen(itemName = "Tomato Sauce", initialValue = "15 oz tin") }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditPackageScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditPackageScreen(itemName = "Tomato Sauce", initialValue = "15 oz tin") }
}

@Preview(name = "Light - loose", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditPackageScreenLoosePhoneLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditPackageScreen(itemName = "Apples", startLoose = true) }
}

@Preview(name = "Dark - loose", group = "Phone", device = Devices.PHONE)
@Composable
private fun PantryItemEditPackageScreenLoosePhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditPackageScreen(itemName = "Apples", startLoose = true) }
}

@Preview(name = "Light - loose", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditPackageScreenLooseTabletLightPreview() {
    VestaTheme(darkTheme = false) { PantryItemEditPackageScreen(itemName = "Apples", startLoose = true) }
}

@Preview(name = "Dark - loose", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PantryItemEditPackageScreenLooseTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PantryItemEditPackageScreen(itemName = "Apples", startLoose = true) }
}
