package uk.veritaslabs.vesta.datasync

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
import uk.veritaslabs.vesta.automerge.HomeRegistryDocumentId
import uk.veritaslabs.vesta.automerge.deleteRegistryHome
import uk.veritaslabs.vesta.automerge.putRegistryHome
import uk.veritaslabs.vesta.automerge.readHomeRegistry
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.home.StoredHome

/**
 * The user's list of Homes as an Automerge document that syncs between their devices
 * (docs/adr/ADR-013-device-data-sync.md). The `Home` table stays what the app reads; this mirrors it in both
 * directions: Home changes made here are written into the document ([saved], [deleted]), and changes that arrive
 * from another device are copied into the table by [project].
 */
internal class HomeRegistry(
    private val database: KitsDatabase,
    private val seeding: HomeSeeding,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : HomeChangeListener, SyncableDocument {

    private val lock = Mutex()
    private var document: Document? = null
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after each change made on this device, so it can be pushed to the other devices. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    override suspend fun saved(home: StoredHome) {
        // Seed the Pantry document before any other device hears of the Home, so the seed is the only one.
        seeding.seedCreated(home.id)
        if (lock.withLock { edit { putRegistryHome(it, home) } }) _changes.tryEmit(Unit)
    }

    override suspend fun deleted(id: String) {
        if (lock.withLock { edit { deleteRegistryHome(it, id) } }) _changes.tryEmit(Unit)
    }

    /** Adds every Home already on this device to the registry, e.g. Homes created before any device was paired. */
    suspend fun importLocalHomes() {
        database.homeQueries.selectAll().awaitAsList().forEach { row ->
            saved(StoredHome(row.id, row.name, row.shape, row.createdAt))
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
     * Brings the `Home` table in line with the registry: creates Homes other devices made, applies their edits and
     * removes Homes they deleted. Returns the ids of the Homes it created.
     */
    suspend fun project(): Set<String> = lock.withLock {
        val created = mutableSetOf<String>()
        open().readHomeRegistry().forEach { entry ->
            val row = database.homeQueries.selectById(entry.id).awaitAsOneOrNull()
            when {
                entry.deleted -> if (row != null) database.homeQueries.deleteById(entry.id)
                // An entry whose fields haven't all arrived yet is skipped; a later message completes it.
                entry.name.isEmpty() || entry.shape.isEmpty() -> Unit
                row == null -> {
                    database.homeQueries.insert(entry.id, entry.name, entry.shape, entry.createdAt)
                    // Created elsewhere, so that device seeds the Pantry; ours starts empty and fills from it.
                    seeding.placeholdersForArrived(entry.id)
                    created += entry.id
                }
                row.name != entry.name || row.shape != entry.shape ->
                    database.homeQueries.updateHome(entry.name, entry.shape, entry.id)
            }
        }
        created
    }

    private suspend fun edit(block: (Document) -> Boolean): Boolean {
        val doc = open()
        return block(doc).also { changed -> if (changed) persist(doc) }
    }

    private suspend fun open(): Document = document ?: run {
        val stored = database.userDocumentQueries.selectById(HomeRegistryDocumentId).awaitAsOneOrNull()
        (stored?.let { Document.load(it.bytes) } ?: Document()).also { document = it }
    }

    private suspend fun persist(doc: Document) {
        database.userDocumentQueries.upsert(HomeRegistryDocumentId, doc.save(), nowMillis())
    }
}
