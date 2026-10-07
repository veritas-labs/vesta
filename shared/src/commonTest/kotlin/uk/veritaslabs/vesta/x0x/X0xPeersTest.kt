package uk.veritaslabs.vesta.x0x

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class X0xPeersTest {

    @Test
    fun aPeerThatRecentlyExchangedTrafficIsActive() {
        // Arrange
        val health = X0xPeerHealth(connected = true, lastReceivedMsAgo = 5_000, lastSentMsAgo = 300_000)

        // Act
        val status = X0xPeers.statusOf(health)

        // Assert
        assertEquals(X0xPeerStatus.Active, status)
    }

    @Test
    fun aConnectedPeerThatHasBeenQuietIsIdle() {
        // Arrange
        val health = X0xPeerHealth(connected = true, lastReceivedMsAgo = 120_000, lastSentMsAgo = null)

        // Act
        val status = X0xPeers.statusOf(health)

        // Assert
        assertEquals(X0xPeerStatus.Idle, status)
    }

    @Test
    fun aPeerWithoutHealthIsUnknown() {
        // Arrange
        val health: X0xPeerHealth? = null

        // Act
        val status = X0xPeers.statusOf(health)

        // Assert
        assertEquals(X0xPeerStatus.Unknown, status)
    }

    @Test
    fun loadsEveryPeerWithItsStatusAndMarksDirectConnections() = runTest {
        // Arrange
        val client = FakeX0xClient().apply {
            peers = listOf("aa", "bb", "cc")
            peerHealth["aa"] = X0xPeerHealth(connected = true, lastReceivedMsAgo = 1_000)
            peerHealth["bb"] = X0xPeerHealth(connected = true, lastReceivedMsAgo = 900_000)
            peerHealthFailures += "cc"
            directConnections = buildJsonObject {
                put("connections", JsonArray(listOf(buildJsonObject { put("agent_id", "zz"); put("machine_id", "bb") })))
            }
        }

        // Act
        val peers = X0xPeers.load(client)

        // Assert
        assertEquals(
            listOf(
                X0xPeer("aa", X0xPeerStatus.Active, direct = false, lastReceivedMsAgo = 1_000),
                X0xPeer("bb", X0xPeerStatus.Idle, direct = true, agentId = "zz", lastReceivedMsAgo = 900_000),
                X0xPeer("cc", X0xPeerStatus.Unknown, direct = false),
            ),
            peers,
        )
    }

    @Test
    fun stillListsPeersWhenDirectConnectionsCannotBeRead() = runTest {
        // Arrange
        val client = FakeX0xClient().apply { peers = listOf("aa") }
        client.directConnections = JsonObject(mapOf("connections" to JsonPrimitive("not an array")))

        // Act
        val peers = X0xPeers.load(client)

        // Assert
        assertEquals(listOf(X0xPeer("aa", X0xPeerStatus.Unknown, direct = false)), peers)
    }
}
