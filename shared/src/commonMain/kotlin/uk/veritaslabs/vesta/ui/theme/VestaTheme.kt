package uk.veritaslabs.vesta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Placeholder for the Default/High Contrast/Kid-Friendly variants described in
// docs/design — only the Default palette (light + dark) exists so far.
// Every role is set explicitly: any role left out falls back to Material 3's baseline,
// which is purple-tinted and clashes with the warm Vesta palette.
private val VestaLightColorScheme = lightColorScheme(
    primary = Color(0xFFDDC6AF),
    onPrimary = Color(0xFF3D2E1F),
    primaryContainer = Color(0xFFEFE0CF),
    onPrimaryContainer = Color(0xFF2A1D0F),
    inversePrimary = Color(0xFFDDC6AF),
    secondary = Color(0xFF6B5D4D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE1D1),
    onSecondaryContainer = Color(0xFF261B0E),
    tertiary = Color(0xFF5C6340),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFE8BC),
    onTertiaryContainer = Color(0xFF191E04),
    background = Color(0xFFF5F4ED),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFF5F4ED),
    onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFE9E2D6),
    onSurfaceVariant = Color(0xFF4C463C),
    surfaceTint = Color(0xFFDDC6AF),
    inverseSurface = Color(0xFF32302A),
    inverseOnSurface = Color(0xFFF5F0E7),
    outline = Color(0xFF7E766A),
    outlineVariant = Color(0xFFD0C8BB),
    surfaceBright = Color(0xFFFBFAF5),
    surfaceDim = Color(0xFFDDDAD1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F8F2),
    surfaceContainer = Color(0xFFF1EFE7),
    surfaceContainerHigh = Color(0xFFEBE8DF),
    surfaceContainerHighest = Color(0xFFE5E2D9),
)
private val VestaDarkColorScheme = darkColorScheme(
    primary = Color(0xFFDDC6AF),
    onPrimary = Color(0xFF3D2E1F),
    primaryContainer = Color(0xFF56432E),
    onPrimaryContainer = Color(0xFFFBDFC3),
    inversePrimary = Color(0xFF6F5A44),
    secondary = Color(0xFFD4C4B0),
    onSecondary = Color(0xFF392F20),
    secondaryContainer = Color(0xFF504536),
    onSecondaryContainer = Color(0xFFF0E0CC),
    tertiary = Color(0xFFC3CC9F),
    onTertiary = Color(0xFF2E3414),
    tertiaryContainer = Color(0xFF444B29),
    onTertiaryContainer = Color(0xFFDFE8BC),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFE8E2D8),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFE8E2D8),
    surfaceVariant = Color(0xFF4C463C),
    onSurfaceVariant = Color(0xFFCFC6B8),
    surfaceTint = Color(0xFFDDC6AF),
    inverseSurface = Color(0xFFE8E2D8),
    inverseOnSurface = Color(0xFF32302A),
    outline = Color(0xFF998F82),
    outlineVariant = Color(0xFF4C463C),
    surfaceBright = Color(0xFF3C3832),
    surfaceDim = Color(0xFF15130F),
    surfaceContainerLowest = Color(0xFF100E0A),
    surfaceContainerLow = Color(0xFF1D1B16),
    surfaceContainer = Color(0xFF211F1A),
    surfaceContainerHigh = Color(0xFF2C2A24),
    surfaceContainerHighest = Color(0xFF37352F),
)

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
