package uk.veritaslabs.vesta.x0x

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.x0x_connectivity_connected
import vesta.shared.generated.resources.x0x_connectivity_connecting
import vesta.shared.generated.resources.x0x_connectivity_offline
import vesta.shared.generated.resources.x0x_label

/** Whether this device currently has a working link to the x0x network. */
sealed interface X0xConnectivity {
    data object Connecting : X0xConnectivity
    data class Connected(val peers: Int) : X0xConnectivity
    data object Offline : X0xConnectivity
}

/**
 * Whether this device is reaching the x0x network, as far as anyone can send it a message: not when the daemon is
 * offline or has no peers. Still connecting counts as reaching it, so nothing flashes a warning at startup.
 */
val X0xConnectivity.isReachingNetwork: Boolean
    get() = when (this) {
        X0xConnectivity.Connecting -> true
        X0xConnectivity.Offline -> false
        is X0xConnectivity.Connected -> peers > 0
    }

private val connectedGreen = Color(0xFF4CAF50)

private const val PollIntervalMillis = 5_000L

/** Polls the local x0x daemon's `GET /health`, reporting [X0xConnectivity.Connecting] until the first answer. */
@Composable
fun rememberX0xConnectivity(): X0xConnectivity {
    val client = remember { createX0xClient() }
    var connectivity by remember { mutableStateOf<X0xConnectivity>(X0xConnectivity.Connecting) }
    LaunchedEffect(client) {
        while (true) {
            connectivity = client.health()?.takeIf { it.ok }?.let { X0xConnectivity.Connected(it.peers) }
                ?: X0xConnectivity.Offline
            delay(PollIntervalMillis)
        }
    }
    return connectivity
}

/** A small status dot, coloured by [connectivity], followed by an "x0x" label. */
@Composable
fun X0xConnectivityIndicator(connectivity: X0xConnectivity, modifier: Modifier = Modifier) {
    val color = when (connectivity) {
        is X0xConnectivity.Connected -> connectedGreen
        X0xConnectivity.Connecting -> MaterialTheme.colorScheme.onSurfaceVariant
        X0xConnectivity.Offline -> MaterialTheme.colorScheme.error
    }
    val description = stringResource(
        when (connectivity) {
            is X0xConnectivity.Connected -> Res.string.x0x_connectivity_connected
            X0xConnectivity.Connecting -> Res.string.x0x_connectivity_connecting
            X0xConnectivity.Offline -> Res.string.x0x_connectivity_offline
        },
    )
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(
            text = stringResource(Res.string.x0x_label),
            style = MaterialTheme.typography.labelMedium.copy(
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
