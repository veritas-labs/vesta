package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.ui.theme.artPaper
import uk.veritaslabs.vesta.ui.theme.isDarkArt
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.empty_assistants
import vesta.shared.generated.resources.empty_assistants_dark
import vesta.shared.generated.resources.empty_cookbook
import vesta.shared.generated.resources.empty_cookbook_dark
import vesta.shared.generated.resources.empty_hubs
import vesta.shared.generated.resources.empty_hubs_dark
import vesta.shared.generated.resources.empty_merchants
import vesta.shared.generated.resources.empty_merchants_dark
import vesta.shared.generated.resources.empty_no_results
import vesta.shared.generated.resources.empty_no_results_dark
import vesta.shared.generated.resources.empty_pantry
import vesta.shared.generated.resources.empty_pantry_categories
import vesta.shared.generated.resources.empty_pantry_categories_dark
import vesta.shared.generated.resources.empty_pantry_dark
import vesta.shared.generated.resources.empty_quiet_day
import vesta.shared.generated.resources.empty_quiet_day_dark
import vesta.shared.generated.resources.empty_shopping
import vesta.shared.generated.resources.empty_shopping_dark
import vesta.shared.generated.resources.empty_synced_devices
import vesta.shared.generated.resources.empty_synced_devices_dark

/** Stable hooks for UI tests to target. */
object EmptyStateTestTags {
    const val Illustration = "empty_state_illustration"
}

/**
 * The line-art picture each empty state shows, in a light and a dark version (made from `branding/slides` by
 * `scripts/slides-to-vectors.py`).
 */
enum class EmptyStateArt(internal val light: DrawableResource, internal val dark: DrawableResource) {
    Pantry(Res.drawable.empty_pantry, Res.drawable.empty_pantry_dark),
    PantryCategories(Res.drawable.empty_pantry_categories, Res.drawable.empty_pantry_categories_dark),
    Shopping(Res.drawable.empty_shopping, Res.drawable.empty_shopping_dark),
    Cookbook(Res.drawable.empty_cookbook, Res.drawable.empty_cookbook_dark),
    Merchants(Res.drawable.empty_merchants, Res.drawable.empty_merchants_dark),
    SyncedDevices(Res.drawable.empty_synced_devices, Res.drawable.empty_synced_devices_dark),
    Hubs(Res.drawable.empty_hubs, Res.drawable.empty_hubs_dark),
    Assistants(Res.drawable.empty_assistants, Res.drawable.empty_assistants_dark),

    /** A search or filter that found nothing, as opposed to a list with nothing in it. */
    NoResults(Res.drawable.empty_no_results, Res.drawable.empty_no_results_dark),

    /** A day on the Calendar with no events. */
    QuietDay(Res.drawable.empty_quiet_day, Res.drawable.empty_quiet_day_dark),
}

/**
 * [art], square, drawn on its own paper colour so it blends into the screen. Decorative: the words next to it say it. It is [size] wide when
 * given, otherwise as wide as [modifier] allows.
 */
@Composable
fun EmptyStateIllustration(art: EmptyStateArt, modifier: Modifier = Modifier, size: Dp? = null) {
    val dark = isDarkArt()
    Image(
        painter = painterResource(if (dark) art.dark else art.light),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .let { if (size != null) it.size(size) else it.aspectRatio(1f) }
            .clip(RoundedCornerShape(24.dp))
            .background(artPaper(dark))
            .testTag(EmptyStateTestTags.Illustration),
    )
}

private val EmptyStateContentMaxWidth = 480.dp
private val EmptyStateIllustrationMaxSize = 420.dp

/**
 * An empty list's message: [art] above a [title] and an optional [body], laid out and styled like a welcome-tour slide (a large picture, a
 * headline, a line of explanation). Centred, and scrolls when the screen is too short to show all of it.
 */
@Composable
fun EmptyState(art: EmptyStateArt, title: String, modifier: Modifier = Modifier, body: String? = null) {
    Column(
        modifier = modifier
            .widthIn(max = EmptyStateContentMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EmptyStateIllustration(art, Modifier.widthIn(max = EmptyStateIllustrationMaxSize).fillMaxWidth())
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        body?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun EmptyStateIllustrationPhoneLightPreview() {
    VestaTheme(darkTheme = false) { EmptyState(EmptyStateArt.Pantry, "Your pantry is empty", body = "Items your household buys will show up here.") }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun EmptyStateIllustrationPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { EmptyState(EmptyStateArt.Pantry, "Your pantry is empty", body = "Items your household buys will show up here.") }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun EmptyStateIllustrationTabletLightPreview() {
    VestaTheme(darkTheme = false) { EmptyState(EmptyStateArt.Cookbook, "No recipes yet", body = "Add the first recipe your household cooks.") }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun EmptyStateIllustrationTabletDarkPreview() {
    VestaTheme(darkTheme = true) { EmptyState(EmptyStateArt.Cookbook, "No recipes yet", body = "Add the first recipe your household cooks.") }
}
