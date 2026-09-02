package uk.veritaslabs.vesta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Placeholder for the Default/High Contrast/Kid-Friendly variants described in
// docs/design — only the Default palette (light + dark) exists so far.
private val VestaLightColorScheme = lightColorScheme()
private val VestaDarkColorScheme = darkColorScheme()

private val VestaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun VestaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) VestaDarkColorScheme else VestaLightColorScheme,
        shapes = VestaShapes,
        typography = vestaTypography(),
        content = content,
    )
}
