package uk.veritaslabs.vesta.automerge

import uk.veritaslabs.vesta.datasync.HomeRosters
import uk.veritaslabs.vesta.datasync.SyncModules
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.docs.HomeDocumentRegistry
import uk.veritaslabs.vesta.docs.HomeDocumentType
import uk.veritaslabs.vesta.docs.Scope

/**
 * Vesta's own list of Home document types, in one place: builds the per-feature sources and describes each document
 * type for the [HomeDocumentRegistry]. The feature strings are the ids' suffixes (ADR-006) and must never change.
 *
 * Registration order is the mailbox's current order (roster first), with the private types after the shared ones.
 *
 * @param publishRosters runs after a live roster sync message has been applied and projected, so this device's own member
 * and devices are written back into the roster.
 */
internal class VestaHomeDocuments(
    database: VestaDatabase,
    val rosters: HomeRosters,
    publishRosters: suspend () -> Unit,
    val pantries: PantrySources = PantrySources(database),
    val shoppingLists: ShoppingListSources = ShoppingListSources(database, pantries),
    val cookbooks: CookbookSources = CookbookSources(database),
    val merchants: MerchantsSources = MerchantsSources(database),
    val mealPlans: MealPlanSources = MealPlanSources(database),
    val calendars: CalendarSources = CalendarSources(database),
    val tasks: TaskSources = TaskSources(database),
) {
    val registry = HomeDocumentRegistry(
        listOf(
            HomeDocumentType(
                "roster", SyncModules.Members, Scope.Shared, rosters::homeDocument, rosters.changes,
                afterReceive = { homeId -> rosters.project(homeId) },
                afterSync = { publishRosters() },
            ),
            HomeDocumentType("pantry", SyncModules.Pantry, Scope.Shared, pantries::forHome, pantries.localChanges),
            HomeDocumentType("shopping-list", SyncModules.Shopping, Scope.Shared, shoppingLists::forHome, shoppingLists.localChanges),
            HomeDocumentType("cookbook", SyncModules.Cookbook, Scope.Shared, cookbooks::forHome, cookbooks.localChanges),
            HomeDocumentType("merchants", SyncModules.Merchants, Scope.Shared, merchants::forHome, merchants.localChanges),
            HomeDocumentType("mealplan", SyncModules.MealPlan, Scope.Shared, mealPlans::forHome, mealPlans.localChanges),
            HomeDocumentType("calendar", SyncModules.Calendar, Scope.Shared, calendars::forHome, calendars.localChanges),
            HomeDocumentType("tasks", SyncModules.Tasks, Scope.Shared, tasks::forHome, tasks.localChanges),
            HomeDocumentType("private-calendar", SyncModules.Calendar, Scope.Private, calendars::privateForHome, calendars.privateChanges),
            HomeDocumentType("private-tasks", SyncModules.Tasks, Scope.Private, tasks::privateForHome, tasks.privateChanges),
        ),
    )
}
