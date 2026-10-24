package uk.veritaslabs.vesta.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.marcellus_regular
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
fun marcellusFontFamily(): FontFamily = FontFamily(
    Font(Res.font.marcellus_regular, FontWeight.Normal, FontStyle.Normal),
)

@Composable
fun vestaTypography(): Typography {
    val marcellus = marcellusFontFamily()
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.copy(fontFamily = marcellus),
        displayMedium = default.displayMedium.copy(fontFamily = marcellus),
        displaySmall = default.displaySmall.copy(fontFamily = marcellus),
        headlineLarge = default.headlineLarge.copy(fontFamily = marcellus),
        headlineMedium = default.headlineMedium.copy(fontFamily = marcellus),
        headlineSmall = default.headlineSmall.copy(fontFamily = marcellus),
        titleLarge = default.titleLarge.copy(fontFamily = marcellus),
        titleMedium = default.titleMedium.copy(fontFamily = marcellus),
        titleSmall = default.titleSmall.copy(fontFamily = marcellus),
        bodyLarge = default.bodyLarge.copy(fontFamily = marcellus),
        bodyMedium = default.bodyMedium.copy(fontFamily = marcellus),
        bodySmall = default.bodySmall.copy(fontFamily = marcellus),
        labelLarge = default.labelLarge.copy(fontFamily = marcellus, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = default.labelMedium.copy(fontFamily = marcellus),
        labelSmall = default.labelSmall.copy(fontFamily = marcellus),
    )
}

/**
 * Style for the tag line on list items (e.g. shopping list tags): a dedicated 13sp variant of [Typography.labelSmall]
 * (which is too small there), so the size is set in the theme rather than changing labelSmall for every other screen.
 * It keeps the theme's Marcellus family, which ships only a Regular weight.
 */
val Typography.itemTag: TextStyle
    get() = labelSmall.copy(fontSize = 13.sp)
