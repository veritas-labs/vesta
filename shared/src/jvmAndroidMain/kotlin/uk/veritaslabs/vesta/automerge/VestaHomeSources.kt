package uk.veritaslabs.vesta.automerge

import uk.veritaslabs.vesta.syncactivity.VestaSyncModules
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.docs.SpaceDocumentType
import uk.veritaslabs.kits.docs.Scope

/**
 * The per-feature sources of one database: the single object the apps and the sync engine share, so a screen, an agent
 * command and the sync all edit the same Automerge documents. Public because the apps build it.
 */
class VestaHomeSources(
    database: KitsDatabase,
    val pantries: PantrySources = PantrySources(database),
    val shoppingLists: ShoppingListSources = ShoppingListSources(database, pantries),
    val cookbooks: CookbookSources = CookbookSources(database),
    val merchants: MerchantsSources = MerchantsSources(database),
    val mealPlans: MealPlanSources = MealPlanSources(database),
    val calendars: CalendarSources = CalendarSources(database),
    val tasks: TaskSources = TaskSources(database),
) {
    /**
     * Vesta's nine Home document types, in the engine's registration order after the roster it registers itself, with the
     * private types after the shared ones (ADR-035). The feature strings are the ids' suffixes (ADR-006) and must never change.
     */
    fun documentTypes(): List<SpaceDocumentType> = listOf(
        SpaceDocumentType("pantry", VestaSyncModules.Pantry, Scope.Shared, pantries::forHome, pantries.localChanges),
        SpaceDocumentType("shopping-list", VestaSyncModules.Shopping, Scope.Shared, shoppingLists::forHome, shoppingLists.localChanges),
        SpaceDocumentType("cookbook", VestaSyncModules.Cookbook, Scope.Shared, cookbooks::forHome, cookbooks.localChanges),
        SpaceDocumentType("merchants", VestaSyncModules.Merchants, Scope.Shared, merchants::forHome, merchants.localChanges),
        SpaceDocumentType("mealplan", VestaSyncModules.MealPlan, Scope.Shared, mealPlans::forHome, mealPlans.localChanges),
        SpaceDocumentType("calendar", VestaSyncModules.Calendar, Scope.Shared, calendars::forHome, calendars.localChanges),
        SpaceDocumentType("tasks", VestaSyncModules.Tasks, Scope.Shared, tasks::forHome, tasks.localChanges),
        SpaceDocumentType("private-calendar", VestaSyncModules.Calendar, Scope.Private, calendars::privateForHome, calendars.privateChanges),
        SpaceDocumentType("private-tasks", VestaSyncModules.Tasks, Scope.Private, tasks::privateForHome, tasks.privateChanges),
    )
}
