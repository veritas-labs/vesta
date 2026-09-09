package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_clipboard_text
import vesta.shared.generated.resources.ic_link

private val ActionCardIconSize = 48.dp

/** Which of the two layouts to render — see [ActionCard]. */
enum class ActionCardOrientation {
    /** Icon beside the title/body, in a full-width row — phones and other narrow layouts. */
    Horizontal,

    /** Icon above the centered title/body — tablet/desktop grids. */
    Vertical,
}

/**
 * A large, tappable card for a single non-exclusive action: a leading
 * [icon], a [title], and optional [body] text underneath it — no selection
 * state, unlike [RadioOptionCard]. Pulls colors/shapes from [VestaTheme].
 * Intended for a short, vertical list of unrelated actions (e.g. entry
 * points into different flows), where [RadioOptionCard]'s mutually-exclusive
 * radio semantics don't apply.
 */
@Composable
fun ActionCard(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: (@Composable () -> Unit)? = null,
    orientation: ActionCardOrientation = ActionCardOrientation.Horizontal,
    onClick: () -> Unit = {},
) {
    val shape = MaterialTheme.shapes.large
    val cardModifier = modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .clip(shape)
        .selectable(selected = false, onClick = onClick, role = Role.Button)
        .padding(20.dp)

    when (orientation) {
        ActionCardOrientation.Horizontal -> Row(
            modifier = cardModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                ActionCardIcon(icon = icon)
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                body?.let {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ActionCardOrientation.Vertical -> Column(
            modifier = cardModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            icon?.let {
                ActionCardIcon(icon = icon)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            body?.let {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ActionCardIcon(icon: @Composable () -> Unit) {
    val containerColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .size(ActionCardIconSize)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
    }
}

@Composable
private fun ActionCardPhoneContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        ActionCard(
            title = "Generate device pairing link",
            body = "Create a link this device can share with a new one to pair it.",
            icon = { Icon(painterResource(Res.drawable.ic_link), contentDescription = null) },
        )
        Spacer(Modifier.height(12.dp))
        ActionCard(
            title = "Paste a device pairing link",
            body = "Pair this device using a link generated on another device.",
            icon = { Icon(painterResource(Res.drawable.ic_clipboard_text), contentDescription = null) },
        )
    }
}

@Composable
private fun ActionCardTabletContent() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ActionCard(
            title = "Generate device pairing link",
            body = "Create a link this device can share with a new one to pair it.",
            icon = { Icon(painterResource(Res.drawable.ic_link), contentDescription = null) },
            orientation = ActionCardOrientation.Vertical,
            modifier = Modifier.weight(1f),
        )
        ActionCard(
            title = "Paste a device pairing link",
            body = "Pair this device using a link generated on another device.",
            icon = { Icon(painterResource(Res.drawable.ic_clipboard_text), contentDescription = null) },
            orientation = ActionCardOrientation.Vertical,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ActionCardPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ActionCardPhoneContent() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ActionCardPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ActionCardPhoneContent() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ActionCardTabletLightPreview() {
    VestaTheme(darkTheme = false) { ActionCardTabletContent() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ActionCardTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ActionCardTabletContent() }
}
