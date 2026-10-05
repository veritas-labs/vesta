package uk.veritaslabs.vesta.automerge

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

    /** [homeId]'s shopping-list source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): ShoppingListDocumentSource? = lock.withLock {
        val pantry = pantrySources.forHome(homeId)
        if (pantry == null) {
            sources.remove(homeId)
            return@withLock null
        }
        sources[homeId] ?: openShoppingListDocumentSource(database, homeId, pantry).also { sources[homeId] = it }
    }
}
