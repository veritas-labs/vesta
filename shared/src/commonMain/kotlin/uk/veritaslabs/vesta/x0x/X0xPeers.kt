package uk.veritaslabs.vesta.x0x

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The `snapshot` of `GET /peers/:id/health`: one connection's state, with times as milliseconds elapsed. */
@Serializable
data class X0xPeerHealth(
    val connected: Boolean = false,
    @SerialName("last_received_ms_ago") val lastReceivedMsAgo: Long? = null,
    @SerialName("last_sent_ms_ago") val lastSentMsAgo: Long? = null,
)

/** How a peer's connection looks from this device. */
enum class X0xPeerStatus {
    /** Traffic has crossed the connection in the last [X0xPeers.ActiveWithinMillis]. */
    Active,

    /** Connected, but quiet. */
    Idle,

    /** The daemon is connected to it but could not say how the connection is doing. */
    Unknown,
}

/**
 * One peer this device's x0x daemon is connected to; [direct] when it is also a direct-message connection, which is the only
 * way to learn its [agentId]. [lastReceivedMsAgo] and [lastSentMsAgo] say when traffic last crossed the connection.
 */
data class X0xPeer(
    val id: String,
    val status: X0xPeerStatus,
    val direct: Boolean,
    val agentId: String? = null,
    val lastReceivedMsAgo: Long? = null,
    val lastSentMsAgo: Long? = null,
)

object X0xPeers {
    const val ActiveWithinMillis = 60_000L

    /** How many health lookups run at once; a busy mesh has dozens of peers and the daemon is on this device. */
    private const val HealthLookups = 8

    fun statusOf(health: X0xPeerHealth?): X0xPeerStatus = when {
        health == null -> X0xPeerStatus.Unknown
        !health.connected -> X0xPeerStatus.Unknown
        listOfNotNull(health.lastReceivedMsAgo, health.lastSentMsAgo).any { it < ActiveWithinMillis } -> X0xPeerStatus.Active
        else -> X0xPeerStatus.Idle
    }

    /**
     * Every peer [client]'s daemon is connected to, with how each connection is doing. A peer is [X0xPeer.direct] when a
     * direct-message connection runs over its machine. A failed health lookup leaves that peer's status [X0xPeerStatus.Unknown]
     * rather than failing the list.
     */
    suspend fun load(client: X0xClient): List<X0xPeer> = coroutineScope {
        // machine id -> agent id
        val directMachines = runCatching {
            client.listDirectConnections()["connections"]?.jsonArray?.mapNotNull {
                val machine = it.jsonObject["machine_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                machine to it.jsonObject["agent_id"]?.jsonPrimitive?.content
            }?.toMap()
        }.getOrNull().orEmpty()
        val lookups = Semaphore(HealthLookups)
        client.listPeers().map { id ->
            async {
                val health = runCatching { lookups.withPermit { client.getPeerHealth(id) } }.getOrNull()
                X0xPeer(
                    id = id,
                    status = statusOf(health),
                    direct = id in directMachines,
                    agentId = directMachines[id],
                    lastReceivedMsAgo = health?.lastReceivedMsAgo,
                    lastSentMsAgo = health?.lastSentMsAgo,
                )
            }
        }.awaitAll()
    }
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
