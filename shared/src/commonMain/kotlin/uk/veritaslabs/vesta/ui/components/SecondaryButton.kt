package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uk.veritaslabs.vesta.ui.theme.VestaTheme

private val SecondaryButtonMaxWidth = 320.dp
private val SecondaryButtonHeight = 60.dp

/**
 * The app's full-width outlined button: same footprint as [PrimaryButton]
 * (capped at [SecondaryButtonMaxWidth] wide and [SecondaryButtonHeight]
 * tall, same corner radius from [VestaTheme]'s shapes) but styled as the
 * lower-emphasis alternative to a screen's primary call-to-action.
 */
@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = SecondaryButtonMaxWidth)
            .height(SecondaryButtonHeight),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryButtonPhoneContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        SecondaryButton(label = "Join a home")
    }
}

@Composable
private fun SecondaryButtonTabletContent() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        SecondaryButton(label = "Join a home")
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SecondaryButtonPhoneLightPreview() {
    VestaTheme(darkTheme = false) { SecondaryButtonPhoneContent() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SecondaryButtonPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { SecondaryButtonPhoneContent() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SecondaryButtonTabletLightPreview() {
    VestaTheme(darkTheme = false) { SecondaryButtonTabletContent() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SecondaryButtonTabletDarkPreview() {
    VestaTheme(darkTheme = true) { SecondaryButtonTabletContent() }
}
