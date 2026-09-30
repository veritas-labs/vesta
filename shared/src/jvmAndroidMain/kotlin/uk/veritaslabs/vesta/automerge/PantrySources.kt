package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase

/**
 * One shared [PantryDocumentSource] per Home. A [PantryDocumentSource] must be the only reader and
 * writer of its Automerge document, so the UI and anything else that edits a Pantry (e.g. agent
 * commands) have to go through the same instance — get it here. Sources are never evicted.
 */
class PantrySources(private val database: VestaDatabase) {

    private val lock = Mutex()
    private val sources = mutableMapOf<String, PantryDocumentSource>()

    /** [homeId]'s Pantry source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): PantryDocumentSource? = lock.withLock {
        sources[homeId] ?: run {
            database.homeQueries.selectById(homeId).awaitAsOneOrNull() ?: return@withLock null
            openPantryDocumentSource(database, homeId).also { sources[homeId] = it }
        }
    }
}
