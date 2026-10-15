package uk.veritaslabs.vesta.ui.theme

import androidx.compose.runtime.Composable

/**
 * Tints the system bar icons to stay readable: dark icons over the light theme, light icons over the dark one.
 * Follows the app's chosen theme, which can differ from the device's. A no-op where there are no system bars to tint.
 */
@Composable
expect fun SystemBarsAppearance(darkTheme: Boolean)
