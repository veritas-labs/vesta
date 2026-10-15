package uk.veritaslabs.vesta.datasync

import org.automerge.SyncState
import uk.veritaslabs.vesta.automerge.CalendarSources
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.vesta.automerge.MealPlanSources
import uk.veritaslabs.vesta.automerge.MerchantsSources
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.TaskSources
import uk.veritaslabs.vesta.automerge.calendarDocumentId
import uk.veritaslabs.vesta.automerge.cookbookDocumentId
import uk.veritaslabs.vesta.automerge.mealPlanDocumentId
import uk.veritaslabs.vesta.automerge.merchantsDocumentId
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.privateCalendarDocumentId
import uk.veritaslabs.vesta.automerge.privateTasksDocumentId
import uk.veritaslabs.vesta.automerge.rosterDocumentId
import uk.veritaslabs.vesta.automerge.shoppingListDocumentId
import uk.veritaslabs.vesta.automerge.tasksDocumentId

/**
 * Asks a Home's document whether a peer has acknowledged all of it (ADR-033, "Unsent-change tracking"): finds the document that
 * [DeviceDataSyncService] keeps under an id, the way its `pump` does, and asks it `isInSync` for that peer's sync state. The
 * answer is true only once the peer has replied with its heads, so a message sent to a device that never answered is not one.
 */
internal class SyncAcknowledgement(
    private val rosters: HomeRosters,
    private val pantries: PantrySources,
    private val shoppingLists: ShoppingListSources,
    private val cookbooks: CookbookSources,
    private val merchants: MerchantsSources,
    private val mealPlans: MealPlanSources,
    private val calendars: CalendarSources,
    private val tasks: TaskSources,
) {
    /** True when the peer whose progress is [state] has everything of [documentId] (a document of [homeId]) this device has; false for a document this device doesn't have. */
    suspend fun isInSync(homeId: String, documentId: String, state: SyncState): Boolean = when (documentId) {
        rosterDocumentId(homeId) -> rosters.isInSync(homeId, state)
        pantryDocumentId(homeId) -> pantries.forHome(homeId)?.isInSync(state)
        shoppingListDocumentId(homeId) -> shoppingLists.forHome(homeId)?.isInSync(state)
        cookbookDocumentId(homeId) -> cookbooks.forHome(homeId)?.isInSync(state)
        merchantsDocumentId(homeId) -> merchants.forHome(homeId)?.isInSync(state)
        mealPlanDocumentId(homeId) -> mealPlans.forHome(homeId)?.isInSync(state)
        calendarDocumentId(homeId) -> calendars.forHome(homeId)?.isInSync(state)
        privateCalendarDocumentId(homeId) -> calendars.privateForHome(homeId)?.isInSync(state)
        tasksDocumentId(homeId) -> tasks.forHome(homeId)?.isInSync(state)
        privateTasksDocumentId(homeId) -> tasks.privateForHome(homeId)?.isInSync(state)
        else -> null
    } ?: false
}
