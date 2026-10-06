package uk.veritaslabs.vesta.mailbox

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.x0x.X0xClient

/**
 * Puts what this device has changed in a Home's documents into the Home's `mailbox` store, so a device that is offline
 * now can pick it up later from any replica (docs/adr/ADR-020-syncing-without-overlapping-online-time.md).
 *
 * [deposit] writes the changes made since its last deposit, as entries under `<this agent>/<sequence>`. Nothing new
 * writes nothing. The deposit is only remembered once every entry is in the store, so a failure repeats the same range
 * on the next call, over the same entry numbers: readers go through a writer's entries without gaps, so a number must
 * never be left unused. Each attempt carries its own deposit number, so a reader doesn't mix the parts of two.
 */
internal class MailboxWriter(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val documents: MailboxDocuments,
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
            ?: client.listStoreKeys(store).mapNotNull { MailboxEntry.parseKey(it.key) }
                .filter { it.first == writer }.maxOfOrNull { it.second + 1 } ?: 0L
}
