package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.homejoin.HomeJoinMessage
import uk.veritaslabs.vesta.homejoin.PendingJoin
import uk.veritaslabs.vesta.ui.components.ActionCard
import uk.veritaslabs.vesta.ui.components.ActionCardOrientation
import uk.veritaslabs.vesta.ui.components.AvatarButton
import uk.veritaslabs.vesta.ui.components.AvatarButtonOrientation
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.ProfileAvatar
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_devices
import vesta.shared.generated.resources.ic_link
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_settings
import vesta.shared.generated.resources.home_picker_settings
import vesta.shared.generated.resources.img_armchair
import vesta.shared.generated.resources.onboarding_create_home
import vesta.shared.generated.resources.pending_join_cancel
import vesta.shared.generated.resources.pending_join_denied_declined
import vesta.shared.generated.resources.pending_join_denied_expired
import vesta.shared.generated.resources.pending_join_denied_revoked
import vesta.shared.generated.resources.pending_join_denied_used
import vesta.shared.generated.resources.pending_join_dismiss
import vesta.shared.generated.resources.pending_join_no_network
import vesta.shared.generated.resources.pending_join_waiting
import vesta.shared.generated.resources.onboarding_empty_subtitle
import vesta.shared.generated.resources.onboarding_empty_title
import vesta.shared.generated.resources.onboarding_get_started
import vesta.shared.generated.resources.onboarding_join_home
import vesta.shared.generated.resources.onboarding_join_home_description
import vesta.shared.generated.resources.onboarding_pair_device
import vesta.shared.generated.resources.onboarding_pair_device_description
import vesta.shared.generated.resources.onboarding_title

private val HomePickerTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or home names. */
internal object HomePickerTestTags {
    const val CreateHomeButton = "home_picker_create_home_button"
    const val JoinHomeButton = "home_picker_join_home_button"
    const val SettingsButton = "home_picker_settings_button"
    const val PairDeviceButton = "home_picker_pair_device_button"

    /** Tag for the card of the invite link to the Home [homeId] still waiting on, or refused. */
    fun pendingJoin(homeId: String) = "home_picker_pending_join_$homeId"

    /** Tag for the Cancel (or Dismiss) button of that card. */
    fun pendingJoinCancel(homeId: String) = "home_picker_pending_join_cancel_$homeId"

    /** Tag for the list row of the home with the given [homeId], independent of its display name. */
    fun homeItem(homeId: String) = "home_picker_item_$homeId"
}

/** A single row of the home picker screen's home list — see [HomePickerScreen]. */
data class HomeListItem(
    val id: String,
    val name: String,
    val avatar: DrawableResource? = null,
)

/**
 * Home picker backed by [viewModel]: picking a home records it as the selected Home. Blank until the
 * homes have loaded, so the empty-state doesn't flash on launch.
 */
@Composable
fun HomePickerScreen(
    viewModel: HomePickerViewModel,
    modifier: Modifier = Modifier,
    onCreateHome: () -> Unit = {},
    onJoinHome: () -> Unit = {},
    onPairDevice: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    pendingJoins: List<PendingJoin> = emptyList(),
    onCancelPendingJoin: (PendingJoin) -> Unit = {},
    networkConnected: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        HomePickerScreen(
            homes = state.homes,
            modifier = modifier,
            onHomeSelected = viewModel::selectHome,
            onCreateHome = onCreateHome,
            onJoinHome = onJoinHome,
            onPairDevice = onPairDevice,
            onSettingsClick = onSettingsClick,
            pendingJoins = pendingJoins,
            onCancelPendingJoin = onCancelPendingJoin,
            networkConnected = networkConnected,
        )
    }
}

/**
 * Carbon copy of [ProfilePickerScreen] for now, under its own name while
 * onboarding diverges from the shared-device profile picker: presents a
 * grid/list of [homes] to choose from (when non-empty) plus "Create home", "Join a home" and "Pair device"
 * actions. [onPairDevice] links this device to the user's other devices, so it can be done before any Home exists.
 * The cog beside the heading (hidden while there are no homes) calls [onSettingsClick]: the app's settings are global, so they live here rather than inside a Home.
 * Under the heading, a card for each invite link this user opened that no admin has answered yet, or refused
 * ([pendingJoins], docs/adr/ADR-018-invite-links.md), with a button ([onCancelPendingJoin]) to give up on it. While
 * [networkConnected] is false a waiting card says it is waiting for a network connection, since nothing can reach an admin.
 */
