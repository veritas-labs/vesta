package uk.veritaslabs.vesta

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

import uk.veritaslabs.vesta.ui.screens.ProfilePickerScreen
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@Composable
@Preview
fun App() {
    VestaTheme {
        ProfilePickerScreen(profiles = emptyList())
    }
}