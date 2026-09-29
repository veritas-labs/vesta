package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.ActionCard
import uk.veritaslabs.vesta.ui.components.ActionCardOrientation
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.home_dashboard_title
import vesta.shared.generated.resources.ic_pantry
import vesta.shared.generated.resources.pantry_title

private val HomeDashboardTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HomeDashboardTestTags {
    const val PantryTile = "home_dashboard_pantry_tile"
}

private data class DashboardTile(
    val testTag: String,
    val label: StringResource,
    val icon: DrawableResource,
    val onClick: () -> Unit,
)

/**
 * Landing page for a selected Home: a two-column grid of tiles, each an icon with a label, that
 * open the Home's areas. Only the Pantry ([onPantryClick]) exists so far.
 */
@Composable
fun HomeDashboardScreen(
    modifier: Modifier = Modifier,
    onPantryClick: () -> Unit = {},
) {
    val tiles = listOf(
        DashboardTile(HomeDashboardTestTags.PantryTile, Res.string.pantry_title, Res.drawable.ic_pantry, onPantryClick),
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val contentPadding = if (maxWidth >= HomeDashboardTabletBreakpoint) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = contentPadding),
        ) {
            Spacer(Modifier.height(contentPadding))
            Text(
                text = stringResource(Res.string.home_dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(contentPadding))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(tiles, key = { it.testTag }) { tile ->
                    ActionCard(
                        title = stringResource(tile.label),
                        icon = { Icon(painterResource(tile.icon), contentDescription = null) },
                        orientation = ActionCardOrientation.Vertical,
                        modifier = Modifier.testTag(tile.testTag),
                        onClick = tile.onClick,
                    )
                }
            }
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeDashboardScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomeDashboardScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeDashboardScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomeDashboardScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeDashboardScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomeDashboardScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeDashboardScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomeDashboardScreen() }
}
