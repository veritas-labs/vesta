package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.added_to_pantry_animation_description
import vesta.shared.generated.resources.added_to_pantry_close
import vesta.shared.generated.resources.added_to_pantry_message
import vesta.shared.generated.resources.added_to_pantry_title

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object AddedToPantryTestTags {
    const val Animation = "added_to_pantry_animation"
    const val CloseButton = "added_to_pantry_close_button"
}

/**
 * The celebration after "Add checked items to Pantry" on the shopping list: a Lottie animation (items drop into a
 * Pantry box, then a check pops) coloured from the app theme, saying that [addedCount] items are now in the Pantry,
 * with a Close button that calls [onClose].
 */
@Composable
fun AddedToPantryScreen(
    addedCount: Int,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PantryAnimation(modifier = Modifier.size(280.dp).testTag(AddedToPantryTestTags.Animation))
        Text(
            text = stringResource(Res.string.added_to_pantry_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = pluralStringResource(Res.plurals.added_to_pantry_message, addedCount, addedCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        Button(
            onClick = onClose,
            modifier = Modifier.widthIn(min = 200.dp).testTag(AddedToPantryTestTags.CloseButton),
        ) { Text(stringResource(Res.string.added_to_pantry_close)) }
    }
}

/** Plays `files/added_to_pantry.json` once, recoloured with the current theme (see [themedLottie]); blank until it has loaded. */
@Composable
private fun PantryAnimation(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val palette = mapOf(
        "BOX_FRONT" to colors.secondary,
        "BOX_BACK" to colors.onSecondaryContainer,
        "BOX_LABEL" to colors.secondaryContainer,
        "ITEM_A" to colors.primary,
        "ITEM_B" to colors.tertiary,
        "ITEM_C" to colors.onSurfaceVariant,
        "ITEM_BAND" to colors.background,
        "BADGE" to colors.tertiary,
        "ON_BADGE" to colors.onTertiary,
        "PRIMARY" to colors.primary,
        "TERTIARY" to colors.tertiary,
    )
    val json by produceState<String?>(null, palette) {
        value = themedLottie(Res.readBytes("files/added_to_pantry.json").decodeToString(), palette)
    }
    val spec = json
    if (spec == null) return
    val composition by rememberLottieComposition(spec) { LottieCompositionSpec.JsonString(spec) }
    val progress by animateLottieCompositionAsState(composition, iterations = 1)
    val description = stringResource(Res.string.added_to_pantry_animation_description)
    Image(
        painter = rememberLottiePainter(composition = composition, progress = { progress }),
        contentDescription = description,
        modifier = modifier,
    )
}

/**
 * [template] with each quoted `"__NAME__"` placeholder replaced by the Lottie colour (an `[r,g,b,1]` array) of
 * [palette]`[NAME]`, so one animation file takes on whichever theme is showing.
 */
internal fun themedLottie(template: String, palette: Map<String, Color>): String =
    palette.entries.fold(template) { json, (name, color) ->
        json.replace("\"__${name}__\"", "[${color.red},${color.green},${color.blue},1]")
    }

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AddedToPantryScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AddedToPantryScreen(addedCount = 5) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AddedToPantryScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AddedToPantryScreen(addedCount = 5) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AddedToPantryScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { AddedToPantryScreen(addedCount = 5) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AddedToPantryScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AddedToPantryScreen(addedCount = 5) }
}
