package uk.veritaslabs.vesta.blobs

import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The one direct message of blob transfer (ADR-023): "I want this blob". The `blob.` prefix keeps it apart from the other JSON messages on the
 * stream (Home join, Home group, agent commands), and it is not a sync envelope. A holder answers with a file transfer, not a message.
 */
@Serializable
sealed interface BlobMessage {

    /** A device that lacks the blob [hash] referenced by the document of [homeId], to a device of that Home. Repeated, with backoff, until it arrives. */
    @Serializable
    @SerialName("blob.want")
    data class Want(val homeId: String, val hash: String) : BlobMessage

    companion object {
        private val json = Json {
            classDiscriminator = "kind"
            ignoreUnknownKeys = true
        }

        fun encode(message: BlobMessage): ByteArray = json.encodeToString(serializer(), message).encodeToByteArray()

        /** Decodes a direct message's base64 payload; null when it isn't a blob message. */
        fun decode(base64Payload: String?): BlobMessage? {
            val bytes = base64Payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() } ?: return null
            return runCatching { json.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()
        }
    }
}
