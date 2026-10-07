package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xConnectivity
import uk.veritaslabs.vesta.x0x.X0xHealth
import uk.veritaslabs.vesta.x0x.X0xLogEntry
import uk.veritaslabs.vesta.x0x.X0xLogEvent
import uk.veritaslabs.vesta.x0x.X0xNetworkDiagnostics
import uk.veritaslabs.vesta.x0x.X0xPeerTraffic
import uk.veritaslabs.vesta.x0x.X0xTransportSnapshot
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.network_diagnostics_back
import vesta.shared.generated.resources.network_diagnostics_download
import vesta.shared.generated.resources.network_diagnostics_log_empty
import vesta.shared.generated.resources.network_diagnostics_log_nat
import vesta.shared.generated.resources.network_diagnostics_log_peers
import vesta.shared.generated.resources.network_diagnostics_log_reachable
import vesta.shared.generated.resources.network_diagnostics_log_unreachable
import vesta.shared.generated.resources.network_diagnostics_loss
import vesta.shared.generated.resources.network_diagnostics_ms
import vesta.shared.generated.resources.network_diagnostics_nat
import vesta.shared.generated.resources.network_diagnostics_peers
import vesta.shared.generated.resources.network_diagnostics_percent
import vesta.shared.generated.resources.network_diagnostics_rtt
import vesta.shared.generated.resources.network_diagnostics_section_connection
import vesta.shared.generated.resources.network_diagnostics_section_log
import vesta.shared.generated.resources.network_diagnostics_section_traffic
import vesta.shared.generated.resources.network_diagnostics_speed
import vesta.shared.generated.resources.network_diagnostics_status
import vesta.shared.generated.resources.network_diagnostics_title
import vesta.shared.generated.resources.network_diagnostics_unknown
import vesta.shared.generated.resources.network_diagnostics_uptime
import vesta.shared.generated.resources.network_diagnostics_upload
import vesta.shared.generated.resources.network_diagnostics_version
import vesta.shared.generated.resources.network_peers_ago_hours
import vesta.shared.generated.resources.network_peers_ago_minutes
import vesta.shared.generated.resources.network_peers_ago_now
import vesta.shared.generated.resources.network_peers_ago_seconds
import vesta.shared.generated.resources.settings_network_connected
import vesta.shared.generated.resources.settings_network_connecting
import vesta.shared.generated.resources.settings_network_offline

private val DiagnosticsTabletBreakpoint = 600.dp
private val ConnectedGreen = Color(0xFF4CAF50)
private val LogPanelHeight = 180.dp

/** Stable hooks for UI tests to target. */
internal object NetworkDiagnosticsTestTags {
    const val BackButton = "network_diagnostics_back_button"
    const val Status = "network_diagnostics_status"
    const val Download = "network_diagnostics_download"
    const val Upload = "network_diagnostics_upload"
    const val Log = "network_diagnostics_log"
    const val LogEmpty = "network_diagnostics_log_empty"
}

