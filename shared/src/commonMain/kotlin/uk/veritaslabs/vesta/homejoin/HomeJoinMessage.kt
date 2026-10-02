package uk.veritaslabs.vesta.homejoin

import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What an admin and someone joining a Home say to each other over x0x direct messages
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

    companion object {
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
