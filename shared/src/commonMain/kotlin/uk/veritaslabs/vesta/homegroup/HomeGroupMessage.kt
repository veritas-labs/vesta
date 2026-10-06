package uk.veritaslabs.vesta.homegroup

import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How the devices of a Home get into its x0x group (docs/adr/ADR-021-home-x0x-group.md), over x0x direct messages. The
 * `homegroup.` prefix keeps these apart from the Home-join, device-sync and agent-command messages on the same stream.
 */
@Serializable
sealed interface HomeGroupMessage {

    /** A device that has the Home but is not in its group to the admins' devices: "please invite me". Resent every sync round. */
    @Serializable
    @SerialName("homegroup.invite.request")
    data class InviteRequest(val homeId: String) : HomeGroupMessage

    /** An admin device to the asker: an x0x invite link for [groupId], the group the Home's roster names. */
    @Serializable
    @SerialName("homegroup.invite")
    data class Invite(val homeId: String, val groupId: String, val invite: String) : HomeGroupMessage

    companion object {
        private val json = Json {
            classDiscriminator = "kind"
            ignoreUnknownKeys = true
        }

        fun encode(message: HomeGroupMessage): ByteArray = json.encodeToString(serializer(), message).encodeToByteArray()

        /** Decodes a direct message's base64 payload; null when it isn't a Home-group message. */
        fun decode(base64Payload: String?): HomeGroupMessage? {
            val bytes = base64Payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() } ?: return null
            return runCatching { json.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()
        }
    }
}
