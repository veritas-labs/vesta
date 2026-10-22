package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.SecondaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_missing_body
import vesta.shared.generated.resources.area_missing_open
import vesta.shared.generated.resources.area_missing_title

/** The address the button opens: the site's front page only, so nothing about the member's area is in the URL. */
internal const val OpenStreetMapUrl = "https://www.openstreetmap.org"

/**
 * "A business is missing": says where Vesta's listings come from and that a missing business is added on OpenStreetMap,
 * with a button that opens the site ([onOpen], by default through the platform's URI handler). Nothing is sent from the app.
 */
@Composable
fun MissingBusinessNote(modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val handler = LocalUriHandler.current
    Surface(
        modifier.fillMaxWidth().testTag(MarketplaceAreaTestTags.MissingNote),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.area_missing_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.area_missing_body), style = MaterialTheme.typography.bodyMedium)
            SecondaryButton(
                stringResource(Res.string.area_missing_open),
                Modifier.testTag(MarketplaceAreaTestTags.MissingOpen),
                onClick = { onOpen?.invoke() ?: handler.openUri(OpenStreetMapUrl) },
            )
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MissingBusinessPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MissingBusinessNote(Modifier.padding(16.dp)) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MissingBusinessPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MissingBusinessNote(Modifier.padding(16.dp)) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MissingBusinessTabletLightPreview() {
    VestaTheme(darkTheme = false) { MissingBusinessNote(Modifier.padding(16.dp)) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MissingBusinessTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MissingBusinessNote(Modifier.padding(16.dp)) }
}
