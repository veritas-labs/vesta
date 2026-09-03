package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.veritaslabs.vesta.ui.theme.VestaTheme

private val AvatarSizeCompact = 40.dp
private val AvatarSizeExpanded = 96.dp

/** Which of the two layouts to render — see [AvatarButton]. */
enum class AvatarButtonOrientation {
    /** Avatar beside the label, in a full-width row — phones and other narrow layouts. */
    Horizontal,

    /** Avatar above the centered label — tablet/desktop grids. */
    Vertical,
}

/**
 * A generic tappable avatar + label control: a bordered, rounded card
 * pulling its colors/radius from [VestaTheme]. The caller supplies the
 * avatar's content (an image, initials, an icon, ...) via [avatar]; this
 * component only owns the card chrome, label, and per-[orientation] sizing —
 * see [ProfileAvatar] for the profile-picker avatar content.
 *
 * [onLongClick], if given, fires on a long-press (touch) — e.g. to surface a
 * contextual actions menu — alongside the normal [onClick] tap.
 */
@Composable
fun AvatarButton(
    label: String,
    avatar: @Composable (size: Dp) -> Unit,
    modifier: Modifier = Modifier,
    orientation: AvatarButtonOrientation = AvatarButtonOrientation.Horizontal,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.small
    val cardModifier = modifier
        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
        .clip(shape)
        .combinedClickable(onLongClick = onLongClick, onClick = onClick)

    when (orientation) {
        AvatarButtonOrientation.Horizontal -> Row(
            modifier = cardModifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            avatar(AvatarSizeCompact)
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        AvatarButtonOrientation.Vertical -> Column(
            modifier = cardModifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            avatar(AvatarSizeExpanded)
            Spacer(Modifier.height(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// A non-profile use of the same button: an icon avatar rather than
// initials, showing the component isn't profile-specific.
@Composable
private fun SettingsIconAvatar(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text("⚙", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AvatarButtonPhoneContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        AvatarButton(label = "Settings", avatar = { size -> SettingsIconAvatar(size) })
    }
}

@Composable
private fun AvatarButtonTabletContent() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        AvatarButton(
            label = "Settings",
            avatar = { size -> SettingsIconAvatar(size) },
            orientation = AvatarButtonOrientation.Vertical,
        )
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AvatarButtonPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AvatarButtonPhoneContent() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AvatarButtonPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AvatarButtonPhoneContent() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AvatarButtonTabletLightPreview() {
    VestaTheme(darkTheme = false) { AvatarButtonTabletContent() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AvatarButtonTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AvatarButtonTabletContent() }
}
