package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xConnectivity
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_chevron_right
import vesta.shared.generated.resources.settings_application_build
import vesta.shared.generated.resources.settings_application_version
import vesta.shared.generated.resources.settings_back
import vesta.shared.generated.resources.settings_dark_theme
import vesta.shared.generated.resources.settings_device_platform
import vesta.shared.generated.resources.settings_network_connected
import vesta.shared.generated.resources.settings_network_connecting
import vesta.shared.generated.resources.settings_network_offline
import vesta.shared.generated.resources.settings_network_peers
import vesta.shared.generated.resources.settings_network_status
import vesta.shared.generated.resources.settings_network_mobile_data
import vesta.shared.generated.resources.settings_section_application
import vesta.shared.generated.resources.settings_section_device
import vesta.shared.generated.resources.settings_section_appearance
import vesta.shared.generated.resources.settings_section_profile
import vesta.shared.generated.resources.settings_your_name
import vesta.shared.generated.resources.settings_section_network
import vesta.shared.generated.resources.settings_synced_devices
import vesta.shared.generated.resources.settings_title

private val SettingsTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object SettingsTestTags {
    const val BackButton = "settings_back_button"
    const val List = "settings_list"
    const val DarkThemeRow = "settings_dark_theme_row"
    const val VersionRow = "settings_application_version"
    const val BuildRow = "settings_application_build"
    const val SyncedDevicesRow = "settings_synced_devices"
    const val YourNameRow = "settings_your_name"
    const val PlatformRow = "settings_device_platform"
    const val NetworkStatusRow = "settings_network_status"
    const val NetworkPeersRow = "settings_network_peers"
    const val MobileDataRow = "settings_network_mobile_data"
}

/**
 * The app's settings: an Appearance section with a dark theme switch ([darkTheme], [onDarkThemeChange]), then read-only Application
 * ([platformName]) and Network ([x0xConnectivity], with the peer count once connected) information; the Network section also has a
 * "Use mobile data" switch ([useMobileData], [onUseMobileDataChange]), on by default.
 * The Synced devices row calls [onSyncedDevicesClick]; with a [profileName], a Profile section shows it and its row calls
 * [onProfileNameClick] to change it; the back arrow calls [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    platformName: String = getPlatform().name,
    appInfo: AppInfo = getAppInfo(),
    x0xConnectivity: X0xConnectivity = X0xConnectivity.Connecting,
    darkTheme: Boolean = isSystemInDarkTheme(),
    onDarkThemeChange: (Boolean) -> Unit = {},
    useMobileData: Boolean = true,
    onUseMobileDataChange: (Boolean) -> Unit = {},
    onSyncedDevicesClick: () -> Unit = {},
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
                        SettingsRow(
                            label = stringResource(Res.string.settings_dark_theme),
                            modifier = Modifier
                                .toggleable(value = darkTheme, role = Role.Switch, onValueChange = onDarkThemeChange)
                                .testTag(SettingsTestTags.DarkThemeRow),
                        ) {
                            Switch(checked = darkTheme, onCheckedChange = null)
                        }
                    }
                }
                item {
                    SettingsSection(stringResource(Res.string.settings_section_application)) {
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
                        InfoRow(
                            stringResource(Res.string.settings_network_status),
                            stringResource(
                                when (x0xConnectivity) {
                                    is X0xConnectivity.Connected -> Res.string.settings_network_connected
                                    X0xConnectivity.Connecting -> Res.string.settings_network_connecting
                                    X0xConnectivity.Offline -> Res.string.settings_network_offline
                                },
                            ),
                            Modifier.testTag(SettingsTestTags.NetworkStatusRow),
                        )
                        (x0xConnectivity as? X0xConnectivity.Connected)?.let { connected ->
                            InfoRow(
                                stringResource(Res.string.settings_network_peers),
                                connected.peers.toString(),
                                Modifier.testTag(SettingsTestTags.NetworkPeersRow),
                            )
                        }
                        SettingsRow(
                            label = stringResource(Res.string.settings_network_mobile_data),
                            modifier = Modifier
                                .toggleable(value = useMobileData, role = Role.Switch, onValueChange = onUseMobileDataChange)
                                .testTag(SettingsTestTags.MobileDataRow),
                        ) {
                            Switch(checked = useMobileData, onCheckedChange = null)
                        }
                    }
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

/** One settings row, in the same surface as a Pantry item row; [trailing] sits at the end. */
@Composable
private fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
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
