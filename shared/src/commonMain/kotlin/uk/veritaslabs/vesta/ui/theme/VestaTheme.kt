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

// Halloween: pumpkin orange on near-black, with candle yellow as the third accent.
// Always dark; it ignores the Light / Dark / System choice.
private val HalloweenColorScheme = darkColorScheme(
    primary = Color(0xFFFF7A00),
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF5C2A00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    inversePrimary = Color(0xFFB34F00),
    secondary = Color(0xFFFFA64D),
    onSecondary = Color(0xFF2B1400),
    secondaryContainer = Color(0xFF3D2200),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFFFFC933),
    onTertiary = Color(0xFF2B2000),
    tertiaryContainer = Color(0xFF4A3A00),
    onTertiaryContainer = Color(0xFFFFE699),
    error = Color(0xFFFF5449),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF5C0F0A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF080605),
    onBackground = Color(0xFFFFE9D6),
    surface = Color(0xFF080605),
    onSurface = Color(0xFFFFE9D6),
    surfaceVariant = Color(0xFF3A2A1C),
    onSurfaceVariant = Color(0xFFE6C9AC),
    surfaceTint = Color(0xFFFF7A00),
    inverseSurface = Color(0xFFFFE9D6),
    inverseOnSurface = Color(0xFF1F1610),
    outline = Color(0xFFB8875A),
    outlineVariant = Color(0xFF4A3524),
    surfaceBright = Color(0xFF2E2118),
    surfaceDim = Color(0xFF080605),
    surfaceContainerLowest = Color(0xFF040302),
    surfaceContainerLow = Color(0xFF120D09),
    surfaceContainer = Color(0xFF1A130D),
    surfaceContainerHigh = Color(0xFF241A12),
    surfaceContainerHighest = Color(0xFF2F2217),
)

private val VestaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun VestaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appTheme: AppTheme = AppTheme.Vesta,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = when (appTheme) {
            AppTheme.Halloween -> HalloweenColorScheme
            AppTheme.Vesta -> if (darkTheme) VestaDarkColorScheme else VestaLightColorScheme
        },
        shapes = VestaShapes,
        typography = vestaTypography(),
        content = content,
    )
}