@Composable
fun HomePickerScreen(
    homes: List<HomeListItem>,
    modifier: Modifier = Modifier,
    onHomeSelected: (HomeListItem) -> Unit = {},
    onCreateHome: () -> Unit = {},
    onJoinHome: () -> Unit = {},
    onPairDevice: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    pendingJoins: List<PendingJoin> = emptyList(),
    onCancelPendingJoin: (PendingJoin) -> Unit = {},
    networkConnected: Boolean = true,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= HomePickerTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = contentPadding),
        ) {
            Spacer(Modifier.height(contentPadding))
            // The leading spacer is as wide as the cog so the title stays centred between them. With no
            // homes there is no cog (Settings opens once there is something to come back to), so no spacer.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (homes.isNotEmpty()) Spacer(Modifier.size(48.dp))
                Text(
                    text = stringResource(
                        if (homes.isEmpty()) Res.string.onboarding_empty_title else Res.string.onboarding_title,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                if (homes.isNotEmpty()) IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag(HomePickerTestTags.SettingsButton),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = stringResource(Res.string.home_picker_settings),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            if (homes.isEmpty()) {
                Text(
                    text = stringResource(Res.string.onboarding_empty_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(contentPadding))

            if (pendingJoins.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingJoins.forEach { join ->
                        PendingJoinCard(join, networkConnected = networkConnected, onCancel = { onCancelPendingJoin(join) })
                    }
                }
                Spacer(Modifier.height(contentPadding))
            }

            if (homes.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(Res.drawable.img_armchair),
                        contentDescription = null,
                        modifier = Modifier.width(if (isTablet) 320.dp else 280.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 3 else 1),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(homes, key = { it.id }) { home ->
                        AvatarButton(
                            label = home.name,
                            avatar = { size -> ProfileAvatar(name = home.name, size = size, avatar = home.avatar) },
                            orientation = if (isTablet) AvatarButtonOrientation.Vertical else AvatarButtonOrientation.Horizontal,
                            modifier = (if (isTablet) Modifier else Modifier.fillMaxWidth())
                                .testTag(HomePickerTestTags.homeItem(home.id)),
                            onClick = { onHomeSelected(home) },
                        )
                    }
                    item {
                        // Same footprint and shape as an AvatarButton card in either orientation.
                        val shape = MaterialTheme.shapes.small
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isTablet) Modifier.fillMaxHeight().heightIn(min = 184.dp) else Modifier.height(64.dp))
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onCreateHome)
                                .testTag(HomePickerTestTags.CreateHomeButton),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_plus),
                                contentDescription = stringResource(Res.string.onboarding_create_home),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionCard(
                    title = stringResource(Res.string.onboarding_pair_device),
                    body = stringResource(Res.string.onboarding_pair_device_description),
                    icon = { Icon(painterResource(Res.drawable.ic_devices), contentDescription = null) },
                    orientation = ActionCardOrientation.Vertical,
                    onClick = onPairDevice,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag(HomePickerTestTags.PairDeviceButton),
                )
                ActionCard(
                    title = stringResource(Res.string.onboarding_join_home),
                    body = stringResource(Res.string.onboarding_join_home_description),
                    icon = { Icon(painterResource(Res.drawable.ic_link), contentDescription = null) },
                    orientation = ActionCardOrientation.Vertical,
                    onClick = onJoinHome,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag(HomePickerTestTags.JoinHomeButton),
                )
            }
            if (homes.isEmpty()) {
                Spacer(Modifier.height(contentPadding))
                PrimaryButton(
                    label = stringResource(Res.string.onboarding_create_home),
                    onClick = onCreateHome,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag(HomePickerTestTags.CreateHomeButton),
                )
            }
            Spacer(Modifier.height(contentPadding))
        }
    }
}

/** An invite link still waiting on an admin, or refused: the Home's name, where it stands, and a way to give up on it. */
@Composable
private fun PendingJoinCard(join: PendingJoin, networkConnected: Boolean, onCancel: () -> Unit) {
    val status = when (join.denial) {
        null -> if (networkConnected) Res.string.pending_join_waiting else Res.string.pending_join_no_network
        HomeJoinMessage.DeniedExpired -> Res.string.pending_join_denied_expired
        HomeJoinMessage.DeniedRevoked -> Res.string.pending_join_denied_revoked
        HomeJoinMessage.DeniedUsed -> Res.string.pending_join_denied_used
        else -> Res.string.pending_join_denied_declined
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (join.denial == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().testTag(HomePickerTestTags.pendingJoin(join.homeId)),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = join.homeName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (join.denial == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onCancel, modifier = Modifier.testTag(HomePickerTestTags.pendingJoinCancel(join.homeId))) {
                Text(stringResource(if (join.denial == null) Res.string.pending_join_cancel else Res.string.pending_join_dismiss))
            }
        }
    }
}

private val HomePickerPreviewPendingJoins = listOf(
    PendingJoin("maple", "Maple Street", expiresAt = 1L, denial = null),
    PendingJoin("elm", "Elm Court", expiresAt = 1L, denial = HomeJoinMessage.DeniedDeclined),
)

private val HomePickerPreviewHomes = listOf(
    HomeListItem(id = "the-johnsons", name = "The Johnsons"),
    HomeListItem(id = "flat-4b", name = "Flat 4B"),
    HomeListItem(id = "mums-place", name = "Mum's Place"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomePickerScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomePickerScreen(homes = HomePickerPreviewHomes, pendingJoins = HomePickerPreviewPendingJoins) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomePickerScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomePickerScreen(homes = HomePickerPreviewHomes) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomePickerScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomePickerScreen(homes = HomePickerPreviewHomes) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomePickerScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomePickerScreen(homes = HomePickerPreviewHomes) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomePickerScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomePickerScreen(homes = emptyList()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomePickerScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomePickerScreen(homes = emptyList(), pendingJoins = HomePickerPreviewPendingJoins) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomePickerScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomePickerScreen(homes = emptyList()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomePickerScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomePickerScreen(homes = emptyList()) }
}
