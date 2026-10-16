package uk.veritaslabs.kits.docs

import uk.veritaslabs.kits.KitsInternalApi

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.x0x.X0xClient

private const val AutomergeExceptionClass = "org.automerge.AutomergeException"

/**
 * Applies what other devices left in a Space's `mailbox` store to this device's documents
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
 * newer deposit is skipped, and a damaged one is skipped. An entry from a newer format is noted in Sync activity (ADR-034) and
 * not read, and the saved place stays before it, so a Vesta that can read it later still finds it; entries after it are read as usual,
 * and this run remembers how far ([Held]) so it doesn't apply them again every round. Only a snapshot that covers it ends the wait.
 * The same holds for an entry (or snapshot document) for a document this version doesn't have, or a Space it doesn't have yet.
 *
 * A gap that the writer's newest complete snapshot ([SnapshotKeys]) covers is not waited for: the writer has deleted those
 * entries, or they are about to be, and the snapshot holds everything they did, so it is applied and the place moves to the end
 * of what it covers. That is how a device that was away for longer than the writer keeps entries (or is new) catches up.
 */
@KitsInternalApi
public class MailboxReader(
    private val client: X0xClient,
    private val database: KitsDatabase,
    private val documents: MailboxDocuments,
    private val recorder: SyncEventRecorder? = null,
    private val storeId: suspend (spaceId: String) -> String?,
) {

    private val lock = Mutex()

    /** Where a round read to past an entry in a newer format, for the next round to resume from; lost on restart, which only means reading again. */
    private class Held(val at: Long, val doneThrough: Long)

    private val held = mutableMapOf<Pair<String, String>, Held>()

    /** What a writer has in the store, as listed. */
    private class Listing(val sequences: MutableSet<Long> = mutableSetOf(), val manifests: MutableSet<Long> = mutableSetOf(), val chunks: MutableMap<Long, MutableSet<Int>> = mutableMapOf())

    /** Reads [spaceId]'s mailbox; returns how many deposits were applied. A Space with no open store reads nothing. */
    suspend fun fetch(spaceId: String): Int = lock.withLock {
        val store = storeId(spaceId) ?: return 0
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
            if (writer != self) applied += readWriter(spaceId, store, writer, listing)
        }
        applied
    }

    private suspend fun readWriter(spaceId: String, store: String, writer: String, listing: Listing): Int {
        val start = database.mailboxQueries.selectLastRead(spaceId, writer).awaitAsOneOrNull() ?: -1L
        var mark = start
        var applied = 0
        var assembler = Assembler()
        var sequence = start + 1
        var snapshot: Snapshot? = null // loaded once, if a gap needs it
        var snapshotLoaded = false
        val key = spaceId to writer
        var earlier = held[key]?.takeIf { start + 1 <= it.at } // an entry in a newer format an earlier round stopped the saved place at
        var holding = false // an entry in a newer format was seen: the saved place stays before it
        var heldAt = -1L
        var groupStart = -1L // the sequence of the current group's first part, where the place must stay if its document is unknown
        var through = start // how far the place would have moved, were nothing held
        fun advance(to: Long) {
            through = to
            if (!holding) mark = to
        }
        try {
            while (true) {
                if (sequence !in listing.sequences) {
                    if (!snapshotLoaded) {
                        snapshotLoaded = true
                        snapshot = latestCompleteSnapshot(spaceId, store, writer, listing)
                    }
                    val covering = snapshot?.takeIf { it.coversThrough >= sequence } ?: break
                    val gapStart = sequence
                    val result = applySnapshot(spaceId, covering)
                    applied += result.applied
                    assembler = Assembler()
                    through = covering.coversThrough
                    if (result.unknown.isEmpty()) {
                        mark = through
                        holding = false // the snapshot holds everything the writer did up to here
                    } else {
                        // It holds a document this version lacks: keep the place before the gap, so the snapshot is read again once it is known.
                        result.unknown.forEach { recorder?.unsupportedVersion(spaceId, SyncModules.Mailbox, writer, it) }
                        if (!holding) heldAt = gapStart
                        holding = true
                    }
                    earlier = null
                    sequence = covering.coversThrough + 1
                    continue
                }
                val bytes = client.getValue(store, MailboxEntry.key(writer, sequence)) ?: break // gone since it was listed: try later
                val entry = MailboxEntry.decode(bytes)
                val resumed = earlier
                if (resumed != null && sequence == resumed.at) {
                    earlier = null
                    val version = if (entry == null) MailboxEntry.newerVersion(bytes) else null
                    if (version != null) {
                        // Still too new. What follows it was applied in an earlier round: carry on from there.
                        recorder?.unsupportedVersion(spaceId, SyncModules.Mailbox, writer, "v$version")
                        holding = true
                        heldAt = resumed.at
                        through = resumed.doneThrough
                        sequence = resumed.doneThrough + 1
                        assembler = Assembler()
                        continue
                    }
                }
                if (entry == null) {
                    // Nothing we can read: a newer format or damaged. Skip it, and the group it may have been part of.
                    assembler = Assembler()
                    val version = MailboxEntry.newerVersion(bytes)
                    if (version != null) {
                        if (!holding) heldAt = sequence
                        holding = true
                        recorder?.unsupportedVersion(spaceId, SyncModules.Mailbox, writer, "v$version")
                    }
                    advance(sequence)
                    sequence++
                    continue
                }
                if (entry.part == 0) groupStart = sequence
                when (val outcome = assembler.offer(entry)) {
                    Assembler.Outcome.Stray -> advance(sequence) // the rest of its group was lost or replaced
                    Assembler.Outcome.Pending -> Unit
                    is Assembler.Outcome.Complete -> {
                        when (apply(spaceId, outcome.documentId, outcome.changes)) {
                            Applied.Yes -> applied++
                            Applied.Damaged -> Unit
                            Applied.UnknownDocument -> {
                                // A document this version lacks (or a Space it doesn't have yet): keep the place before the group, so an update reads it.
                                recorder?.unsupportedVersion(spaceId, SyncModules.Mailbox, writer, documentType(outcome.documentId))
                                if (!holding) heldAt = groupStart
                                holding = true
                            }
                        }
                        advance(sequence)
                    }
                }
                sequence++
            }
        } finally {
            if (holding) held[key] = Held(heldAt, through) else held.remove(key)
            if (mark != start) database.mailboxQueries.upsertLastRead(spaceId, writer, mark)
        }
        return applied
    }

    private class Snapshot(val generation: Long, val coversThrough: Long, val store: String, val writer: String, val chunkCount: Int)

    /** The newest generation whose manifest and every chunk are listed, or null. */
    private suspend fun latestCompleteSnapshot(spaceId: String, store: String, writer: String, listing: Listing): Snapshot? {
        for (generation in listing.manifests.sortedDescending()) {
            val bytes = client.getValue(store, SnapshotKeys.manifest(writer, generation)) ?: continue
            val manifest = SnapshotManifest.decode(bytes)
            if (manifest == null) {
                SnapshotManifest.newerVersion(bytes)?.let { recorder?.unsupportedVersion(spaceId, SyncModules.Mailbox, writer, "v$it") }
                continue
            }
            val present = listing.chunks[generation].orEmpty()
            if ((0 until manifest.chunkCount).all { it in present }) return Snapshot(generation, manifest.coversThrough, store, writer, manifest.chunkCount)
        }
        return null
    }

    /** What applying a snapshot did: how many documents were applied, and the types of those this version has no document for. */
    private class SnapshotResult(val applied: Int, val unknown: Set<String>)

    private enum class Applied { Yes, Damaged, UnknownDocument }

    /** The document type of a mailbox document id (`<space id>:<type>`), for the log. */
    private fun documentType(documentId: String): String = documentId.substringAfterLast(':')

    /** Applies every document of [snapshot]. A chunk that has gone (it was replaced) applies none. */
    private suspend fun applySnapshot(spaceId: String, snapshot: Snapshot): SnapshotResult {
        val assembler = Assembler()
        val documentsAndChanges = mutableListOf<Pair<String, ByteArray>>()
        for (chunk in 0 until snapshot.chunkCount) {
            val bytes = client.getValue(snapshot.store, SnapshotKeys.chunk(snapshot.writer, snapshot.generation, chunk)) ?: return SnapshotResult(0, emptySet())
            val entry = MailboxEntry.decode(bytes) ?: continue
            (assembler.offer(entry) as? Assembler.Outcome.Complete)?.let { documentsAndChanges += it.documentId to it.changes }
        }
        val results = documentsAndChanges.map { (documentId, changes) -> documentId to apply(spaceId, documentId, changes) }
        return SnapshotResult(
            results.count { it.second == Applied.Yes },
            results.filter { it.second == Applied.UnknownDocument }.map { documentType(it.first) }.toSet(),
        )
    }

    /** Applies the changes; [Applied.UnknownDocument] when this version has no such document (keep the entry), [Applied.Damaged] when the changes are invalid. */
    private suspend fun apply(spaceId: String, documentId: String, changes: ByteArray): Applied {
        val document = documents.open(spaceId, documentId) ?: return Applied.UnknownDocument
        return try {
            document.applyChanges(changes)
            Applied.Yes
        } catch (e: RuntimeException) {
            // Automerge's exception class isn't visible outside its package. Anything else (a failed save, say) is not the
            // changes' fault, so it goes on up and the entries are read again next time.
            if (e.javaClass.name != AutomergeExceptionClass) throw e
            Applied.Damaged // not valid changes; reading them again would not help
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
