package uk.veritaslabs.vesta.mailbox

import kotlin.random.Random

/**
 * One value in a Home's `mailbox` group store (docs/adr/ADR-020-syncing-without-overlapping-online-time.md): a slice of
 * the Automerge changes a device made to one document. x0x refuses a value over 64 KiB, so changes larger than
 * [MaxPartBytes] are cut into parts, written under consecutive keys and put back together by the reader.
 *
 * Layout: `[version = 1][id length N][N bytes: document id, UTF-8][deposit: i32][part: u16][part count: u16][payload]`. The
 * version byte lets a sealed form be added later without breaking old entries. The deposit number is random per attempt,
 * so a reader never joins parts of two attempts when a failed deposit is written again over the same keys.
 */
internal class MailboxEntry(
    val documentId: String,
    val deposit: Int,
    val part: Int,
    val partCount: Int,
    val payload: ByteArray,
) {

    fun encode(): ByteArray {
        val id = documentId.encodeToByteArray()
        require(id.size in 1..255) { "document id must be 1-255 bytes" }
        require(partCount in 1..MaxParts && part in 0 until partCount) { "part $part of $partCount is out of range" }
        return byteArrayOf(Version, id.size.toByte()) + id +
            byteArrayOf(
                (deposit shr 24).toByte(), (deposit shr 16).toByte(), (deposit shr 8).toByte(), deposit.toByte(),
                (part shr 8).toByte(), part.toByte(), (partCount shr 8).toByte(), partCount.toByte(),
            ) + payload
    }

    companion object {
        const val Version: Byte = 1

        /** Well under x0x's 64 KiB value limit, which leaves room for the header. */
        const val MaxPartBytes = 48 * 1024
        const val MaxParts = 0xFFFF

        /** The entries that carry [changes] for [documentId], in order. */
        fun split(documentId: String, changes: ByteArray, deposit: Int = Random.nextInt()): List<MailboxEntry> {
            val count = maxOf(1, (changes.size + MaxPartBytes - 1) / MaxPartBytes)
            require(count <= MaxParts) { "too many changes for one deposit" }
            return List(count) { part ->
                MailboxEntry(documentId, deposit, part, count, changes.copyOfRange(part * MaxPartBytes, minOf(changes.size, (part + 1) * MaxPartBytes)))
            }
        }

        /** Null when [bytes] isn't an entry this version understands: a newer format, or damaged. */
        fun decode(bytes: ByteArray): MailboxEntry? {
            if (bytes.size < 2 || bytes[0] != Version) return null
            val idLength = bytes[1].toInt() and 0xFF
            val headerEnd = 2 + idLength + 8
            if (idLength == 0 || bytes.size < headerEnd) return null
            val id = runCatching { bytes.copyOfRange(2, 2 + idLength).decodeToString(throwOnInvalidSequence = true) }.getOrNull() ?: return null
            fun u16(at: Int) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
            val at = 2 + idLength
            val deposit = (u16(at) shl 16) or u16(at + 2)
            val part = u16(at + 4)
            val count = u16(at + 6)
            if (count == 0 || part >= count) return null
            return MailboxEntry(id, deposit, part, count, bytes.copyOfRange(headerEnd, bytes.size))
        }

        /** The store key for [writerAgentId]'s entry number [sequence]; zero-padded so keys sort in writing order. */
        fun key(writerAgentId: String, sequence: Long): String = "$writerAgentId/${sequence.toString().padStart(SequenceDigits, '0')}"

        /** The writer and sequence a store key names, or null for a key that isn't a mailbox entry's. */
        fun parseKey(key: String): Pair<String, Long>? {
            if (SnapshotKeys.isSnapshotKey(key)) return null
            val split = key.lastIndexOf('/')
            if (split <= 0) return null
            val sequence = key.substring(split + 1).takeIf { it.length == SequenceDigits }?.toLongOrNull() ?: return null
            return key.substring(0, split) to sequence
        }

        private const val SequenceDigits = 10
    }
}
