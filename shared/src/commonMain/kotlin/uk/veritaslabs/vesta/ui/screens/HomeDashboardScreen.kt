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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
import vesta.shared.generated.resources.home_dashboard_back
import vesta.shared.generated.resources.home_dashboard_nav_home
import vesta.shared.generated.resources.home_dashboard_nav_settings
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_home
import vesta.shared.generated.resources.ic_pantry
import vesta.shared.generated.resources.ic_settings
import vesta.shared.generated.resources.pantry_title

private val HomeDashboardTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HomeDashboardTestTags {
    const val BackButton = "home_dashboard_back_button"
    const val PantryTile = "home_dashboard_pantry_tile"
    const val X0xIndicator = "home_dashboard_x0x_indicator"
    const val HomeNavItem = "home_dashboard_nav_home"
    const val SettingsNavItem = "home_dashboard_nav_settings"
}

private data class DashboardTile(
    val testTag: String,
    val label: StringResource,
    val icon: DrawableResource,
    val onClick: () -> Unit,
)

/**
 * Landing page for a selected Home: a two-column grid of tiles, each an icon with a label, that
 * open the Home's areas. Only the Pantry ([onPantryClick]) exists so far. The app bar is titled with
 * [homeName], its back arrow calls [onBack] (to the Home picker), and its trailing indicator shows
 * [x0xConnectivity]. The bottom nav bar has Home (selected, [onHomeClick]) and Settings
 * ([onSettingsClick]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    homeName: String,
    modifier: Modifier = Modifier,
    x0xConnectivity: X0xConnectivity = X0xConnectivity.Connecting,
    onBack: () -> Unit = {},
    onPantryClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
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
                title = { Text(homeName) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(HomeDashboardTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.home_dashboard_back),
                        )
                    }
                },
                actions = {
                    X0xConnectivityIndicator(
                        x0xConnectivity,
                        modifier = Modifier.padding(end = contentPadding).testTag(HomeDashboardTestTags.X0xIndicator),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
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
            NavigationBar(
                modifier = Modifier.shadow(elevation = 8.dp),
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                    unselectedTextColor = MaterialTheme.colorScheme.onBackground,
                )
                NavigationBarItem(
                    selected = true,
                    onClick = onHomeClick,
                    icon = { Icon(painterResource(Res.drawable.ic_home), contentDescription = null) },
                    label = { Text(stringResource(Res.string.home_dashboard_nav_home)) },
                    colors = itemColors,
                    modifier = Modifier.testTag(HomeDashboardTestTags.HomeNavItem),
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettingsClick,
                    icon = { Icon(painterResource(Res.drawable.ic_settings), contentDescription = null) },
                    label = { Text(stringResource(Res.string.home_dashboard_nav_settings)) },
                    colors = itemColors,
                    modifier = Modifier.testTag(HomeDashboardTestTags.SettingsNavItem),
                )
            }
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeDashboardScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomeDashboardScreen(homeName = "The Johnsons") }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeDashboardScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomeDashboardScreen(homeName = "The Johnsons") }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeDashboardScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomeDashboardScreen(homeName = "The Johnsons") }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeDashboardScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomeDashboardScreen(homeName = "The Johnsons") }
}
