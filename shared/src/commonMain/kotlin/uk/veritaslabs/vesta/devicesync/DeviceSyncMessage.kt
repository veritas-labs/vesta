package uk.veritaslabs.vesta.devicesync

import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a user's devices say to each other over x0x direct messages while pairing and afterwards
 * (docs/adr/ADR-012-device-sync-over-x0x-group.md). Discriminated by `kind`, with the `devicesync.` prefix keeping
 * these apart from ADR-009 agent commands, which arrive on the same stream and are simply not decoded here.
 */
@Serializable
sealed interface DeviceSyncMessage {

    /** From the device that read a pairing link: the group invite for the device that showed it, and who is inviting. */
    @Serializable
    @SerialName("devicesync.invite")
    data class Invite(val invite: String, val name: String) : DeviceSyncMessage

    /** "This is my name" — sent to a member we have just seen, so every device can label the others. */
    @Serializable
    @SerialName("devicesync.hello")
    data class Hello(val name: String) : DeviceSyncMessage

    /** The group is being replaced to cut out a lost device: join [invite] and leave [oldGroupId]. */
    @Serializable
    @SerialName("devicesync.rotate")
    data class Rotate(val invite: String, val oldGroupId: String, val name: String) : DeviceSyncMessage

    companion object {
        private val json = Json {
            classDiscriminator = "kind"
            ignoreUnknownKeys = true
        }

        fun encode(message: DeviceSyncMessage): ByteArray =
            json.encodeToString(serializer(), message).encodeToByteArray()

        /** Decodes a direct message's base64 payload; null when it isn't a device-sync message. */
        fun decode(base64Payload: String?): DeviceSyncMessage? {
            val bytes = base64Payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() } ?: return null
            return runCatching { json.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()
        }
    }
}
