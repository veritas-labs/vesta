package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import uk.veritaslabs.vesta.x0x.X0xConnectivity
import uk.veritaslabs.vesta.x0x.X0xConnectivityIndicator
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.home_dashboard_title
import vesta.shared.generated.resources.ic_pantry
import vesta.shared.generated.resources.pantry_title

private val HomeDashboardTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HomeDashboardTestTags {
    const val PantryTile = "home_dashboard_pantry_tile"
    const val X0xIndicator = "home_dashboard_x0x_indicator"
}

private data class DashboardTile(
    val testTag: String,
    val label: StringResource,
    val icon: DrawableResource,
    val onClick: () -> Unit,
)

/**
 * Landing page for a selected Home: a two-column grid of tiles, each an icon with a label, that
 * open the Home's areas. Only the Pantry ([onPantryClick]) exists so far. The app bar's trailing
 * indicator shows [x0xConnectivity].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    modifier: Modifier = Modifier,
    x0xConnectivity: X0xConnectivity = X0xConnectivity.Connecting,
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

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.home_dashboard_title)) },
                actions = {
                    X0xConnectivityIndicator(
                        x0xConnectivity,
                        modifier = Modifier.padding(end = contentPadding).testTag(HomeDashboardTestTags.X0xIndicator),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding),
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
