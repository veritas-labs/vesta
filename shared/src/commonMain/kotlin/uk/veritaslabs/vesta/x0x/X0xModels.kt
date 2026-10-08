package uk.veritaslabs.vesta.x0x

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class X0xAgent(
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("machine_id") val machineId: String? = null,
    @SerialName("user_id") val userId: String? = null,
)

/** One row of `GET /agents/discovered`: what the daemon's discovery cache knows of an agent; [lastSeen] is Unix seconds. */
@Serializable
data class X0xDiscoveredAgent(
    @SerialName("agent_id") val agentId: String,
    @SerialName("machine_id") val machineId: String? = null,
    @SerialName("last_seen") val lastSeen: Long? = null,
)

@Serializable
data class X0xSession(
    @SerialName("session_token") val sessionToken: String,
    @SerialName("expires_in") val expiresIn: Int = 600,
)

@Serializable
data class X0xStoreKey(
    val key: String,
    val size: Long? = null,
    @SerialName("content_type") val contentType: String? = null,
)

/** Summary row from `GET /groups` — enough to find a previously-created group by name. */
@Serializable
data class X0xGroupSummary(
    @SerialName("group_id") val groupId: String,
    val name: String,
)

/** One row of `GET /groups/:id/members`; [role] is `"admin"` or `"member"`, [state] is `"active"` once admitted. */
@Serializable
data class X0xGroupMember(
    @SerialName("agent_id") val agentId: String,
    val role: String = "member",
    val state: String = "active",
)

/**
 * Signed `GroupPublicMessage` envelope, as it appears both from `GET /groups/:id/messages` and
 * (undecoded) as the raw gossip payload on `x0x.groups.public.{groupId}` — a group's public send
 * rides ordinary gossip underneath, just with this signed wrapper around the body.
 */
@Serializable
data class X0xGroupPublicMessage(
    @SerialName("group_id") val groupId: String,
    @SerialName("author_agent_id") val authorAgentId: String,
    val kind: String,
    /** UTF-8 message body — the caller's own payload, e.g. JSON-LD text. */
    val body: String,
    val timestamp: Long? = null,
    /** Unique id from `GET /groups/:id/messages` — use to dedupe when polling that cache. */
    @SerialName("msg_id") val msgId: String? = null,
)

/** Gossip event as delivered on `GET /events` (fields live under a `data` envelope). */
@Serializable
data class X0xGossipMessage(
    @SerialName("subscription_id") val subscriptionId: String? = null,
    val topic: String? = null,
    /** Base64-encoded payload bytes. */
    val payload: String? = null,
    val sender: String? = null,
    val verified: Boolean? = null,
    @SerialName("trust_level") val trustLevel: String? = null,
)

/** A presence transition as delivered on `GET /presence/events`: [event] is `"online"` or `"offline"`. */
@Serializable
data class X0xPresenceEvent(
    val event: String,
    @SerialName("agent_id") val agentId: String,
    val reachable: Boolean? = null,
) {
    val isOnline: Boolean get() = event == "online"
}

/** Direct message as delivered on `GET /direct/events` (flat, no envelope). */
@Serializable
data class X0xDirectMessage(
    val sender: String? = null,
    @SerialName("machine_id") val machineId: String? = null,
    /** Base64-encoded payload bytes. */
    val payload: String? = null,
    @SerialName("received_at") val receivedAt: Long? = null,
    val verified: Boolean? = null,
    @SerialName("trust_decision") val trustDecision: String? = null,
)

/**
 * One file transfer as `GET /files/transfers[/:id]` reports it. [state] is `"Pending"`, `"InProgress"`, `"Complete"`, `"Failed"` or `"Rejected"`;
 * a receiver's [outputPath] is where the daemon put the file (it may dangle once the app has moved it) and a sender's [sourcePath] is the file it read.
 */
@Serializable
data class X0xFileTransfer(
    @SerialName("transfer_id") val transferId: String,
    val filename: String? = null,
    val size: Long? = null,
    val sha256: String? = null,
    val state: String? = null,
    val error: String? = null,
    @SerialName("output_path") val outputPath: String? = null,
    @SerialName("source_path") val sourcePath: String? = null,
)

/**
 * A file-transfer event from the daemon's `/events` stream (`file:*` frames). x0xd sends no `file:failed` frame (ADR-023, Spike 1),
 * so a failed receive is only visible through [X0xClient.getFileTransfer]; [Failed] is there for daemons that do emit one.
 */
sealed interface X0xFileEvent {
    val transferId: String

    /** Someone offers [filename] of [size] bytes; nothing moves until [X0xClient.acceptFile]. */
    data class Offer(override val transferId: String, val filename: String, val size: Long, val sender: String) : X0xFileEvent

    /** The file landed at [path] (the daemon's own name for it, not [filename]) and the daemon computed [sha256] over it. */
    data class Complete(override val transferId: String, val filename: String, val sha256: String?, val path: String) : X0xFileEvent

    data class Failed(override val transferId: String, val reason: String?) : X0xFileEvent
}
