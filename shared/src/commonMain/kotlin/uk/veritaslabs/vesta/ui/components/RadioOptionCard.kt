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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_buildings
import vesta.shared.generated.resources.ic_home
import vesta.shared.generated.resources.ic_home_heart
import vesta.shared.generated.resources.ic_topology_star_ring_2

/** Which of the two layouts to render — see [RadioOptionCard]. */
enum class RadioOptionCardOrientation {
    /** Icon beside the title/body, in a full-width row — phones and other narrow layouts. */
    Horizontal,

    /** Icon above the centered title/body — tablet/desktop grids. */
    Vertical,
}

/**
 * A tappable card showing a single radio-selectable option: an optional
 * leading [icon], a [title], optional [body] text, and a [RadioButton] in
 * the top-right corner. Pulls colors/shapes from [VestaTheme], including a
 * highlighted border/background and icon chip when [selected]. Intended for
 * use in a vertical list (or grid) of mutually-exclusive options — the
 * caller owns the selection state and group semantics.
 */
@Composable
fun RadioOptionCard(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: (@Composable () -> Unit)? = null,
    orientation: RadioOptionCardOrientation = RadioOptionCardOrientation.Horizontal,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val shape = MaterialTheme.shapes.large
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 2.dp else 1.dp
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    val cardModifier = modifier
        .fillMaxWidth()
        .background(backgroundColor, shape)
        .border(borderWidth, borderColor, shape)
        .clip(shape)
        .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
        .padding(16.dp)

    when (orientation) {
        RadioOptionCardOrientation.Horizontal -> Row(
            modifier = cardModifier,
            verticalAlignment = Alignment.Top,
        ) {
            icon?.let {
                RadioOptionIcon(icon = icon)
                Spacer(Modifier.width(12.dp))
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
            Spacer(Modifier.width(12.dp))
            RadioButton(selected = selected, onClick = null)
        }
        RadioOptionCardOrientation.Vertical -> Box(modifier = cardModifier) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                icon?.let {
                    RadioOptionIcon(icon = icon)
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
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun RadioOptionIcon(icon: @Composable () -> Unit) {
    val containerColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
    }
}

// A "Home Shape" option list, matching the reference design this component
// was built from — icon, title, body, and radio state per option.
@Composable
private fun HomeShapeOptionList(selected: String, onSelect: (String) -> Unit) {
    Column {
        RadioOptionCard(
            title = "Family",
            body = "A household with kids, parents, or multiple generations.",
            icon = { Icon(painterResource(Res.drawable.ic_home), contentDescription = null) },
            selected = selected == "Family",
            onClick = { onSelect("Family") },
        )
        Spacer(Modifier.height(12.dp))
        RadioOptionCard(
            title = "Flat Share",
            body = "Roommates or housemates splitting a place together.",
            icon = { Icon(painterResource(Res.drawable.ic_buildings), contentDescription = null) },
            selected = selected == "Flat Share",
            onClick = { onSelect("Flat Share") },
        )
        Spacer(Modifier.height(12.dp))
        RadioOptionCard(
            title = "Single + Support",
            body = "Living alone with occasional help from family or carers.",
            icon = { Icon(painterResource(Res.drawable.ic_home_heart), contentDescription = null) },
            selected = selected == "Single + Support",
            onClick = { onSelect("Single + Support") },
        )
        Spacer(Modifier.height(12.dp))
        RadioOptionCard(
            title = "Other",
            body = "Doesn't fit neatly — you can describe it your own way.",
            icon = { Icon(painterResource(Res.drawable.ic_topology_star_ring_2), contentDescription = null) },
            selected = selected == "Other",
            onClick = { onSelect("Other") },
        )
    }
}

// The same options as [HomeShapeOptionList], laid out as a 2-column grid of
// [RadioOptionCardOrientation.Vertical] cards — tablet/desktop width.
@Composable
private fun HomeShapeOptionGrid(selected: String, onSelect: (String) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RadioOptionCard(
                title = "Family",
                body = "A household with kids, parents, or multiple generations.",
                icon = { Icon(painterResource(Res.drawable.ic_home), contentDescription = null) },
                orientation = RadioOptionCardOrientation.Vertical,
                selected = selected == "Family",
                onClick = { onSelect("Family") },
                modifier = Modifier.weight(1f),
            )
            RadioOptionCard(
                title = "Flat Share",
                body = "Roommates or housemates splitting a place together.",
                icon = { Icon(painterResource(Res.drawable.ic_buildings), contentDescription = null) },
                orientation = RadioOptionCardOrientation.Vertical,
                selected = selected == "Flat Share",
                onClick = { onSelect("Flat Share") },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RadioOptionCard(
                title = "Single + Support",
                body = "Living alone with occasional help from family or carers.",
                icon = { Icon(painterResource(Res.drawable.ic_home_heart), contentDescription = null) },
                orientation = RadioOptionCardOrientation.Vertical,
                selected = selected == "Single + Support",
                onClick = { onSelect("Single + Support") },
                modifier = Modifier.weight(1f),
            )
            RadioOptionCard(
                title = "Other",
                body = "Doesn't fit neatly — you can describe it your own way.",
                icon = { Icon(painterResource(Res.drawable.ic_topology_star_ring_2), contentDescription = null) },
                orientation = RadioOptionCardOrientation.Vertical,
                selected = selected == "Other",
                onClick = { onSelect("Other") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RadioOptionCardPhoneContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        HomeShapeOptionList(selected = "Family", onSelect = {})
    }
}

@Composable
private fun RadioOptionCardTabletContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        HomeShapeOptionGrid(selected = "Family", onSelect = {})
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun RadioOptionCardPhoneLightPreview() {
    VestaTheme(darkTheme = false) { RadioOptionCardPhoneContent() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun RadioOptionCardPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { RadioOptionCardPhoneContent() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun RadioOptionCardTabletLightPreview() {
    VestaTheme(darkTheme = false) { RadioOptionCardTabletContent() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun RadioOptionCardTabletDarkPreview() {
    VestaTheme(darkTheme = true) { RadioOptionCardTabletContent() }
}
