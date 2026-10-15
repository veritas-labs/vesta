package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import kotlinx.coroutines.flow.SharedFlow
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.docs.HomeDocumentStore

/** One shared [MealPlanDocumentSource] per Home (see [PantrySources] for why there must be exactly one). */
class MealPlanSources(database: VestaDatabase) {

    private val store = HomeDocumentStore(
        database, "mealplan", { Clock.System.now().toEpochMilliseconds() },
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> MealPlanDocumentSource(document, persist, changed) },
    )

    /** The id of a Home each time its meal plan is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = store.localChanges

    /** [homeId]'s meal plan source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): MealPlanDocumentSource? = store.forHome(homeId)
}
