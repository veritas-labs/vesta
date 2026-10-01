package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase

/**
 * One shared [PantryDocumentSource] per Home. A [PantryDocumentSource] must be the only reader and
 * writer of its Automerge document, so the UI and anything else that edits a Pantry (e.g. agent
 * commands, device sync) have to go through the same instance — get it here.
 */
class PantrySources(private val database: VestaDatabase) {

    private val lock = Mutex()
    private val sources = mutableMapOf<String, PantryDocumentSource>()
    private val _localChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Home each time its Pantry is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = _localChanges.asSharedFlow()

    /** [homeId]'s Pantry source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): PantryDocumentSource? = lock.withLock {
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) {
            sources.remove(homeId)
            return@withLock null
        }
        sources[homeId] ?: openPantryDocumentSource(database, homeId, onLocalChange = { _localChanges.tryEmit(homeId) })
            .also { sources[homeId] = it }
    }
}
