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
import vesta.shared.generated.resources.area_rechoose_body
import vesta.shared.generated.resources.area_rechoose_choose
import vesta.shared.generated.resources.area_rechoose_title
import vesta.shared.generated.resources.area_title

/** Shown when the stored place is not in the location data any more (a newer release): Choose ([onChoose]) starts again. */
@Composable
fun AreaNeedsRechooseScreen(
    modifier: Modifier = Modifier,
    onChoose: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    AreaScaffold(stringResource(Res.string.area_title), onBack, modifier) {
        AreaScrollColumn {
            Text(stringResource(Res.string.area_rechoose_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.area_rechoose_body), style = MaterialTheme.typography.bodyLarge)
            PrimaryButton(
                stringResource(Res.string.area_rechoose_choose),
                Modifier.testTag(MarketplaceAreaTestTags.RechooseChoose),
                onClick = onChoose,
            )
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaNeedsRechooseScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AreaNeedsRechooseScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AreaNeedsRechooseScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AreaNeedsRechooseScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaNeedsRechooseScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { AreaNeedsRechooseScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AreaNeedsRechooseScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AreaNeedsRechooseScreen() }
}
