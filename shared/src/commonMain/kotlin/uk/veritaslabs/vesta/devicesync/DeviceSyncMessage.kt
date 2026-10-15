package uk.veritaslabs.vesta.devicesync

import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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

        private const val KindPrefix = "devicesync."

        private val knownKinds: Set<String> by lazy { serializer().descriptor.getElementDescriptor(1).elementNames.toSet() }

        /**
         * The `kind` of a direct message that belongs to this family (its kind starts with `devicesync.`) but that this version
         * doesn't recognise, such as one a newer Vesta added; null for anything else, including a message of another family
         * (an agent command, say) and a known kind. Lets a caller record an unknown message without counting foreign traffic as one.
         */
        fun unrecognisedKind(base64Payload: String?): String? {
            val kind = stringField(base64Payload, "kind") ?: return null
            return kind.takeIf { it.startsWith(KindPrefix) && it !in knownKinds }
        }

        /** The string value of [field] in a direct message's JSON payload, or null when there isn't one. */
        fun stringField(base64Payload: String?, field: String): String? {
            val bytes = base64Payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() } ?: return null
            return runCatching {
                (json.parseToJsonElement(bytes.decodeToString()).jsonObject[field] as? JsonPrimitive)?.takeIf { it.isString }?.content
            }.getOrNull()
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
