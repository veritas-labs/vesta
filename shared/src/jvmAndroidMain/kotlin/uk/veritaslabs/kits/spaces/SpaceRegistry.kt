@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.kits.spaces

import uk.veritaslabs.kits.docs.SyncableDocument

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.kits.store.KitsDatabase
/**
 * The user's list of Spaces as an Automerge document that syncs between their devices
 * (docs/adr/ADR-013-device-data-sync.md). The `Home` table stays what the app reads; this mirrors it in both
 * directions: Space changes made here are written into the document ([saved], [deleted]), and changes that arrive
 * from another device are copied into the table by [project].
 */
internal class SpaceRegistry(
    private val database: KitsDatabase,
    private val seeding: SpaceSeeding,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SpaceChangeListener, SyncableDocument {

    private val lock = Mutex()
    private var document: Document? = null
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after each change made on this device, so it can be pushed to the other devices. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    override suspend fun saved(space: StoredSpace) {
        // Seed the Pantry document before any other device hears of the Space, so the seed is the only one.
        seeding.seedCreated(space.id)
        if (lock.withLock { edit { putRegistrySpace(it, space) } }) _changes.tryEmit(Unit)
    }

    override suspend fun deleted(id: String) {
        if (lock.withLock { edit { deleteRegistrySpace(it, id) } }) _changes.tryEmit(Unit)
    }

    /** Adds every Space already on this device to the registry, e.g. Spaces created before any device was paired. */
    suspend fun importLocalSpaces() {
        database.homeQueries.selectAll().awaitAsList().forEach { row ->
            saved(StoredSpace(row.id, row.name, row.shape, row.createdAt)) // `Home.shape` is the kind
        }
    }

    override suspend fun generate(state: SyncState): ByteArray? = lock.withLock { open().generateSyncMessage(state).orElse(null) }

    override suspend fun receive(state: SyncState, message: ByteArray) = lock.withLock {
        val doc = open()
        val before = doc.heads
        doc.receiveSyncMessage(state, message)
        if (!doc.heads.contentEquals(before)) persist(doc)
    }

    /**
     * Brings the `Home` table in line with the registry: creates Spaces other devices made, applies their edits and
     * removes Spaces they deleted. Returns the ids of the Spaces it created.
     */
    suspend fun project(): Set<String> = lock.withLock {
        val created = mutableSetOf<String>()
        open().readSpaceRegistry().forEach { entry ->
            val row = database.homeQueries.selectById(entry.id).awaitAsOneOrNull()
            when {
                entry.deleted -> if (row != null) database.homeQueries.deleteById(entry.id)
                // An entry whose fields haven't all arrived yet is skipped; a later message completes it.
                entry.name.isEmpty() || entry.kind.isEmpty() -> Unit
                row == null -> {
                    database.homeQueries.insert(entry.id, entry.name, entry.kind, entry.createdAt)
                    // Created elsewhere, so that device seeds the Pantry; ours starts empty and fills from it.
                    seeding.placeholdersForArrived(entry.id)
                    created += entry.id
                }
                row.name != entry.name || row.shape != entry.kind ->
                    database.homeQueries.updateHome(entry.name, entry.kind, entry.id)
            }
        }
        created
    }

    private suspend fun edit(block: (Document) -> Boolean): Boolean {
        val doc = open()
        return block(doc).also { changed -> if (changed) persist(doc) }
    }

    private suspend fun open(): Document = document ?: run {
        val stored = database.userDocumentQueries.selectById(SpaceRegistryDocumentId).awaitAsOneOrNull()
        (stored?.let { Document.load(it.bytes) } ?: Document()).also { document = it }
    }

    private suspend fun persist(doc: Document) {
        database.userDocumentQueries.upsert(SpaceRegistryDocumentId, doc.save(), nowMillis())
    }
}
