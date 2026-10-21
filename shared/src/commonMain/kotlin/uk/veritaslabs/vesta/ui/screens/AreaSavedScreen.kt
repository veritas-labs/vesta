package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.ConfirmDeleteDialog
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.SecondaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_saved_change
import vesta.shared.generated.resources.area_saved_forget
import vesta.shared.generated.resources.area_saved_forget_body
import vesta.shared.generated.resources.area_saved_forget_title
import vesta.shared.generated.resources.area_saved_searching
import vesta.shared.generated.resources.area_saved_searching_alone
import vesta.shared.generated.resources.area_saved_set
import vesta.shared.generated.resources.area_saved_title

/**
 * The stored area (ADR-039 decision 3): "Searching in X and N nearby places", or "Your area is set" while the place's name
 * is not known because the location data is not loaded. Change ([onChange]) edits it; Forget asks first and then calls
 * [onForget].
 */
@Composable
fun AreaSavedScreen(
    summary: AreaSummary,
    modifier: Modifier = Modifier,
    onChange: () -> Unit = {},
    onForget: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var confirmingForget by remember { mutableStateOf(false) }
    AreaScaffold(stringResource(Res.string.area_saved_title), onBack, modifier) {
        AreaScrollColumn {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag(MarketplaceAreaTestTags.SavedSummary),
            ) {
                Column(Modifier.padding(16.dp)) {
                    val name = summary.homeName
                    Text(
                        when {
                            name == null -> stringResource(Res.string.area_saved_set)
                            summary.nearbyCount == 0 -> stringResource(Res.string.area_saved_searching_alone, name)
                            else -> pluralStringResource(Res.plurals.area_saved_searching, summary.nearbyCount, name, summary.nearbyCount)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    summary.chainText?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            PrimaryButton(
                stringResource(Res.string.area_saved_change),
                Modifier.testTag(MarketplaceAreaTestTags.SavedChange),
                onClick = onChange,
            )
            SecondaryButton(
                stringResource(Res.string.area_saved_forget),
                Modifier.testTag(MarketplaceAreaTestTags.SavedForget),
                onClick = { confirmingForget = true },
            )
        }
    }
    if (confirmingForget) {
        ConfirmDeleteDialog(
            title = stringResource(Res.string.area_saved_forget_title),
            body = stringResource(Res.string.area_saved_forget_body),
            confirmLabel = stringResource(Res.string.area_saved_forget),
            onConfirm = {
                confirmingForget = false
                onForget()
            },
            onDismiss = { confirmingForget = false },
        )
    }
}

private val PreviewSummary = AreaSummary("Newport", "Newport, Isle of Wight, England", nearbyCount = 3)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaSavedScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaSavedScreen(PreviewSummary) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaSavedScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaSavedScreen(PreviewSummary) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaSavedScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaSavedScreen(PreviewSummary) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaSavedScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaSavedScreen(AreaSummary(null, null, nearbyCount = 0)) }
}
