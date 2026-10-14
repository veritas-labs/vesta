package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.ui.components.ConfirmDeleteDialog
import vesta.shared.generated.resources.home_delete_confirm_title
import vesta.shared.generated.resources.home_delete_confirm_body
import vesta.shared.generated.resources.home_delete_confirm
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.HomeNavigationBar
import uk.veritaslabs.vesta.ui.components.HomeTab
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xConnectivity
import uk.veritaslabs.vesta.x0x.X0xConnectivityIndicator
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.home_dashboard_back
import vesta.shared.generated.resources.calendar_title
import vesta.shared.generated.resources.home_dashboard_tasks_body
import vesta.shared.generated.resources.home_dashboard_tasks_overdue
import vesta.shared.generated.resources.home_dashboard_tasks_today
import vesta.shared.generated.resources.ic_checklist
import vesta.shared.generated.resources.tasks_title
import vesta.shared.generated.resources.cookbook_title
import vesta.shared.generated.resources.ic_calendar
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_cookbook
import vesta.shared.generated.resources.ic_home
import vesta.shared.generated.resources.home_dashboard_cookbook_body
import vesta.shared.generated.resources.home_dashboard_calendar_body
import vesta.shared.generated.resources.home_dashboard_marketplace_body
import vesta.shared.generated.resources.home_dashboard_meal_planner_body
import vesta.shared.generated.resources.home_dashboard_pantry_body
import vesta.shared.generated.resources.home_dashboard_shopping_body
import vesta.shared.generated.resources.ic_meal_planner
import vesta.shared.generated.resources.meal_planner_title
import vesta.shared.generated.resources.ic_merchants
import vesta.shared.generated.resources.marketplace_title
import vesta.shared.generated.resources.ic_dots_vertical
import vesta.shared.generated.resources.ic_pantry
import vesta.shared.generated.resources.ic_shopping_cart
import vesta.shared.generated.resources.shopping_title
import vesta.shared.generated.resources.home_dashboard_more_options
import vesta.shared.generated.resources.hub_menu_item
import vesta.shared.generated.resources.members_leave
import vesta.shared.generated.resources.members_leave_blocked_body
import vesta.shared.generated.resources.members_leave_blocked_ok
import vesta.shared.generated.resources.members_leave_blocked_title
import vesta.shared.generated.resources.members_leave_cancel
import vesta.shared.generated.resources.members_leave_confirm
import vesta.shared.generated.resources.members_leave_confirm_body
import vesta.shared.generated.resources.members_leave_confirm_title
import vesta.shared.generated.resources.onboarding_delete_home
import vesta.shared.generated.resources.onboarding_edit_home
import vesta.shared.generated.resources.pantry_title

private val HomeDashboardTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HomeDashboardTestTags {
    const val BackButton = "home_dashboard_back_button"
    const val PantryTile = "home_dashboard_pantry_tile"
    const val ShoppingTile = "home_dashboard_shopping_tile"
    const val CookbookTile = "home_dashboard_cookbook_tile"
    const val MealPlannerTile = "home_dashboard_meal_planner_tile"
    const val CalendarTile = "home_dashboard_calendar_tile"
    const val TasksTile = "home_dashboard_tasks_tile"
    const val TasksTileBody = "home_dashboard_tasks_tile_body"
    const val MarketplaceTile = "home_dashboard_marketplace_tile"
    const val X0xIndicator = "home_dashboard_x0x_indicator"
    const val OverflowButton = "home_dashboard_overflow_button"
    const val EditMenuItem = "home_dashboard_edit_menu_item"
    const val LeaveMenuItem = "home_dashboard_leave_menu_item"
    const val DeleteMenuItem = "home_dashboard_delete_menu_item"
    const val HubMenuItem = "home_dashboard_hub_menu_item"
    const val ConfirmLeave = "home_dashboard_leave_confirm"
    const val BlockedDialog = "home_dashboard_leave_blocked"
}

private data class DashboardTile(
    val testTag: String,
    val label: StringResource,
    val body: StringResource,
    val icon: DrawableResource,
    val accent: Color,
    val onClick: () -> Unit,
    /** Words to show instead of [body], such as how many tasks are overdue; [bodyIsAlert] draws them in the error colour. */
    val bodyOverride: String? = null,
    val bodyIsAlert: Boolean = false,
)

/** The Pantry tile's blue, shared with the Pantry screen's app bar shadow. */
internal val PantryAccent = Color(0xFF6F93B5)

/** The shopping tile's gold, shared with the shopping screens' app bar shadow. */
internal val ShoppingAccent = Color(0xFFC9973A)

/**
 * A colour for each tile, fixed rather than from the theme (which has too few accents to tell six areas apart) and muted enough to sit
 * on both the light and the dark background. It is the bar down a tile's left side and the icon's circle.
 */
