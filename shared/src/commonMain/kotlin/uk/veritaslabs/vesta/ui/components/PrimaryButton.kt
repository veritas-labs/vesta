package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uk.veritaslabs.vesta.ui.theme.VestaTheme

private val PrimaryButtonMaxWidth = 320.dp
private val PrimaryButtonHeight = 60.dp

/**
 * The app's full-width filled call-to-action button: capped at
 * [PrimaryButtonMaxWidth] wide and [PrimaryButtonHeight] tall, with its
 * corner radius pulled from [VestaTheme]'s shapes rather than Material3's
 * default pill shape.
 */
@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = PrimaryButtonMaxWidth)
            .height(PrimaryButtonHeight),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PrimaryButtonPhoneContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        PrimaryButton(label = "Create profile")
    }
}

@Composable
private fun PrimaryButtonTabletContent() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        PrimaryButton(label = "Create profile")
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun PrimaryButtonPhoneLightPreview() {
    VestaTheme(darkTheme = false) { PrimaryButtonPhoneContent() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun PrimaryButtonPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { PrimaryButtonPhoneContent() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PrimaryButtonTabletLightPreview() {
    VestaTheme(darkTheme = false) { PrimaryButtonTabletContent() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun PrimaryButtonTabletDarkPreview() {
    VestaTheme(darkTheme = true) { PrimaryButtonTabletContent() }
}
