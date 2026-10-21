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
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_title
import vesta.shared.generated.resources.area_unavailable_not_on_device
import vesta.shared.generated.resources.area_unavailable_try_again
import vesta.shared.generated.resources.area_unavailable_unconfigured
import vesta.shared.generated.resources.area_unavailable_unreachable

/**
 * Says why the area cannot be chosen ([why]): no data address configured here, not available on this platform yet, or the
 * data could not be loaded, which also offers Try again ([onTryAgain]). The reason inside Unreachable is for logs, not shown.
 */
@Composable
fun AreaUnavailableScreen(
    why: AreaUnavailable,
    modifier: Modifier = Modifier,
    onTryAgain: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_title), onBack, modifier) {
        AreaScrollColumn {
            when (why) {
                AreaUnavailable.Unconfigured -> Message(
                    stringResource(Res.string.area_unavailable_unconfigured),
                    MarketplaceAreaTestTags.UnavailableUnconfigured,
                )
                AreaUnavailable.NotAvailableOnDevice -> Message(
                    stringResource(Res.string.area_unavailable_not_on_device),
                    MarketplaceAreaTestTags.UnavailableNotOnDevice,
                )
                is AreaUnavailable.Unreachable -> {
                    Message(
                        stringResource(Res.string.area_unavailable_unreachable),
                        MarketplaceAreaTestTags.UnavailableUnreachable,
                    )
                    PrimaryButton(
                        stringResource(Res.string.area_unavailable_try_again),
                        Modifier.testTag(MarketplaceAreaTestTags.TryAgain),
                        onClick = onTryAgain,
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, tag: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaUnavailableScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaUnavailableScreen(AreaUnavailable.Unreachable("offline")) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaUnavailableScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaUnavailableScreen(AreaUnavailable.Unreachable("offline")) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaUnavailableScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaUnavailableScreen(AreaUnavailable.Unconfigured) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaUnavailableScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaUnavailableScreen(AreaUnavailable.NotAvailableOnDevice) }
}
