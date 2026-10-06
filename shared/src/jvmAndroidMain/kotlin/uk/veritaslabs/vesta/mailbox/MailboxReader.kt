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
 * after) converge to the same document.
 *
 * A writer's entries are read without gaps, from the one after the last dealt with (saved per writer): a replica can
 * hand over entry 5 before entry 4, so a missing number means "not here yet" and the rest waits. The place only moves
 * past entries that were applied or never can be: a group of parts still arriving is read again next time, one cut short by a
 * newer deposit is skipped, and an entry from a newer format or a damaged one is skipped.
 *
 * A gap that the writer's newest complete snapshot ([SnapshotKeys]) covers is not waited for: the writer has deleted those
 * entries, or they are about to be, and the snapshot holds everything they did, so it is applied and the place moves to the end
 * of what it covers. That is how a device that was away for longer than the writer keeps entries (or is new) catches up.
 */
internal class MailboxReader(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val documents: MailboxDocuments,
    private val storeId: suspend (homeId: String) -> String?,
) {

    private val lock = Mutex()

    /** What a writer has in the store, as listed. */
    private class Listing(val sequences: MutableSet<Long> = mutableSetOf(), val manifests: MutableSet<Long> = mutableSetOf(), val chunks: MutableMap<Long, MutableSet<Int>> = mutableMapOf())

    /** Reads [homeId]'s mailbox; returns how many deposits were applied. A Home with no open store reads nothing. */
    suspend fun fetch(homeId: String): Int = lock.withLock {
        val store = storeId(homeId) ?: return 0
        val self = checkNotNull(client.agent().agentId) { "x0x did not report this device's agent id" }
        val writers = mutableMapOf<String, Listing>()
        for (key in client.listStoreKeys(store).map { it.key }) {
            MailboxEntry.parseKey(key)?.let { (writer, sequence) -> writers.getOrPut(writer) { Listing() }.sequences += sequence }
            SnapshotKeys.parse(key)?.let { snapshot ->
                val listing = writers.getOrPut(snapshot.writer) { Listing() }
                if (snapshot.chunk == null) listing.manifests += snapshot.generation
                else listing.chunks.getOrPut(snapshot.generation) { mutableSetOf() } += snapshot.chunk
            }
        }
        var applied = 0
        for ((writer, listing) in writers) {
            if (writer != self) applied += readWriter(homeId, store, writer, listing)
        }
        applied
    }

    private suspend fun readWriter(homeId: String, store: String, writer: String, listing: Listing): Int {
        val start = database.mailboxQueries.selectLastRead(homeId, writer).awaitAsOneOrNull() ?: -1L
        var mark = start
        var applied = 0
        var assembler = Assembler()
        var sequence = start + 1
        var snapshot: Snapshot? = null // loaded once, if a gap needs it
        var snapshotLoaded = false
        try {
            while (true) {
                if (sequence !in listing.sequences) {
                    if (!snapshotLoaded) {
                        snapshotLoaded = true
                        snapshot = latestCompleteSnapshot(store, writer, listing)
                    }
                    val covering = snapshot?.takeIf { it.coversThrough >= sequence } ?: break
                    applied += applySnapshot(homeId, covering)
                    assembler = Assembler()
                    mark = covering.coversThrough
                    sequence = covering.coversThrough + 1
                    continue
                }
                val bytes = client.getValue(store, MailboxEntry.key(writer, sequence)) ?: break // gone since it was listed: try later
                val entry = MailboxEntry.decode(bytes)
                if (entry == null) {
                    // Nothing we can read: a newer format or damaged. Skip it, and the group it may have been part of.
                    assembler = Assembler()
                    mark = sequence++
                    continue
                }
                when (val outcome = assembler.offer(entry)) {
                    Assembler.Outcome.Stray -> mark = sequence // the rest of its group was lost or replaced
                    Assembler.Outcome.Pending -> Unit
                    is Assembler.Outcome.Complete -> {
                        if (apply(homeId, outcome.documentId, outcome.changes)) applied++
                        mark = sequence
                    }
                }
                sequence++
            }
        } finally {
            if (mark != start) database.mailboxQueries.upsertLastRead(homeId, writer, mark)
        }
        return applied
    }

    private class Snapshot(val generation: Long, val coversThrough: Long, val store: String, val writer: String, val chunkCount: Int)

    /** The newest generation whose manifest and every chunk are listed, or null. */
    private suspend fun latestCompleteSnapshot(store: String, writer: String, listing: Listing): Snapshot? {
        for (generation in listing.manifests.sortedDescending()) {
            val manifest = client.getValue(store, SnapshotKeys.manifest(writer, generation))?.let { SnapshotManifest.decode(it) } ?: continue
            val present = listing.chunks[generation].orEmpty()
            if ((0 until manifest.chunkCount).all { it in present }) return Snapshot(generation, manifest.coversThrough, store, writer, manifest.chunkCount)
        }
        return null
    }

    /** Applies every document of [snapshot]; returns how many were applied. A chunk that has gone (it was replaced) applies none. */
    private suspend fun applySnapshot(homeId: String, snapshot: Snapshot): Int {
        val assembler = Assembler()
        val documentsAndChanges = mutableListOf<Pair<String, ByteArray>>()
        for (chunk in 0 until snapshot.chunkCount) {
            val bytes = client.getValue(snapshot.store, SnapshotKeys.chunk(snapshot.writer, snapshot.generation, chunk)) ?: return 0
            val entry = MailboxEntry.decode(bytes) ?: continue
            (assembler.offer(entry) as? Assembler.Outcome.Complete)?.let { documentsAndChanges += it.documentId to it.changes }
        }
        return documentsAndChanges.count { (documentId, changes) -> apply(homeId, documentId, changes) }
    }

    /** True when the changes were applied; false when skipped (a document this version doesn't know, or damaged changes). */
    private suspend fun apply(homeId: String, documentId: String, changes: ByteArray): Boolean {
        val document = documents.open(homeId, documentId) ?: return false
        return try {
            document.applyChanges(changes)
            true
        } catch (e: RuntimeException) {
            // Automerge's exception class isn't visible outside its package. Anything else (a failed save, say) is not the
            // changes' fault, so it goes on up and the entries are read again next time.
            if (e.javaClass.name != AutomergeExceptionClass) throw e
            false // not valid changes; reading them again would not help
        }
    }

    /** Puts a deposit's parts back together, one group at a time. */
    private class Assembler {
        private var documentId = ""
        private var deposit = 0
        private var partCount = 0
        private var parts: MutableList<ByteArray>? = null

        sealed interface Outcome {
            /** A part that belongs to no group in progress. */
            data object Stray : Outcome

            /** Taken, and more parts are due. */
            data object Pending : Outcome

            class Complete(val documentId: String, val changes: ByteArray) : Outcome
        }

        fun offer(entry: MailboxEntry): Outcome {
            if (entry.part == 0) {
                documentId = entry.documentId
                deposit = entry.deposit
                partCount = entry.partCount
                parts = mutableListOf()
            } else if (parts == null || entry.documentId != documentId || entry.deposit != deposit ||
                entry.partCount != partCount || entry.part != parts!!.size
            ) {
                parts = null
                return Outcome.Stray
            }
            val taken = parts!!.also { it += entry.payload }
            if (taken.size < partCount) return Outcome.Pending
            parts = null
            return Outcome.Complete(documentId, taken.fold(ByteArray(0)) { all, part -> all + part })
        }
    }
}
