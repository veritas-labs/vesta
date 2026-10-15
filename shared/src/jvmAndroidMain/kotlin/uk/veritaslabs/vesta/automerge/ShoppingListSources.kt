package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import kotlinx.coroutines.flow.SharedFlow
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.docs.HomeDocumentStore

/**
 * One shared [ShoppingListDocumentSource] per Home, built on that Home's shared [PantryDocumentSource] from
 * [pantrySources] (see [PantrySources] for why there must be exactly one of each).
 */
class ShoppingListSources(database: VestaDatabase, pantrySources: PantrySources) {

    // [create] returns null without a Pantry (no such Home), so the store caches nothing and evicts any earlier entry.
    private val store = HomeDocumentStore(
        database, "shopping-list", { Clock.System.now().toEpochMilliseconds() },
        refreshNew = { it.refresh() },
        create = { homeId, document, persist, changed ->
            pantrySources.forHome(homeId)?.let { ShoppingListDocumentSource(document, it, persist, changed) }
        },
        newDocument = { newShoppingListDocument() },
    )

    /** The id of a Home each time its list is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = store.localChanges

    /** [homeId]'s shopping-list source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): ShoppingListDocumentSource? = store.forHome(homeId)
}
