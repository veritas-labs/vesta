package uk.veritaslabs.kits.x0x

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class X0xNetworkMonitorTest {

    private fun health(peers: Int = 3) = X0xHealth(true, "healthy", "0.46.0", peers, peers, 10)

    private fun snapshot(vararg traffic: Pair<String, Pair<Long, Long>>, nat: String = "FullCone") = X0xTransportSnapshot(
        traffic = traffic.associate { (id, bytes) -> id to X0xPeerTraffic(bytes.first, bytes.second) },
        connections = traffic.size,
        avgRttMs = 100,
        natType = nat,
        packetLossRate = null,
    )

    @Test
    fun speedIsTheChangeInBytesOverTheSampleInterval() {
        // Arrange
        val monitor = X0xNetworkMonitor()
        monitor.sample(0, health(), snapshot("a" to (1_000L to 500L)))

        // Act
        val result = monitor.sample(2_000, health(), snapshot("a" to (5_000L to 1_500L)))

        // Assert
        assertEquals(2_000, result.downBytesPerSec)
        assertEquals(500, result.upBytesPerSec)
    }

    @Test
    fun aReplacedConnectionWhoseCounterWentDownAddsNothing() {
        // Arrange
        val monitor = X0xNetworkMonitor()
        monitor.sample(0, health(), snapshot("a" to (9_000L to 9_000L), "b" to (0L to 0L)))

        // Act
        val result = monitor.sample(1_000, health(), snapshot("a" to (100L to 100L), "b" to (300L to 0L)))

        // Assert
        assertEquals(300, result.downBytesPerSec)
        assertEquals(0, result.upBytesPerSec)
    }

    @Test
    fun aPeerSeenForTheFirstTimeAddsNothing() {
        // Arrange
        val monitor = X0xNetworkMonitor()
        monitor.sample(0, health(), snapshot("a" to (0L to 0L)))

        // Act
        val result = monitor.sample(1_000, health(), snapshot("a" to (0L to 0L), "new" to (50_000L to 50_000L)))

        // Assert
        assertEquals(0, result.downBytesPerSec)
    }

    @Test
    fun historyKeepsOnlyTheLatestSamples() {
        // Arrange
        val monitor = X0xNetworkMonitor(historySize = 3)

        // Act
        var result = monitor.sample(0, health(), snapshot("a" to (0L to 0L)))
        for (i in 1..5) result = monitor.sample(i * 1_000L, health(), snapshot("a" to (i * 100L to 0L)))

        // Assert
        assertEquals(listOf(100L, 100L, 100L), result.downHistory)
    }

    @Test
    fun logsReachabilityPeerAndNatChangesNewestFirst() {
        // Arrange
        val monitor = X0xNetworkMonitor()
        monitor.sample(0, health(3), snapshot("a" to (0L to 0L)))

        // Act
        monitor.sample(1_000, health(4), snapshot("a" to (0L to 0L), nat = "Symmetric"))
        val result = monitor.sample(2_000, null, null)

        // Assert
        assertEquals(
            listOf(
                X0xLogEvent.DaemonUnreachable,
                X0xLogEvent.PeersChanged(3, 4),
                X0xLogEvent.NatChanged("Symmetric"),
                X0xLogEvent.DaemonReachable,
            ),
            result.log.map { it.event },
        )
    }

    @Test
    fun anUnchangedDaemonLogsNothingNew() {
        // Arrange
        val monitor = X0xNetworkMonitor()
        monitor.sample(0, health(), snapshot("a" to (0L to 0L)))

        // Act
        val result = monitor.sample(1_000, health(), snapshot("a" to (0L to 0L)))

        // Assert
        assertEquals(1, result.log.size)
    }

    @Test
    fun parsesPerPeerBytesFromTheConnectivityResponse() {
        // Arrange
        val body = buildJsonObject {
            put("avg_rtt_ms", 176)
            put("nat_type", "FullCone")
            put("per_peer_transport", buildJsonArray {
                add(buildJsonObject { put("peer_id", "p1"); put("udp_rx_bytes", 10); put("udp_tx_bytes", 4); put("packet_loss_rate", 0.02) })
                add(buildJsonObject { put("peer_id", "p2") })
            })
        }

        // Act
        val snapshot = X0xTransportSnapshot.parse(body)

        // Assert
        assertEquals(mapOf("p1" to X0xPeerTraffic(10, 4)), snapshot.traffic)
        assertEquals(176L, snapshot.avgRttMs)
        assertTrue(snapshot.packetLossRate!! in 0.019..0.021)
    }
}
