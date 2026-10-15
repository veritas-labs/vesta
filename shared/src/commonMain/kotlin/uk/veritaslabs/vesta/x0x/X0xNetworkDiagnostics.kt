package uk.veritaslabs.vesta.x0x

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Bytes that have crossed one peer's connection since it was (re)established. */
data class X0xPeerTraffic(val rxBytes: Long, val txBytes: Long)

/**
 * The parts of `GET /diagnostics/connectivity` the diagnostics screen shows: the UDP bytes received and sent per peer connection
 * (the daemon keeps no whole-node counter), plus how the node sees itself on the network.
 */
data class X0xTransportSnapshot(
    val traffic: Map<String, X0xPeerTraffic>,
    val connections: Int,
    val avgRttMs: Long?,
    val natType: String?,
    val packetLossRate: Double?,
) {
    companion object {
        fun parse(body: JsonObject): X0xTransportSnapshot {
            val traffic = body["per_peer_transport"]?.jsonArray.orEmpty().mapNotNull { entry ->
                val peer = entry.jsonObject
                val id = peer["peer_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val rx = peer["udp_rx_bytes"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val tx = peer["udp_tx_bytes"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                id to X0xPeerTraffic(rx, tx)
            }.toMap()
            val loss = body["per_peer_transport"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["packet_loss_rate"]?.jsonPrimitive?.doubleOrNull }
            return X0xTransportSnapshot(
                traffic = traffic,
                connections = body["connections"]?.jsonObject?.get("active")?.jsonPrimitive?.longOrNull?.toInt() ?: traffic.size,
                avgRttMs = body["avg_rtt_ms"]?.jsonPrimitive?.longOrNull,
                natType = body["nat_type"]?.jsonPrimitive?.content,
                packetLossRate = loss.takeIf { it.isNotEmpty() }?.average(),
            )
        }
    }
}

/** Something worth a line in the diagnostics log; the screen words it. */
sealed interface X0xLogEvent {
    data object DaemonReachable : X0xLogEvent
    data object DaemonUnreachable : X0xLogEvent
    data class PeersChanged(val from: Int, val to: Int) : X0xLogEvent
    data class NatChanged(val natType: String) : X0xLogEvent
}

data class X0xLogEntry(val atMillis: Long, val event: X0xLogEvent)

/** What the diagnostics screen shows; [downBytesPerSec] and [upBytesPerSec] are over the last sample interval. */
data class X0xNetworkDiagnostics(
    val connectivity: X0xConnectivity = X0xConnectivity.Connecting,
    val health: X0xHealth? = null,
    val snapshot: X0xTransportSnapshot? = null,
    val sampledAtMillis: Long = 0,
    val downBytesPerSec: Long = 0,
    val upBytesPerSec: Long = 0,
    val downHistory: List<Long> = emptyList(),
    val upHistory: List<Long> = emptyList(),
    val log: List<X0xLogEntry> = emptyList(),
)

/**
 * Turns successive samples of the daemon into [X0xNetworkDiagnostics]: speeds from the change in each peer's byte counters (a counter
 * that went down means that connection was replaced, and a peer seen for the first time has no earlier reading, so neither counts), a
 * rolling history for the charts, and a log of what changed between samples. Not thread-safe; feed it from one coroutine.
 */
class X0xNetworkMonitor(private val historySize: Int = 60, private val logSize: Int = 100) {
    private var previous: X0xNetworkDiagnostics? = null

    fun sample(nowMillis: Long, health: X0xHealth?, snapshot: X0xTransportSnapshot?): X0xNetworkDiagnostics {
        val before = previous
        val reachable = health?.ok == true
        val connectivity = if (reachable) X0xConnectivity.Connected(health.peers) else X0xConnectivity.Offline

        val events = buildList {
            val wasReachable = before?.connectivity?.let { it is X0xConnectivity.Connected }
            if (reachable && wasReachable != true) add(X0xLogEvent.DaemonReachable)
            if (!reachable && wasReachable != false) add(X0xLogEvent.DaemonUnreachable)
            val peersBefore = (before?.connectivity as? X0xConnectivity.Connected)?.peers
            if (reachable && peersBefore != null && peersBefore != health.peers) add(X0xLogEvent.PeersChanged(peersBefore, health.peers))
            val nat = snapshot?.natType
            val natBefore = before?.snapshot?.natType
            if (nat != null && natBefore != null && nat != natBefore) add(X0xLogEvent.NatChanged(nat))
        }

        var down = 0L
        var up = 0L
        val earlier = before?.snapshot
        if (earlier != null && snapshot != null && before.sampledAtMillis < nowMillis) {
            snapshot.traffic.forEach { (id, now) ->
                val then = earlier.traffic[id] ?: return@forEach
                if (now.rxBytes >= then.rxBytes) down += now.rxBytes - then.rxBytes
                if (now.txBytes >= then.txBytes) up += now.txBytes - then.txBytes
            }
            val seconds = (nowMillis - before.sampledAtMillis) / 1_000.0
            down = (down / seconds).toLong()
            up = (up / seconds).toLong()
        }

        val result = X0xNetworkDiagnostics(
            connectivity = connectivity,
            health = health,
            snapshot = snapshot ?: before?.snapshot.takeIf { reachable },
            sampledAtMillis = nowMillis,
            downBytesPerSec = down,
            upBytesPerSec = up,
            downHistory = ((before?.downHistory ?: emptyList()) + down).takeLast(historySize),
            upHistory = ((before?.upHistory ?: emptyList()) + up).takeLast(historySize),
            log = (events.map { X0xLogEntry(nowMillis, it) } + (before?.log ?: emptyList())).take(logSize),
        )
        previous = result
        return result
    }
}
