package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.AppInfo
import uk.veritaslabs.vesta.getAppInfo
import uk.veritaslabs.vesta.getPlatform
import uk.veritaslabs.vesta.ui.theme.AppTheme
import uk.veritaslabs.vesta.ui.theme.ThemeMode
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xConnectivity
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_chevron_right
import vesta.shared.generated.resources.settings_application_build
import vesta.shared.generated.resources.settings_assistants
import vesta.shared.generated.resources.settings_application_version
import vesta.shared.generated.resources.settings_back
import vesta.shared.generated.resources.settings_device_platform
import vesta.shared.generated.resources.settings_network_connected
import vesta.shared.generated.resources.settings_network_connecting
import vesta.shared.generated.resources.settings_network_offline
import vesta.shared.generated.resources.settings_network_peers
import vesta.shared.generated.resources.settings_network_peers_open
import vesta.shared.generated.resources.settings_network_status
import vesta.shared.generated.resources.settings_network_status_open
import vesta.shared.generated.resources.settings_network_mobile_data
import vesta.shared.generated.resources.settings_network_mobile_data_hint
import vesta.shared.generated.resources.settings_open_source_licenses
import vesta.shared.generated.resources.settings_open_source_licenses_open
import vesta.shared.generated.resources.settings_section_application
import vesta.shared.generated.resources.settings_section_device
import vesta.shared.generated.resources.settings_section_appearance
import vesta.shared.generated.resources.settings_section_profile
import vesta.shared.generated.resources.settings_your_name
import vesta.shared.generated.resources.settings_section_network
import vesta.shared.generated.resources.settings_synced_devices
import vesta.shared.generated.resources.settings_theme
import vesta.shared.generated.resources.settings_theme_mode_dark
import vesta.shared.generated.resources.settings_theme_mode_light
import vesta.shared.generated.resources.settings_theme_mode_system
import vesta.shared.generated.resources.settings_theme_halloween
import vesta.shared.generated.resources.settings_theme_vesta
import vesta.shared.generated.resources.settings_title
import vesta.shared.generated.resources.settings_welcome_tour
import vesta.shared.generated.resources.settings_welcome_tour_show

private val SettingsTabletBreakpoint = 600.dp
private const val DisabledRowAlpha = 0.38f

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object SettingsTestTags {
    const val BackButton = "settings_back_button"
    const val List = "settings_list"
    const val ThemeRow = "settings_theme"
    const val ThemeModeLight = "settings_theme_mode_light"
    const val ThemeModeDark = "settings_theme_mode_dark"
    const val ThemeModeSystem = "settings_theme_mode_system"
    const val VersionRow = "settings_application_version"
    const val BuildRow = "settings_application_build"
    const val SyncedDevicesRow = "settings_synced_devices"
    const val AssistantsRow = "settings_assistants"
    const val YourNameRow = "settings_your_name"
    const val PlatformRow = "settings_device_platform"
    const val NetworkStatusRow = "settings_network_status"
    const val NetworkPeersRow = "settings_network_peers"
    const val MobileDataRow = "settings_network_mobile_data"
    const val OpenSourceLicensesRow = "settings_open_source_licenses"
    const val WelcomeTourRow = "settings_welcome_tour"
}

