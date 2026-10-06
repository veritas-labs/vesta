package uk.veritaslabs.vesta.mailbox

/**
 * A writer's snapshot of a Home's documents, which lets it delete its old entries
 * (docs/adr/ADR-020-syncing-without-overlapping-online-time.md). A generation is the writer's whole state of every document at a
 * moment after it had deposited every entry up to [SnapshotManifest.coversThrough], so a reader that has none of those entries
 * can take the snapshot instead.
 *
 * Keys: `snapshot/<writer>/<generation>/<chunk>` hold ordinary [MailboxEntry] values (one group of parts per document, chunks
 * numbered from 0), and `snapshot/<writer>/<generation>/manifest` is written last, so a generation without one is incomplete.
 */
internal object SnapshotKeys {
    private const val Prefix = "snapshot/"
    private const val Manifest = "manifest"
    private const val Digits = 10

    fun isSnapshotKey(key: String) = key.startsWith(Prefix)

    fun chunk(writer: String, generation: Long, chunk: Int) = "$Prefix$writer/${pad(generation)}/${pad(chunk.toLong())}"

    fun manifest(writer: String, generation: Long) = "$Prefix$writer/${pad(generation)}/$Manifest"

    /** What a snapshot key names. [chunk] is null for the manifest. */
    class Parsed(val writer: String, val generation: Long, val chunk: Int?)

    fun parse(key: String): Parsed? {
        if (!isSnapshotKey(key)) return null
        val parts = key.removePrefix(Prefix).split('/')
        if (parts.size != 3 || parts[0].isEmpty()) return null
        val generation = parts[1].takeIf { it.length == Digits }?.toLongOrNull() ?: return null
        if (parts[2] == Manifest) return Parsed(parts[0], generation, null)
        val chunk = parts[2].takeIf { it.length == Digits }?.toIntOrNull() ?: return null
        return Parsed(parts[0], generation, chunk)
    }

    private fun pad(value: Long) = value.toString().padStart(Digits, '0')
}

/** Names what a snapshot covers and how many chunks it has: `[version = 1][covers through: i64][chunk count: i32]`. */
internal class SnapshotManifest(val coversThrough: Long, val chunkCount: Int) {

    fun encode(): ByteArray =
        byteArrayOf(Version) + ByteArray(8) { (coversThrough shr (56 - 8 * it)).toByte() } +
            ByteArray(4) { (chunkCount shr (24 - 8 * it)).toByte() }

    companion object {
        const val Version: Byte = 1

        fun decode(bytes: ByteArray): SnapshotManifest? {
            if (bytes.size != 13 || bytes[0] != Version) return null
            val through = (1..8).fold(0L) { all, i -> (all shl 8) or (bytes[i].toLong() and 0xFF) }
            val count = (9..12).fold(0) { all, i -> (all shl 8) or (bytes[i].toInt() and 0xFF) }
            return SnapshotManifest(through, count).takeIf { through >= 0 && count > 0 }
        }
    }
}
