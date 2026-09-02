package uk.veritaslabs.vesta.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.raleway_black
import vesta.shared.generated.resources.raleway_black_italic
import vesta.shared.generated.resources.raleway_bold
import vesta.shared.generated.resources.raleway_bold_italic
import vesta.shared.generated.resources.raleway_extrabold
import vesta.shared.generated.resources.raleway_extrabold_italic
import vesta.shared.generated.resources.raleway_extralight
import vesta.shared.generated.resources.raleway_extralight_italic
import vesta.shared.generated.resources.raleway_light
import vesta.shared.generated.resources.raleway_light_italic
import vesta.shared.generated.resources.raleway_medium
import vesta.shared.generated.resources.raleway_medium_italic
import vesta.shared.generated.resources.raleway_regular
import vesta.shared.generated.resources.raleway_regular_italic
import vesta.shared.generated.resources.raleway_semibold
import vesta.shared.generated.resources.raleway_semibold_italic
import vesta.shared.generated.resources.raleway_thin
import vesta.shared.generated.resources.raleway_thin_italic

@Composable
fun ralewayFontFamily(): FontFamily = FontFamily(
    Font(Res.font.raleway_thin, FontWeight.Thin, FontStyle.Normal),
    Font(Res.font.raleway_thin_italic, FontWeight.Thin, FontStyle.Italic),
    Font(Res.font.raleway_extralight, FontWeight.ExtraLight, FontStyle.Normal),
    Font(Res.font.raleway_extralight_italic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(Res.font.raleway_light, FontWeight.Light, FontStyle.Normal),
    Font(Res.font.raleway_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(Res.font.raleway_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.raleway_regular_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.raleway_medium, FontWeight.Medium, FontStyle.Normal),
    Font(Res.font.raleway_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(Res.font.raleway_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(Res.font.raleway_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(Res.font.raleway_bold, FontWeight.Bold, FontStyle.Normal),
    Font(Res.font.raleway_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(Res.font.raleway_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
    Font(Res.font.raleway_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(Res.font.raleway_black, FontWeight.Black, FontStyle.Normal),
    Font(Res.font.raleway_black_italic, FontWeight.Black, FontStyle.Italic),
)

@Composable
fun vestaTypography(): Typography {
    val raleway = ralewayFontFamily()
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.copy(fontFamily = raleway),
        displayMedium = default.displayMedium.copy(fontFamily = raleway),
        displaySmall = default.displaySmall.copy(fontFamily = raleway),
        headlineLarge = default.headlineLarge.copy(fontFamily = raleway),
        headlineMedium = default.headlineMedium.copy(fontFamily = raleway),
        headlineSmall = default.headlineSmall.copy(fontFamily = raleway),
        titleLarge = default.titleLarge.copy(fontFamily = raleway),
        titleMedium = default.titleMedium.copy(fontFamily = raleway),
        titleSmall = default.titleSmall.copy(fontFamily = raleway),
        bodyLarge = default.bodyLarge.copy(fontFamily = raleway),
        bodyMedium = default.bodyMedium.copy(fontFamily = raleway),
        bodySmall = default.bodySmall.copy(fontFamily = raleway),
        labelLarge = default.labelLarge.copy(fontFamily = raleway),
        labelMedium = default.labelMedium.copy(fontFamily = raleway),
        labelSmall = default.labelSmall.copy(fontFamily = raleway),
    )
}
