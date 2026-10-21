package uk.veritaslabs.vesta.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.SecondaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_disclosure_businesses
import vesta.shared.generated.resources.area_disclosure_continue
import vesta.shared.generated.resources.area_disclosure_not_now
import vesta.shared.generated.resources.area_disclosure_private
import vesta.shared.generated.resources.area_disclosure_size
import vesta.shared.generated.resources.area_disclosure_title
import vesta.shared.generated.resources.area_disclosure_what
import vesta.shared.generated.resources.area_disclosure_who

/**
 * Asks before the first download of the location data (ADR-039 decision 3): who can see the request, what it names, how big
 * it is and that the chosen area stays on the user's devices. Nothing has been requested yet. [onContinue] agrees to the
 * download, [onNotNow] declines it, and [onBack] is the back arrow.
 */
@Composable
fun AreaDisclosureScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit = {},
    onNotNow: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_disclosure_title), onBack, modifier) {
        AreaScrollColumn(Modifier.testTag(MarketplaceAreaTestTags.Disclosure)) {
            Text(stringResource(Res.string.area_disclosure_who), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(Res.string.area_disclosure_what), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(Res.string.area_disclosure_size), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(Res.string.area_disclosure_businesses), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.area_disclosure_private),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                stringResource(Res.string.area_disclosure_continue),
                Modifier.testTag(MarketplaceAreaTestTags.DisclosureContinue),
                onClick = onContinue,
            )
            SecondaryButton(
                stringResource(Res.string.area_disclosure_not_now),
                Modifier.testTag(MarketplaceAreaTestTags.DisclosureNotNow),
                onClick = onNotNow,
            )
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaDisclosureScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaDisclosureScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaDisclosureScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaDisclosureScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaDisclosureScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaDisclosureScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaDisclosureScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaDisclosureScreen() }
}
