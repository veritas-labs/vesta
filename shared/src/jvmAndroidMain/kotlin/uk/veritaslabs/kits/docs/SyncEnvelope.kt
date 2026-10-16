package uk.veritaslabs.kits.docs

/**
 * The wire format of ADR-006: `[1 byte: id length N][N bytes: document id, UTF-8][Automerge sync message]`. An
 * Automerge sync message doesn't say which document it is for, and every direct message arrives on one stream.
 */
public class SyncEnvelope(val documentId: String, val message: ByteArray) {

    fun encode(): ByteArray {
        val id = documentId.encodeToByteArray()
        require(id.size in 1..255) { "document id must be 1-255 bytes" }
        return byteArrayOf(id.size.toByte()) + id + message
    }

    companion object {
        /** Null when [bytes] isn't an envelope, e.g. a device-sync or agent JSON message that shares the stream. */
        fun decode(bytes: ByteArray): SyncEnvelope? {
            if (bytes.isEmpty()) return null
            val length = bytes[0].toInt() and 0xFF
            if (length == 0 || bytes.size < 1 + length) return null
            val id = runCatching { bytes.copyOfRange(1, 1 + length).decodeToString(throwOnInvalidSequence = true) }.getOrNull()
                ?: return null
            return SyncEnvelope(id, bytes.copyOfRange(1 + length, bytes.size))
        }
    }
}
