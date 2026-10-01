package uk.veritaslabs.vesta.x0x

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class X0xAgent(
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("machine_id") val machineId: String? = null,
    @SerialName("user_id") val userId: String? = null,
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