private val TileAccents = mapOf(
    HomeDashboardTestTags.PantryTile to PantryAccent, // blue
    HomeDashboardTestTags.ShoppingTile to ShoppingAccent, // gold
    HomeDashboardTestTags.CookbookTile to Color(0xFFD9694F), // coral
    HomeDashboardTestTags.MealPlannerTile to Color(0xFFD9743A), // terracotta
    HomeDashboardTestTags.CalendarTile to Color(0xFF8FA07A), // sage
    HomeDashboardTestTags.TasksTile to Color(0xFF6FA5A0), // teal
    HomeDashboardTestTags.MarketplaceTile to Color(0xFFA89A6E), // khaki
)

/**
 * Landing page for a selected Home: a two-column grid of coloured tiles, each a round icon with a name and a line on what it is for, that
 * open the Home's areas. The Pantry ([onPantryClick]), the shopping list ([onShoppingClick]) and the cookbook ([onCookbookClick]), the meal planner ([onMealPlannerClick]), the calendar ([onCalendarClick]) and the marketplace ([onMarketplaceClick]) so far. The app bar is titled with
 * [homeName], its back arrow calls [onBack] (to the Home picker), and its trailing indicator shows
 * [x0xConnectivity]. The bottom nav bar has Home (selected, [onHomeClick]) and Members
 * ([onMembersClick]), with a badge of [membersBadge] people waiting to join, for an admin.
 *
 * The app bar's overflow menu has Manage hubs ([onHubClick]), Edit home ([onEditHome]) and Delete home ([onDeleteHome]), all only for an
 * [isAdmin], and Leave home, which asks for confirmation before calling [onLeaveHome]. [leaveBlocked] shows why
 * the user can't leave (they are the only admin) until [onLeaveBlockedDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    homeName: String,
    modifier: Modifier = Modifier,
    x0xConnectivity: X0xConnectivity = X0xConnectivity.Connecting,
    onBack: () -> Unit = {},
    onPantryClick: () -> Unit = {},
    onShoppingClick: () -> Unit = {},
    onCookbookClick: () -> Unit = {},
    onMealPlannerClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onTasksClick: () -> Unit = {},
    tasksSummary: TasksSummary = TasksSummary(),
    onMarketplaceClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onMembersClick: () -> Unit = {},
    isAdmin: Boolean = false,
    onEditHome: () -> Unit = {},
    onHubClick: () -> Unit = {},
    onDeleteHome: () -> Unit = {},
    onLeaveHome: () -> Unit = {},
    leaveBlocked: Boolean = false,
    onLeaveBlockedDismiss: () -> Unit = {},
    membersBadge: Int = 0,
) {
    var confirmingLeave by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val tiles = listOf(
        DashboardTile(HomeDashboardTestTags.PantryTile, Res.string.pantry_title, Res.string.home_dashboard_pantry_body, Res.drawable.ic_pantry, TileAccents.getValue(HomeDashboardTestTags.PantryTile), onPantryClick),
        DashboardTile(HomeDashboardTestTags.ShoppingTile, Res.string.shopping_title, Res.string.home_dashboard_shopping_body, Res.drawable.ic_shopping_cart, TileAccents.getValue(HomeDashboardTestTags.ShoppingTile), onShoppingClick),
        DashboardTile(HomeDashboardTestTags.CookbookTile, Res.string.cookbook_title, Res.string.home_dashboard_cookbook_body, Res.drawable.ic_cookbook, TileAccents.getValue(HomeDashboardTestTags.CookbookTile), onCookbookClick),
        DashboardTile(HomeDashboardTestTags.MealPlannerTile, Res.string.meal_planner_title, Res.string.home_dashboard_meal_planner_body, Res.drawable.ic_meal_planner, TileAccents.getValue(HomeDashboardTestTags.MealPlannerTile), onMealPlannerClick),
        DashboardTile(HomeDashboardTestTags.CalendarTile, Res.string.calendar_title, Res.string.home_dashboard_calendar_body, Res.drawable.ic_calendar, TileAccents.getValue(HomeDashboardTestTags.CalendarTile), onCalendarClick),
        DashboardTile(
            HomeDashboardTestTags.TasksTile, Res.string.tasks_title, Res.string.home_dashboard_tasks_body, Res.drawable.ic_checklist,
            TileAccents.getValue(HomeDashboardTestTags.TasksTile), onTasksClick,
            bodyOverride = when {
                tasksSummary.overdue > 0 -> stringResource(Res.string.home_dashboard_tasks_overdue, tasksSummary.overdue)
                tasksSummary.today > 0 -> stringResource(Res.string.home_dashboard_tasks_today, tasksSummary.today)
                else -> null
            },
            bodyIsAlert = tasksSummary.overdue > 0,
        ),
        DashboardTile(HomeDashboardTestTags.MarketplaceTile, Res.string.marketplace_title, Res.string.home_dashboard_marketplace_body, Res.drawable.ic_merchants, TileAccents.getValue(HomeDashboardTestTags.MarketplaceTile), onMarketplaceClick),
    ).map { it to stringResource(it.label) }
        .sortedBy { (_, label) -> label.lowercase() }
        .map { (tile, _) -> tile }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val contentPadding = if (maxWidth >= HomeDashboardTabletBreakpoint) 24.dp else 16.dp

        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            ),
        ) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(Res.drawable.ic_home), contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(homeName, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(HomeDashboardTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.home_dashboard_back),
                        )
                    }
                },
                actions = {
                    X0xConnectivityIndicator(x0xConnectivity, modifier = Modifier.testTag(HomeDashboardTestTags.X0xIndicator))
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(end = contentPadding - 12.dp)) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag(HomeDashboardTestTags.OverflowButton),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_dots_vertical),
                                contentDescription = stringResource(Res.string.home_dashboard_more_options),
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (isAdmin) DropdownMenuItem(
                                text = { Text(stringResource(Res.string.hub_menu_item)) },
                                onClick = {
                                    menuExpanded = false
                                    onHubClick()
                                },
                                modifier = Modifier.testTag(HomeDashboardTestTags.HubMenuItem),
                            )
                            if (isAdmin) DropdownMenuItem(
                                text = { Text(stringResource(Res.string.onboarding_edit_home)) },
                                onClick = {
                                    menuExpanded = false
                                    onEditHome()
                                },
                                modifier = Modifier.testTag(HomeDashboardTestTags.EditMenuItem),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.members_leave)) },
                                onClick = {
                                    menuExpanded = false
                                    confirmingLeave = true
                                },
                                modifier = Modifier.testTag(HomeDashboardTestTags.LeaveMenuItem),
                            )
                            if (isAdmin) DropdownMenuItem(
                                text = { Text(stringResource(Res.string.onboarding_delete_home), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    confirmingDelete = true
                                },
                                modifier = Modifier.testTag(HomeDashboardTestTags.DeleteMenuItem),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
            ) {
                items(tiles, key = { it.testTag }) { tile -> DashboardTileCard(tile) }
            }
            HomeNavigationBar(selected = HomeTab.Home, onHomeClick = onHomeClick, onMembersClick = onMembersClick, membersBadge = membersBadge)
        }
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            title = stringResource(Res.string.home_delete_confirm_title),
            body = stringResource(Res.string.home_delete_confirm_body),
            confirmLabel = stringResource(Res.string.home_delete_confirm),
            onConfirm = {
                confirmingDelete = false
                onDeleteHome()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
    if (confirmingLeave) {
        AlertDialog(
            onDismissRequest = { confirmingLeave = false },
            title = { Text(stringResource(Res.string.members_leave_confirm_title)) },
            text = { Text(stringResource(Res.string.members_leave_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingLeave = false
                        onLeaveHome()
                    },
                    modifier = Modifier.testTag(HomeDashboardTestTags.ConfirmLeave),
                ) { Text(stringResource(Res.string.members_leave_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingLeave = false }) { Text(stringResource(Res.string.members_leave_cancel)) }
            },
        )
    }
    if (leaveBlocked) {
        AlertDialog(
            onDismissRequest = onLeaveBlockedDismiss,
            title = { Text(stringResource(Res.string.members_leave_blocked_title)) },
            text = { Text(stringResource(Res.string.members_leave_blocked_body)) },
            confirmButton = {
                TextButton(onClick = onLeaveBlockedDismiss) { Text(stringResource(Res.string.members_leave_blocked_ok)) }
            },
            modifier = Modifier.testTag(HomeDashboardTestTags.BlockedDialog),
        )
    }
}

/** One area of the Home: a card like the Meal Planner's days, with the tile's colour as a bar down its left side and a round icon in it, then the area's name and what it is for. */
@Composable
private fun DashboardTileCard(tile: DashboardTile, modifier: Modifier = Modifier) {
    Surface(
        onClick = tile.onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth().testTag(tile.testTag),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(6.dp).background(tile.accent))
            Column(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp).background(tile.accent, CircleShape)) {
                    Icon(painterResource(tile.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column {
                    Text(
                        text = stringResource(tile.label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tile.bodyOverride ?: stringResource(tile.body),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (tile.bodyIsAlert) FontWeight.Bold else null,
                        color = if (tile.bodyIsAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (tile.testTag == HomeDashboardTestTags.TasksTile) Modifier.testTag(HomeDashboardTestTags.TasksTileBody) else Modifier,
                    )
                }
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