/**
 * How the x0x daemon is doing, live: the connection status with what the daemon reports about itself, the download and upload speed with
 * a chart of the last minute or so, and a small panel with the latest changes (newest first). [diagnostics] is the latest sample; the
 * back arrow calls [onBack]. UI only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDiagnosticsScreen(
    diagnostics: X0xNetworkDiagnostics,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= DiagnosticsTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.network_diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(NetworkDiagnosticsTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.network_diagnostics_back),
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
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { ConnectionSection(diagnostics) }
                item { TrafficSection(diagnostics) }
                item { LogSection(diagnostics) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Line(label: String, value: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        leading?.invoke()
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectionSection(diagnostics: X0xNetworkDiagnostics) {
    val unknown = stringResource(Res.string.network_diagnostics_unknown)
    val snapshot = diagnostics.snapshot
    val dotColor = when (diagnostics.connectivity) {
        is X0xConnectivity.Connected -> ConnectedGreen
        X0xConnectivity.Connecting -> MaterialTheme.colorScheme.onSurfaceVariant
        X0xConnectivity.Offline -> MaterialTheme.colorScheme.error
    }
    Section(stringResource(Res.string.network_diagnostics_section_connection)) {
        Panel {
            Line(
                label = stringResource(Res.string.network_diagnostics_status),
                value = stringResource(
                    when (diagnostics.connectivity) {
                        is X0xConnectivity.Connected -> Res.string.settings_network_connected
                        X0xConnectivity.Connecting -> Res.string.settings_network_connecting
                        X0xConnectivity.Offline -> Res.string.settings_network_offline
                    },
                ),
                modifier = Modifier.testTag(NetworkDiagnosticsTestTags.Status),
                leading = { Row(Modifier.padding(end = 8.dp)) { Box(Modifier.size(8.dp).background(dotColor, CircleShape)) } },
            )
            Line(
                stringResource(Res.string.network_diagnostics_peers),
                (diagnostics.connectivity as? X0xConnectivity.Connected)?.peers?.toString() ?: unknown,
            )
            Line(
                stringResource(Res.string.network_diagnostics_rtt),
                snapshot?.avgRttMs?.let { stringResource(Res.string.network_diagnostics_ms, it.toInt()) } ?: unknown,
            )
            Line(stringResource(Res.string.network_diagnostics_nat), snapshot?.natType ?: unknown)
            Line(
                stringResource(Res.string.network_diagnostics_loss),
                snapshot?.packetLossRate?.let { stringResource(Res.string.network_diagnostics_percent, formatDecimal(it * 100)) } ?: unknown,
            )
            Line(stringResource(Res.string.network_diagnostics_version), diagnostics.health?.version ?: unknown)
            Line(stringResource(Res.string.network_diagnostics_uptime), diagnostics.health?.let { formatDuration(it.uptimeSecs) } ?: unknown)
        }
    }
}

@Composable
private fun TrafficSection(diagnostics: X0xNetworkDiagnostics) {
    Section(stringResource(Res.string.network_diagnostics_section_traffic)) {
        Panel {
            SpeedLine(
                label = stringResource(Res.string.network_diagnostics_download),
                bytesPerSec = diagnostics.downBytesPerSec,
                history = diagnostics.downHistory,
                color = MaterialTheme.colorScheme.primary,
                tag = NetworkDiagnosticsTestTags.Download,
            )
            SpeedLine(
                label = stringResource(Res.string.network_diagnostics_upload),
                bytesPerSec = diagnostics.upBytesPerSec,
                history = diagnostics.upHistory,
                color = MaterialTheme.colorScheme.tertiary,
                tag = NetworkDiagnosticsTestTags.Upload,
            )
        }
    }
}

@Composable
private fun SpeedLine(label: String, bytesPerSec: Long, history: List<Long>, color: Color, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Line(label, stringResource(Res.string.network_diagnostics_speed, formatBytes(bytesPerSec)), Modifier.testTag(tag))
        Sparkline(history, color, Modifier.fillMaxWidth().height(40.dp))
    }
}

/** [values] as a line scaled to its own peak, oldest on the left; a flat line along the bottom while there is nothing to draw. */
@Composable
private fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        if (values.size < 2) return@Canvas
        val peak = values.max().coerceAtLeast(1L).toFloat()
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val point = Offset(i * step, size.height - (v / peak) * (size.height - 2.dp.toPx()))
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun LogSection(diagnostics: X0xNetworkDiagnostics) {
    Section(stringResource(Res.string.network_diagnostics_section_log)) {
        Panel(Modifier.height(LogPanelHeight)) {
            if (diagnostics.log.isEmpty()) {
                Text(
                    stringResource(Res.string.network_diagnostics_log_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(NetworkDiagnosticsTestTags.LogEmpty),
                )
            } else {
                LazyColumn(modifier = Modifier.testTag(NetworkDiagnosticsTestTags.Log), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(diagnostics.log.size) { i ->
                        val entry = diagnostics.log[i]
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                ageText(diagnostics.sampledAtMillis - entry.atMillis),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(logText(entry.event), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun logText(event: X0xLogEvent): String = when (event) {
    X0xLogEvent.DaemonReachable -> stringResource(Res.string.network_diagnostics_log_reachable)
    X0xLogEvent.DaemonUnreachable -> stringResource(Res.string.network_diagnostics_log_unreachable)
    is X0xLogEvent.PeersChanged -> stringResource(Res.string.network_diagnostics_log_peers, event.from, event.to)
    is X0xLogEvent.NatChanged -> stringResource(Res.string.network_diagnostics_log_nat, event.natType)
}

@Composable
private fun ageText(ms: Long): String = when {
    ms < 1_000 -> stringResource(Res.string.network_peers_ago_now)
    ms < 60_000 -> stringResource(Res.string.network_peers_ago_seconds, (ms / 1_000).toInt())
    ms < 3_600_000 -> stringResource(Res.string.network_peers_ago_minutes, (ms / 60_000).toInt())
    else -> stringResource(Res.string.network_peers_ago_hours, (ms / 3_600_000).toInt())
}

/** [bytes] with a binary unit, one decimal below 10 of a unit: "0 B", "812 B", "1.4 KB", "23 MB". */
internal fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0 || value >= 10) "${value.toLong()} ${units[unit]}" else "${formatDecimal(value)} ${units[unit]}"
}

private fun formatDecimal(value: Double): String {
    val tenths = (value * 10).toLong()
    return "${tenths / 10}.${tenths % 10}"
}

/** Seconds as the two largest non-zero units: "3d 4h", "2h 5m", "45s". */
internal fun formatDuration(seconds: Long): String {
    val d = seconds / 86_400
    val h = seconds % 86_400 / 3_600
    val m = seconds % 3_600 / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

private fun previewDiagnostics() = X0xNetworkDiagnostics(
    connectivity = X0xConnectivity.Connected(20),
    health = X0xHealth(ok = true, status = "healthy", version = "0.46.0", peers = 20, sendReadyPeers = 20, uptimeSecs = 88_525),
    snapshot = X0xTransportSnapshot(
        traffic = mapOf("a" to X0xPeerTraffic(1, 1)),
        connections = 25,
        avgRttMs = 176,
        natType = "FullCone",
        packetLossRate = 0.025,
    ),
    sampledAtMillis = 100_000,
    downBytesPerSec = 184_320,
    upBytesPerSec = 20_480,
    downHistory = listOf(10L, 40, 20, 90, 60, 120, 80, 150, 100, 180),
    upHistory = listOf(5L, 8, 4, 20, 12, 9, 30, 15, 10, 20),
    log = listOf(
        X0xLogEntry(98_000, X0xLogEvent.PeersChanged(19, 20)),
        X0xLogEntry(60_000, X0xLogEvent.PeersChanged(20, 19)),
        X0xLogEntry(10_000, X0xLogEvent.DaemonReachable),
    ),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun NetworkDiagnosticsScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { NetworkDiagnosticsScreen(previewDiagnostics()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun NetworkDiagnosticsScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { NetworkDiagnosticsScreen(previewDiagnostics()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun NetworkDiagnosticsScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { NetworkDiagnosticsScreen(previewDiagnostics()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun NetworkDiagnosticsScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { NetworkDiagnosticsScreen(previewDiagnostics()) }
}
