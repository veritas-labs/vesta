package uk.veritaslabs.vesta.mailbox

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.x0x.X0xClient

private const val AutomergeExceptionClass = "org.automerge.AutomergeException"

/**
 * Applies what other devices left in a Home's `mailbox` store to this device's documents
 * (docs/adr/ADR-020-syncing-without-overlapping-online-time.md).
 *
 * [fetch] lists the store, and for each other writer reads the entries after the last one it dealt with, in order, puts a
 * deposit's parts back together and applies it through [MailboxDocuments]. Applying is idempotent, and Automerge queues
 * changes whose earlier ones haven't arrived, so entries can be repeated, and a mailbox read before a direct sync (or
 * after) converges to the same document. The place reached is saved per writer, and only moves past entries that
 * were applied or can never be: a group of parts that is still arriving is read again next time, one that was cut short
 * by a newer deposit is skipped, and an entry from a newer format or damaged is skipped.
 */
internal class MailboxReader(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val documents: MailboxDocuments,
    private val storeId: suspend (homeId: String) -> String?,
) {

    private val lock = Mutex()

    /** Reads [homeId]'s mailbox; returns how many deposits were applied. A Home with no open store reads nothing. */
    suspend fun fetch(homeId: String): Int = lock.withLock {
        val store = storeId(homeId) ?: return 0
        val self = checkNotNull(client.agent().agentId) { "x0x did not report this device's agent id" }
        val bySequence = client.listStoreKeys(store).mapNotNull { key -> MailboxEntry.parseKey(key.key)?.let { (writer, sequence) -> writer to sequence } }
            .filter { it.first != self }
            .groupBy({ it.first }, { it.second })
        var applied = 0
        for ((writer, sequences) in bySequence) {
            applied += readWriter(homeId, store, writer, sequences.toSet())
        }
        applied
    }

    private suspend fun readWriter(homeId: String, store: String, writer: String, sequences: Set<Long>): Int {
        val start = database.mailboxQueries.selectLastRead(homeId, writer).awaitAsOneOrNull() ?: -1L
        var mark = start
        var applied = 0
        var group: Group? = null
        var sequence = start + 1
        try {
            while (sequence in sequences) {
                val bytes = client.getValue(store, MailboxEntry.key(writer, sequence)) ?: break // gone since it was listed: try later
                val entry = MailboxEntry.decode(bytes)
                if (entry == null) {
                    // Nothing we can read: a newer format or damaged. Skip it, and the group it may have been part of.
                    group = null
                    mark = sequence++
                    continue
                }
                if (entry.part == 0) {
                    group = Group(entry.documentId, entry.deposit, entry.partCount, sequence)
                } else if (group == null || !group.accepts(entry)) {
                    group = null // a stray part: the rest of its group was lost or replaced
                    mark = sequence++
                    continue
                }
                val current = group
                current.parts += entry.payload
                if (current.parts.size == current.partCount) {
                    if (apply(homeId, current)) applied++
                    mark = sequence
                    group = null
                }
                sequence++
            }
        } finally {
            if (mark != start) database.mailboxQueries.upsertLastRead(homeId, writer, mark)
        }
        return applied
    }

    /** True when the deposit was applied; false when it was skipped (a document this version doesn't know, or damaged changes). */
    private suspend fun apply(homeId: String, group: Group): Boolean {
        val document = documents.open(homeId, group.documentId) ?: return false
        return try {
            document.applyChanges(group.parts.fold(ByteArray(0)) { all, part -> all + part })
            true
        } catch (e: RuntimeException) {
            // Automerge's exception class isn't visible outside its package. Anything else (a failed save, say) is not the
            // changes' fault, so it goes on up and the entries are read again next time.
            if (e.javaClass.name != AutomergeExceptionClass) throw e
            false // not valid changes; reading them again would not help
        }
    }

    private class Group(val documentId: String, val deposit: Int, val partCount: Int, val firstSequence: Long) {
        val parts = mutableListOf<ByteArray>()

        /** Whether [entry], read at the next number, is the next part of this group. */
        fun accepts(entry: MailboxEntry) =
            entry.documentId == documentId && entry.deposit == deposit && entry.partCount == partCount && entry.part == parts.size
    }
}
