package uk.veritaslabs.vesta.autonomi

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

private sealed interface AutonomiState {
    data object Starting : AutonomiState
    data class Live(val status: AutonomiStatus) : AutonomiState
    data object Unreachable : AutonomiState
}

private val connectedGreen = Color(0xFF4CAF50)

/**
 * Home-screen demo card: live proof that this app talks to the Autonomi
 * network. Polls [AutonomiClient.connectionStatus] every few seconds and
 * shows connection status, network name, version and uptime where available.
 * On Android the client is embedded in-process (connected on first poll); on
 * desktop this drives the standalone `antd` REST API.
 */
@Composable
fun AutonomiStatusCard(modifier: Modifier = Modifier) {
    val client = remember { createAutonomiClient() }
    var state by remember { mutableStateOf<AutonomiState>(AutonomiState.Starting) }

    LaunchedEffect(client) {
        while (true) {
            state = client.connectionStatus()?.let(AutonomiState::Live) ?: AutonomiState.Unreachable
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
                    "Autonomi network",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                AutonomiStatusBadge(state)
            }
            when (val s = state) {
                is AutonomiState.Starting -> Text(
                    "Connecting to the Autonomi network…",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is AutonomiState.Live -> with(s.status) {
                    Text(
                        buildString {
                            append(network)
                            if (!version.isNullOrEmpty()) append(" · v$version")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    uptimeSecs?.let {
                        Text(
                            "up ${formatUptime(it)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                is AutonomiState.Unreachable -> Text(
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
private fun AutonomiStatusBadge(state: AutonomiState) {
    val (color, label) = when (state) {
        is AutonomiState.Live ->
            if (state.status.connected) connectedGreen to "connected"
            else MaterialTheme.colorScheme.error to "unhealthy"
        is AutonomiState.Starting ->
            MaterialTheme.colorScheme.onSurfaceVariant to "starting"
        is AutonomiState.Unreachable ->
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
