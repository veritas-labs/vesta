package uk.veritaslabs.vesta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.coroutines.delay
import uk.veritaslabs.kits.x0x.X0xConnectivity
import uk.veritaslabs.kits.x0x.X0xNetworkDiagnostics
import uk.veritaslabs.kits.x0x.X0xNetworkMonitor
import uk.veritaslabs.kits.x0x.X0xPeer
import uk.veritaslabs.kits.x0x.X0xPeers
import uk.veritaslabs.kits.x0x.createX0xClient

// Compose wrappers that poll the local x0x daemon while composed. They live here, not beside the client, so the x0x code has no UI dependency.

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

private const val PeersPollIntervalMillis = 5_000L

/** Polls the local daemon for its peers; null until the first answer, and an empty list while the daemon can't be asked. */
@Composable
fun rememberX0xPeers(): List<X0xPeer>? {
    val client = remember { createX0xClient() }
    var peers by remember { mutableStateOf<List<X0xPeer>?>(null) }
    LaunchedEffect(client) {
        while (true) {
            peers = runCatching { X0xPeers.load(client) }.getOrDefault(emptyList())
            delay(PeersPollIntervalMillis)
        }
    }
    return peers
}

private const val DiagnosticsPollIntervalMillis = 2_000L

/** Polls the local daemon for what the diagnostics screen shows, every couple of seconds while composed. */
@Composable
fun rememberX0xNetworkDiagnostics(): X0xNetworkDiagnostics {
    val client = remember { createX0xClient() }
    val monitor = remember { X0xNetworkMonitor() }
    var diagnostics by remember { mutableStateOf(X0xNetworkDiagnostics()) }
    LaunchedEffect(client) {
        while (true) {
            val health = runCatching { client.health() }.getOrNull()
            val snapshot = health?.let { runCatching { client.getTransportSnapshot() }.getOrNull() }
            diagnostics = monitor.sample(Clock.System.now().toEpochMilliseconds(), health, snapshot)
            delay(DiagnosticsPollIntervalMillis)
        }
    }
    return diagnostics
}
