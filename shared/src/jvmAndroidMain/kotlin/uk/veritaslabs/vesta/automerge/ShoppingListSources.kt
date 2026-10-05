package uk.veritaslabs.vesta.automerge

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase

/**
 * One shared [ShoppingListDocumentSource] per Home, built on that Home's shared [PantryDocumentSource] from
 * [pantrySources] (see [PantrySources] for why there must be exactly one of each).
 */
class ShoppingListSources(private val database: VestaDatabase, private val pantrySources: PantrySources) {

    private val lock = Mutex()
    private val sources = mutableMapOf<String, ShoppingListDocumentSource>()
    private val _localChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Home each time its list is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = _localChanges.asSharedFlow()

    /** [homeId]'s shopping-list source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): ShoppingListDocumentSource? = lock.withLock {
        val pantry = pantrySources.forHome(homeId)
        if (pantry == null) {
            sources.remove(homeId)
            return@withLock null
        }
        sources[homeId] ?: openShoppingListDocumentSource(database, homeId, pantry, onLocalChange = { _localChanges.tryEmit(homeId) })
            .also { sources[homeId] = it }
    }
}
