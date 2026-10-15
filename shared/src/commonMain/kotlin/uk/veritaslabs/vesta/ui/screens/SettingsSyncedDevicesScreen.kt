package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.devicesync.SyncedDevice
import uk.veritaslabs.vesta.ui.components.FabClearance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.EmptyState
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.ic_trash
import vesta.shared.generated.resources.synced_devices_add
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.synced_devices_empty_subtitle
import vesta.shared.generated.resources.synced_devices_empty_title
import vesta.shared.generated.resources.synced_devices_remove
import vesta.shared.generated.resources.common_cancel
import vesta.shared.generated.resources.synced_devices_remove_confirm
import vesta.shared.generated.resources.synced_devices_remove_confirm_body
import vesta.shared.generated.resources.synced_devices_remove_confirm_title
import vesta.shared.generated.resources.synced_devices_title

private val SyncedDevicesTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or device names. */
internal object SyncedDevicesTestTags {
    const val BackButton = "synced_devices_back_button"
    const val AddButton = "synced_devices_add_button"
    const val EmptyState = "synced_devices_empty_state"
    const val ConfirmRemove = "synced_devices_confirm_remove"

    fun device(agentId: String) = "synced_device_$agentId"
    fun remove(agentId: String) = "synced_device_remove_$agentId"
}

/**
 * The devices that belong to this user and sync with this one: each [devices] row shows the device's name and
 * agent id with a remove button, which asks for confirmation before calling [onRemoveDevice]. With none, a
 * friendly empty state explains what syncing devices is for. The floating action button calls [onAddDevice]; the
 * back arrow calls [onBack]. UI only — the caller owns [devices].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSyncedDevicesScreen(
    devices: List<SyncedDevice>,
    modifier: Modifier = Modifier,
    onAddDevice: () -> Unit = {},
    onRemoveDevice: (SyncedDevice) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var removing by remember { mutableStateOf<SyncedDevice?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= SyncedDevicesTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.synced_devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(SyncedDevicesTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding)
                        .testTag(SyncedDevicesTestTags.EmptyState),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        EmptyStateArt.SyncedDevices,
                        title = stringResource(Res.string.synced_devices_empty_title),
                        body = stringResource(Res.string.synced_devices_empty_subtitle),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding),
                    contentPadding = PaddingValues(top = 4.dp, bottom = FabClearance),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(devices, key = { it.agentId }) { device ->
                        SyncedDeviceRow(device = device, onRemove = { removing = device })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddDevice,
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(contentPadding)
                .testTag(SyncedDevicesTestTags.AddButton),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_plus),
                contentDescription = stringResource(Res.string.synced_devices_add),
            )
        }
    }

    removing?.let { device ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.synced_devices_remove_confirm_title)) },
            text = { Text(stringResource(Res.string.synced_devices_remove_confirm_body, device.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveDevice(device)
                        removing = null
                    },
                    modifier = Modifier.testTag(SyncedDevicesTestTags.ConfirmRemove),
                ) { Text(stringResource(Res.string.synced_devices_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun SyncedDeviceRow(device: SyncedDevice, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)
            .testTag(SyncedDevicesTestTags.device(device.agentId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = device.agentId,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(40.dp).testTag(SyncedDevicesTestTags.remove(device.agentId)),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_trash),
                contentDescription = stringResource(Res.string.synced_devices_remove, device.name),
            )
        }
    }
}

private val SyncedDevicesPreviewDevices = listOf(
    SyncedDevice("Mark's Pixel 9", "3f9a1c52e07b4d68a9c1f0e2b7d4835a6c19e0f2d4b8a7c351e6f90a2b4d7c18"),
    SyncedDevice("Office desktop", "a7c2e91f4b6d03885e1a2f7c9d4b06e3158a7f2c0d9e4b61a3c85f70d2e9b14c"),
    SyncedDevice("Kitchen tablet", "0e5b8d3a7c1f49266b9e0a4d8f2c71e35a9d6b0c4f8e2173a5c9d0b6e4f1a82d"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SettingsSyncedDevicesScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { SettingsSyncedDevicesScreen(devices = SyncedDevicesPreviewDevices) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SettingsSyncedDevicesScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { SettingsSyncedDevicesScreen(devices = SyncedDevicesPreviewDevices) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SettingsSyncedDevicesScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { SettingsSyncedDevicesScreen(devices = SyncedDevicesPreviewDevices) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SettingsSyncedDevicesScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { SettingsSyncedDevicesScreen(devices = SyncedDevicesPreviewDevices) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SettingsSyncedDevicesScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { SettingsSyncedDevicesScreen(devices = emptyList()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SettingsSyncedDevicesScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { SettingsSyncedDevicesScreen(devices = emptyList()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SettingsSyncedDevicesScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { SettingsSyncedDevicesScreen(devices = emptyList()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SettingsSyncedDevicesScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { SettingsSyncedDevicesScreen(devices = emptyList()) }
}
