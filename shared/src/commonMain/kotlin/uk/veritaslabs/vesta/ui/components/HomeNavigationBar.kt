package uk.veritaslabs.vesta.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.home_dashboard_nav_home
import vesta.shared.generated.resources.home_dashboard_nav_members
import vesta.shared.generated.resources.ic_home
import vesta.shared.generated.resources.ic_users

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HomeNavigationBarTestTags {
    const val HomeItem = "home_nav_home"
    const val MembersItem = "home_nav_members"
}

/** The tabs of a selected Home. */
enum class HomeTab { Home, Members }

/** The bottom navigation bar shared by a Home's tabs: [selected] is highlighted, and the others call their click handler. */
@Composable
fun HomeNavigationBar(
    selected: HomeTab,
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onMembersClick: () -> Unit = {},
) {
    NavigationBar(
        modifier = modifier.shadow(elevation = 8.dp),
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
            selected = selected == HomeTab.Home,
            onClick = onHomeClick,
            icon = { Icon(painterResource(Res.drawable.ic_home), contentDescription = null) },
            label = { Text(stringResource(Res.string.home_dashboard_nav_home)) },
            colors = itemColors,
            modifier = Modifier.testTag(HomeNavigationBarTestTags.HomeItem),
        )
        NavigationBarItem(
            selected = selected == HomeTab.Members,
            onClick = onMembersClick,
            icon = { Icon(painterResource(Res.drawable.ic_users), contentDescription = null) },
            label = { Text(stringResource(Res.string.home_dashboard_nav_members)) },
            colors = itemColors,
            modifier = Modifier.testTag(HomeNavigationBarTestTags.MembersItem),
        )
    }
}

@Preview(name = "Light", group = "Phone", widthDp = 360)
@Composable
private fun HomeNavigationBarPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomeNavigationBar(selected = HomeTab.Home) }
}

@Preview(name = "Dark", group = "Phone", widthDp = 360)
@Composable
private fun HomeNavigationBarPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomeNavigationBar(selected = HomeTab.Members) }
}

@Preview(name = "Light", group = "Tablet", widthDp = 840)
@Composable
private fun HomeNavigationBarTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomeNavigationBar(selected = HomeTab.Home) }
}

@Preview(name = "Dark", group = "Tablet", widthDp = 840)
@Composable
private fun HomeNavigationBarTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomeNavigationBar(selected = HomeTab.Members) }
}
