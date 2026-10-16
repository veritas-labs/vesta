package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import kotlinx.coroutines.flow.SharedFlow
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.docs.HomeDocumentStore

/**
 * One shared [PantryDocumentSource] per Home. A [PantryDocumentSource] must be the only reader and
 * writer of its Automerge document, so the UI and anything else that edits a Pantry (e.g. agent
 * commands, device sync) have to go through the same instance — get it here.
 */
class PantrySources(database: KitsDatabase) {

    private val store = HomeDocumentStore(
        database, "pantry", { Clock.System.now().toEpochMilliseconds() },
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed ->
            migratePantryCategories(document)
            PantryDocumentSource(document, persist, changed)
        },
        newDocument = { newPantryDocument() },
    )

    /** The id of a Home each time its Pantry is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = store.localChanges

    /** [homeId]'s Pantry source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): PantryDocumentSource? = store.forHome(homeId)
}