/**
 * The app's settings: an Appearance section with a Theme drop-down ([appTheme], [onAppThemeChange]) and a Light / Dark / System radio group ([themeMode], [onThemeModeChange]), then read-only Application
 * ([platformName]) and Network ([x0xConnectivity], with the peer count once connected) information; the Network section also has a
 * "Use mobile data" switch ([useMobileData], [onUseMobileDataChange]), on by default and disabled when
 * the device has no mobile data ([mobileDataAvailable], false on desktop).
 * The Open source licences row in Application calls [onOpenSourceLicensesClick], and the Welcome tour row [onWelcomeTourClick].
 * The Synced devices row calls [onSyncedDevicesClick], the x0x status row [onNetworkStatusClick] to show network diagnostics, and the Peers row [onPeersClick] to show them as a graph; with a [profileName], a Profile section shows it and its row calls
 * [onProfileNameClick] to change it; the back arrow calls [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    platformName: String = getPlatform().name,
    appInfo: AppInfo = getAppInfo(),
    x0xConnectivity: X0xConnectivity = X0xConnectivity.Connecting,
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    appTheme: AppTheme = AppTheme.Vesta,
    onAppThemeChange: (AppTheme) -> Unit = {},
    useMobileData: Boolean = true,
    onUseMobileDataChange: (Boolean) -> Unit = {},
    mobileDataAvailable: Boolean = getPlatform().hasMobileData,
    onSyncedDevicesClick: () -> Unit = {},
    onAssistantsClick: (() -> Unit)? = null,
    onNetworkStatusClick: () -> Unit = {},
    onPeersClick: () -> Unit = {},
    onOpenSourceLicensesClick: () -> Unit = {},
    onWelcomeTourClick: () -> Unit = {},
    profileName: String? = null,
    onProfileNameClick: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= SettingsTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(SettingsTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.settings_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding).testTag(SettingsTestTags.List),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                profileName?.let { name ->
                    item {
                        SettingsSection(stringResource(Res.string.settings_section_profile)) {
                            SettingsRow(
                                label = stringResource(Res.string.settings_your_name),
                                modifier = Modifier.testTag(SettingsTestTags.YourNameRow),
                                onClick = onProfileNameClick,
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.settings_section_appearance)) {
                        ThemeDropdownRow(appTheme, onAppThemeChange)
                        ThemeModeRadioRow(themeMode, onThemeModeChange)
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.settings_section_application)) {
                        onAssistantsClick?.let { onClick ->
                            SettingsRow(
                                label = stringResource(Res.string.settings_assistants),
                                modifier = Modifier.testTag(SettingsTestTags.AssistantsRow),
                                onClick = onClick,
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        SettingsRow(
                            label = stringResource(Res.string.settings_welcome_tour),
                            modifier = Modifier.testTag(SettingsTestTags.WelcomeTourRow),
                            onClick = onWelcomeTourClick,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_chevron_right),
                                contentDescription = stringResource(Res.string.settings_welcome_tour_show),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        InfoRow(
                            stringResource(Res.string.settings_application_version),
                            appInfo.version,
                            Modifier.testTag(SettingsTestTags.VersionRow),
                        )
                        InfoRow(
                            stringResource(Res.string.settings_application_build),
                            appInfo.buildCode,
                            Modifier.testTag(SettingsTestTags.BuildRow),
                        )
                        SettingsRow(
                            label = stringResource(Res.string.settings_open_source_licenses),
                            modifier = Modifier.testTag(SettingsTestTags.OpenSourceLicensesRow),
                            onClick = onOpenSourceLicensesClick,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_chevron_right),
                                contentDescription = stringResource(Res.string.settings_open_source_licenses_open),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.settings_section_device)) {
                        SettingsRow(
                            label = stringResource(Res.string.settings_synced_devices),
                            modifier = Modifier.testTag(SettingsTestTags.SyncedDevicesRow),
                            onClick = onSyncedDevicesClick,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        InfoRow(
                            stringResource(Res.string.settings_device_platform),
                            platformName,
                            Modifier.testTag(SettingsTestTags.PlatformRow),
                        )
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.settings_section_network)) {
                        SettingsRow(
                            label = stringResource(Res.string.settings_network_status),
                            modifier = Modifier.testTag(SettingsTestTags.NetworkStatusRow),
                            onClick = onNetworkStatusClick,
                        ) {
                            Text(
                                stringResource(
                                    when (x0xConnectivity) {
                                        is X0xConnectivity.Connected -> Res.string.settings_network_connected
                                        X0xConnectivity.Connecting -> Res.string.settings_network_connecting
                                        X0xConnectivity.Offline -> Res.string.settings_network_offline
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                painter = painterResource(Res.drawable.ic_chevron_right),
                                contentDescription = stringResource(Res.string.settings_network_status_open),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        (x0xConnectivity as? X0xConnectivity.Connected)?.let { connected ->
                            SettingsRow(
                                label = stringResource(Res.string.settings_network_peers),
                                modifier = Modifier.testTag(SettingsTestTags.NetworkPeersRow),
                                onClick = onPeersClick,
                            ) {
                                Text(
                                    connected.peers.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    painter = painterResource(Res.drawable.ic_chevron_right),
                                    contentDescription = stringResource(Res.string.settings_network_peers_open),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        SettingsRow(
                            label = stringResource(Res.string.settings_network_mobile_data),
                            supporting = stringResource(Res.string.settings_network_mobile_data_hint),
                            enabled = mobileDataAvailable,
                            modifier = Modifier
                                .toggleable(
                                    value = useMobileData,
                                    enabled = mobileDataAvailable,
                                    role = Role.Switch,
                                    onValueChange = onUseMobileDataChange,
                                )
                                .testTag(SettingsTestTags.MobileDataRow),
                        ) {
                            Switch(checked = useMobileData, onCheckedChange = null, enabled = mobileDataAvailable)
                        }
                    }
                }
            }
        }
    }
}

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.Light -> Res.string.settings_theme_mode_light
    ThemeMode.Dark -> Res.string.settings_theme_mode_dark
    ThemeMode.System -> Res.string.settings_theme_mode_system
}

private fun ThemeMode.testTag() = when (this) {
    ThemeMode.Light -> SettingsTestTags.ThemeModeLight
    ThemeMode.Dark -> SettingsTestTags.ThemeModeDark
    ThemeMode.System -> SettingsTestTags.ThemeModeSystem
}

private fun AppTheme.labelRes() = when (this) {
    AppTheme.Vesta -> Res.string.settings_theme_vesta
    AppTheme.Halloween -> Res.string.settings_theme_halloween
}

/** Light / Dark / System as a horizontal radio group, in the same surface as a settings row. */
@Composable
private fun ThemeModeRadioRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .selectableGroup()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .selectable(selected = selected == mode, role = Role.RadioButton, onClick = { onSelect(mode) })
                    .padding(vertical = 8.dp)
                    .testTag(mode.testTag()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == mode, onClick = null)
                Text(
                    text = stringResource(mode.labelRes()),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** The Theme row: shows the current theme and opens a menu of every [AppTheme] to choose from. */
@Composable
private fun ThemeDropdownRow(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SettingsRow(
        label = stringResource(Res.string.settings_theme),
        modifier = Modifier.testTag(SettingsTestTags.ThemeRow),
        onClick = { expanded = true },
    ) {
        Box {
            Text(
                stringResource(selected.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AppTheme.entries.forEach { theme ->
                    DropdownMenuItem(
                        text = { Text(stringResource(theme.labelRes())) },
                        onClick = {
                            expanded = false
                            onSelect(theme)
                        },
                    )
                }
            }
        }
    }
}

/** A labelled group of rows, styled like a Pantry category header over its rows. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

/** One settings row, in the same surface as a Pantry item row; [trailing] sits at the end. A row that isn't [enabled] is greyed out as a whole. */
@Composable
private fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    supporting: String? = null,
    trailing: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DisabledRowAlpha)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    SettingsRow(label, modifier) {
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SettingsScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { SettingsScreen(platformName = "Android 15", appInfo = AppInfo("1.0", "1"), x0xConnectivity = X0xConnectivity.Connected(23)) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SettingsScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { SettingsScreen(platformName = "Android 15", appInfo = AppInfo("1.0", "1"), x0xConnectivity = X0xConnectivity.Connected(23)) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SettingsScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { SettingsScreen(platformName = "Android 15", appInfo = AppInfo("1.0", "1"), x0xConnectivity = X0xConnectivity.Connected(23)) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SettingsScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { SettingsScreen(platformName = "Android 15", appInfo = AppInfo("1.0", "1"), x0xConnectivity = X0xConnectivity.Connected(23)) }
}
