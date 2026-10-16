package uk.veritaslabs.vesta.mailbox

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.x0x.X0xClient

/**
 * Puts what this device has changed in a Home's documents into the Home's `mailbox` store, so a device that is offline
 * now can pick it up later from any replica (docs/adr/ADR-020-syncing-without-overlapping-online-time.md).
 *
 * [deposit] writes the changes made since its last deposit, as entries under `<this agent>/<sequence>`. Nothing new
 * writes nothing. The deposit is only remembered once every entry is in the store, so a failure repeats the same range
 * on the next call, over the same entry numbers: readers go through a writer's entries without gaps, so a number must
 * never be left unused. Each attempt carries its own deposit number, so a reader doesn't mix the parts of two.
 *
 * [compact] keeps the store from growing for ever: every [snapshotInterval] it publishes a snapshot of the Home's documents
 * ([SnapshotKeys]), and it deletes its own entries once a snapshot that covers them is [retention] old, which leaves
 * a device that has been away that long to take the snapshot instead. Only the newest snapshot is kept.
 */
internal class MailboxWriter(
    private val client: X0xClient,
    private val database: KitsDatabase,
    private val documents: MailboxDocuments,
    private val retention: Duration = 30.days,
    private val snapshotInterval: Duration = 15.days,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val storeId: suspend (homeId: String) -> String?,
) {

    // Entry numbers are handed out and used up one deposit at a time.
    private val lock = Mutex()

    /** Deposits every document of [homeId]; returns how many entries were written. A Home with no open store writes none. */
    suspend fun deposit(homeId: String): Int = documents.documentIds(homeId).sumOf { deposit(homeId, it) }

    /** Deposits [documentId] of [homeId]; returns how many entries were written. */
    suspend fun deposit(homeId: String, documentId: String): Int = lock.withLock {
        val store = storeId(homeId) ?: return 0
        val document = documents.open(homeId, documentId) ?: return 0
        // Read first: changes made while the deposit runs are then in the next one as well, and repeating is harmless.
        val heads = document.heads()
        val deposited = database.mailboxQueries.selectHeads(homeId, documentId).awaitAsOneOrNull() ?: ByteArray(0)
        if (heads.isEmpty() || heads.contentEquals(deposited)) return 0
        val changes = document.changesSince(deposited)
        if (changes.isEmpty()) return 0

        val entries = MailboxEntry.split(documentId, changes)
        val writer = checkNotNull(client.agent().agentId) { "x0x did not report this device's agent id" }
        val first = nextSequence(homeId, store, writer)
        entries.forEachIndexed { index, entry ->
            client.putValue(store, MailboxEntry.key(writer, first + index), entry.encode())
        }
        // Numbers first: if only the heads were lost the range is sent again under new numbers, which is harmless.
        database.mailboxQueries.upsertNextSequence(homeId, first + entries.size)
        database.mailboxQueries.upsertHeads(homeId, documentId, heads)
        entries.size
    }

    /**
     * The number of this device's next entry. A device with no record (a restored or new database under an old agent id)
     * starts after the highest of its own entries still in the store, so it never overwrites one.
     */
    private suspend fun nextSequence(homeId: String, store: String, writer: String): Long =
        database.mailboxQueries.selectNextSequence(homeId).awaitAsOneOrNull()
            ?: client.listStoreKeys(store).map { it.key }.let { keys ->
                val entries = keys.mapNotNull { MailboxEntry.parseKey(it) }.filter { it.first == writer }.map { it.second + 1 }
                // Entries a snapshot covers may all be deleted, but their numbers are still taken.
                val covered = keys.mapNotNull { SnapshotKeys.parse(it) }.filter { it.writer == writer && it.chunk == null }
                    .mapNotNull { client.getValue(store, SnapshotKeys.manifest(writer, it.generation)) }
                    .mapNotNull { SnapshotManifest.decode(it)?.let { manifest -> manifest.coversThrough + 1 } }
                (entries + covered).maxOrNull() ?: 0L
            }

    /**
     * Publishes a snapshot of [homeId]'s documents if one is due, and deletes the entries (and older snapshots) it makes
     * redundant once it is old enough. Cheap when nothing is due: it only reads this device's own records.
     */
    suspend fun compact(homeId: String) = lock.withLock {
        val store = storeId(homeId) ?: return
        val writer = checkNotNull(client.agent().agentId) { "x0x did not report this device's agent id" }
        val now = nowMillis()
        val lastEntry = (database.mailboxQueries.selectNextSequence(homeId).awaitAsOneOrNull() ?: return) - 1
        val latest = database.mailboxQueries.selectLatestSnapshot(homeId).awaitAsOneOrNull()
        val due = lastEntry > (latest?.coversThrough ?: -1) &&
            (latest == null || now - latest.createdAt >= snapshotInterval.inWholeMilliseconds)
        if (due) snapshot(homeId, store, writer, lastEntry, (latest?.generation ?: 0L) + 1, now)

        val expired = database.mailboxQueries.selectUnprunedSnapshotCreatedBefore(homeId, now - retention.inWholeMilliseconds)
            .awaitAsOneOrNull() ?: return
        prune(homeId, store, writer, expired.coversThrough, expired.generation)
    }

    /** Publishes generation [generation], covering this device's entries through [coversThrough]. */
    private suspend fun snapshot(homeId: String, store: String, writer: String, coversThrough: Long, generation: Long, now: Long) {
        var chunk = 0
        for (documentId in documents.documentIds(homeId)) {
            val document = documents.open(homeId, documentId) ?: continue
            val everything = document.changesSince(ByteArray(0))
            if (everything.isEmpty()) continue
            for (entry in MailboxEntry.split(documentId, everything)) {
                client.putValue(store, SnapshotKeys.chunk(writer, generation, chunk++), entry.encode())
            }
        }
        if (chunk == 0) return // nothing to snapshot yet
        // Last, so a generation with a manifest is complete.
        client.putValue(store, SnapshotKeys.manifest(writer, generation), SnapshotManifest(coversThrough, chunk).encode())
        database.mailboxQueries.insertSnapshot(homeId, generation, coversThrough, now)
        // Only the newest is kept. Best effort: a failure here is repeated by the next prune.
        runCatching { dropSupersededSnapshots(homeId, store, writer, client.listStoreKeys(store).map { it.key }) }
    }

    /**
     * Deletes this device's entries through [coversThrough], which the snapshot of [generation] (old enough to have given every
     * reader time to read them) covers, and every snapshot but the newest. Nothing is recorded as done until it all is, so a
     * failure is repeated.
     */
    private suspend fun prune(homeId: String, store: String, writer: String, coversThrough: Long, generation: Long) {
        val keys = client.listStoreKeys(store).map { it.key }
        keys.forEach { key ->
            val entry = MailboxEntry.parseKey(key)
            if (entry != null && entry.first == writer && entry.second <= coversThrough) client.deleteValue(store, key)
        }
        dropSupersededSnapshots(homeId, store, writer, keys)
        database.mailboxQueries.markSnapshotsPruned(homeId, generation)
    }

    /** Deletes this device's snapshot generations older than its newest, and chunks a failed attempt left past that one's end. */
    private suspend fun dropSupersededSnapshots(homeId: String, store: String, writer: String, keys: List<String>) {
        val newest = database.mailboxQueries.selectLatestSnapshot(homeId).awaitAsOneOrNull()?.generation ?: return
        val newestChunks = client.getValue(store, SnapshotKeys.manifest(writer, newest))?.let { SnapshotManifest.decode(it)?.chunkCount }
        keys.mapNotNull { key -> SnapshotKeys.parse(key)?.takeIf { it.writer == writer }?.let { key to it } }.forEach { (key, snapshot) ->
            val superseded = snapshot.generation < newest ||
                (snapshot.generation == newest && snapshot.chunk != null && newestChunks != null && snapshot.chunk >= newestChunks)
            if (superseded) client.deleteValue(store, key)
        }
    }
}
