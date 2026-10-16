package uk.veritaslabs.kits.spaces

import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What an admin and someone joining, leaving or being removed from a Space say to each other over x0x direct messages
 * (docs/adr/ADR-015-joining-a-home.md). Discriminated by `kind`; the `homejoin.` prefix keeps these apart from the
 * device-sync (ADR-012) and agent-command (ADR-009) messages on the same stream, which simply don't decode as these.
 */
@Serializable
sealed interface SpaceJoinMessage {

    /** Admin to joiner: the Space to join, and who is inviting, so the joiner knows whom to sync it with. */
    @Serializable
    @SerialName("homejoin.invite")
    data class Invite(
        @SerialName("homeId") val spaceId: String,
        @SerialName("homeName") val spaceName: String,
        @SerialName("shape") val kind: String,
        val createdAt: Long,
        val inviterMemberId: String,
        val inviterName: String,
    ) : SpaceJoinMessage

    /** Joiner to admin: "I have the Space now", so the admin can add them to its roster and stop repeating the invite. */
    @Serializable
    @SerialName("homejoin.accepted")
    data class Accepted(@SerialName("homeId") val spaceId: String) : SpaceJoinMessage

    /** A member to the others: "I have left this Space", so they drop me from the roster. */
    @Serializable
    @SerialName("homejoin.left")
    data class Left(@SerialName("homeId") val spaceId: String, val memberId: String) : SpaceJoinMessage

    /** An admin to a member they have removed: "you are no longer in this Space". */
    @Serializable
    @SerialName("homejoin.removed")
    data class Removed(@SerialName("homeId") val spaceId: String) : SpaceJoinMessage

    /**
     * Recipient to sender: "I have your [of] message about this Space" (`left` or `removed`; see [LeftKind] and
     * [RemovedKind]), so the sender stops resending it (docs/adr/ADR-017-retrying-messages-to-offline-devices.md).
     */
    @Serializable
    @SerialName("homejoin.ack")
    data class Ack(@SerialName("homeId") val spaceId: String, val of: String) : SpaceJoinMessage

    /**
     * Joiner to admin: "I opened an invite link for this Space", with the link's secret
     * (docs/adr/ADR-018-invite-links.md). The admin only asks a person to approve it; the secret alone admits nobody.
     */
    @Serializable
    @SerialName("homejoin.request")
    data class Request(
        @SerialName("homeId") val spaceId: String,
        val secret: String,
        val memberId: String,
        val name: String,
        val agentId: String,
    ) : SpaceJoinMessage

    /** Admin to joiner: "this request will not succeed" ([reason] is one of the `Denied*` constants), so they stop retrying. */
    @Serializable
    @SerialName("homejoin.denied")
    data class Denied(@SerialName("homeId") val spaceId: String, val reason: String) : SpaceJoinMessage

    companion object {
        const val DeniedExpired = "expired"
        const val DeniedRevoked = "revoked"
        const val DeniedUsed = "used"
        const val DeniedDeclined = "declined"

        const val LeftKind = "left"
        const val RemovedKind = "removed"

        private val json = Json {
            classDiscriminator = "kind"
            ignoreUnknownKeys = true
        }

        fun encode(message: SpaceJoinMessage): ByteArray = json.encodeToString(serializer(), message).encodeToByteArray()

        /** Decodes a direct message's base64 payload; null when it isn't a Space-join message. */
        fun decode(base64Payload: String?): SpaceJoinMessage? {
            val bytes = base64Payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() } ?: return null
            return runCatching { json.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()
        }
    }
}
