package uk.veritaslabs.vesta.homejoin

import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What an admin and someone joining, leaving or being removed from a Home say to each other over x0x direct messages
 * (docs/adr/ADR-015-joining-a-home.md). Discriminated by `kind`; the `homejoin.` prefix keeps these apart from the
 * device-sync (ADR-012) and agent-command (ADR-009) messages on the same stream, which simply don't decode as these.
 */
@Serializable
sealed interface HomeJoinMessage {

    /** Admin to joiner: the Home to join, and who is inviting, so the joiner knows whom to sync it with. */
    @Serializable
    @SerialName("homejoin.invite")
    data class Invite(
        val homeId: String,
        val homeName: String,
        val shape: String,
        val createdAt: Long,
        val inviterMemberId: String,
        val inviterName: String,
    ) : HomeJoinMessage

    /** Joiner to admin: "I have the Home now", so the admin can add them to its roster and stop repeating the invite. */
    @Serializable
    @SerialName("homejoin.accepted")
    data class Accepted(val homeId: String) : HomeJoinMessage

    /** A member to the others: "I have left this Home", so they drop me from the roster. */
    @Serializable
    @SerialName("homejoin.left")
    data class Left(val homeId: String, val memberId: String) : HomeJoinMessage

    /** An admin to a member they have removed: "you are no longer in this Home". */
    @Serializable
    @SerialName("homejoin.removed")
    data class Removed(val homeId: String) : HomeJoinMessage

    /**
     * Recipient to sender: "I have your [of] message about this Home" (`left` or `removed`; see [LeftKind] and
     * [RemovedKind]), so the sender stops resending it (docs/adr/ADR-017-retrying-messages-to-offline-devices.md).
     */
    @Serializable
    @SerialName("homejoin.ack")
    data class Ack(val homeId: String, val of: String) : HomeJoinMessage

    /**
     * Joiner to admin: "I opened an invite link for this Home", with the link's secret
     * (docs/adr/ADR-018-invite-links.md). The admin only asks a person to approve it; the secret alone admits nobody.
     */
    @Serializable
    @SerialName("homejoin.request")
    data class Request(
        val homeId: String,
        val secret: String,
        val memberId: String,
        val name: String,
        val agentId: String,
    ) : HomeJoinMessage

    /** Admin to joiner: "this request will not succeed" ([reason] is one of the `Denied*` constants), so they stop retrying. */
    @Serializable
    @SerialName("homejoin.denied")
    data class Denied(val homeId: String, val reason: String) : HomeJoinMessage

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

        fun encode(message: HomeJoinMessage): ByteArray = json.encodeToString(serializer(), message).encodeToByteArray()

        /** Decodes a direct message's base64 payload; null when it isn't a Home-join message. */
        fun decode(base64Payload: String?): HomeJoinMessage? {
            val bytes = base64Payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() } ?: return null
            return runCatching { json.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()
        }
    }
}
