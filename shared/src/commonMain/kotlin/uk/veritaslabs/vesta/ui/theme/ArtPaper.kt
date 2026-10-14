package uk.veritaslabs.vesta.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The paper colours the line-art illustrations were recoloured to by `scripts/slides-to-vectors.py`: keep in step with the backgrounds in
 * [VestaTheme] and with `THEMES` in that script. Drawn behind a picture, so a theme whose background differs (Halloween) still shows it as
 * a framed card.
 */
internal val ArtLightPaper = Color(0xFFF5F4ED)
internal val ArtDarkPaper = Color(0xFF15130F)

/** Whether the illustrations should be their dark versions: when the screen's background is dark, whichever theme made it so. */
@Composable
internal fun isDarkArt(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

internal fun artPaper(dark: Boolean): Color = if (dark) ArtDarkPaper else ArtLightPaper
