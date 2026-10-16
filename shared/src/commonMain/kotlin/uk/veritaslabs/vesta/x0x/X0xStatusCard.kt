package uk.veritaslabs.vesta.x0x

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.veritaslabs.kits.x0x.X0xHealth
import uk.veritaslabs.kits.x0x.createX0xClient

private sealed interface X0xStatus {
    data object Starting : X0xStatus
    data class Live(val health: X0xHealth) : X0xStatus
    data object Unreachable : X0xStatus
}

private val connectedGreen = Color(0xFF4CAF50)

/**
 * Home-screen demo card: live proof that this app talks to the x0x network.
 * Polls the local daemon's `GET /health` every few seconds and shows mesh
 * status, version, peer count and uptime. On mobile the daemon is embedded
 * in-process (started on first poll); on desktop this is the standalone
 * `x0xd` REST API.
 */
@Composable
fun X0xStatusCard(modifier: Modifier = Modifier) {
    val client = remember { createX0xClient() }
    var status by remember { mutableStateOf<X0xStatus>(X0xStatus.Starting) }

    LaunchedEffect(client) {
        while (true) {
            status = client.health()?.let(X0xStatus::Live) ?: X0xStatus.Unreachable
            delay(5_000)
        }
    }

    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "x0x network",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                X0xStatusBadge(status)
            }
            when (val s = status) {
                is X0xStatus.Starting -> Text(
                    "Starting the embedded daemon…",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is X0xStatus.Live -> with(s.health) {
                    Text(
                        "$status · v$version",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "$peers peers in the gossip mesh ($sendReadyPeers send-ready)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "up ${formatUptime(uptimeSecs)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is X0xStatus.Unreachable -> Text(
                    "Not connected",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                client.endpoint,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun X0xStatusBadge(status: X0xStatus) {
    val (color, label) = when (status) {
        is X0xStatus.Live ->
            if (status.health.ok) connectedGreen to "connected"
            else MaterialTheme.colorScheme.error to "unhealthy"
        is X0xStatus.Starting ->
            MaterialTheme.colorScheme.onSurfaceVariant to "starting"
        is X0xStatus.Unreachable ->
            MaterialTheme.colorScheme.error to "offline"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("●", color = color)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

private fun formatUptime(secs: Long): String {
    val minutes = secs / 60
    val seconds = secs % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
