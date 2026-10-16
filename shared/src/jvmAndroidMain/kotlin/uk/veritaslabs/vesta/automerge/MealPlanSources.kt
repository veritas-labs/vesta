package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import kotlinx.coroutines.flow.SharedFlow
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.docs.SpaceDocumentStore

/** One shared [MealPlanDocumentSource] per Home (see [PantrySources] for why there must be exactly one). */
class MealPlanSources(database: KitsDatabase) {

    private val store = SpaceDocumentStore(
        database, "mealplan", { Clock.System.now().toEpochMilliseconds() },
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> MealPlanDocumentSource(document, persist, changed) },
    )

    /** The id of a Home each time its meal plan is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = store.localChanges

    /** [homeId]'s meal plan source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): MealPlanDocumentSource? = store.forSpace(homeId)
}
