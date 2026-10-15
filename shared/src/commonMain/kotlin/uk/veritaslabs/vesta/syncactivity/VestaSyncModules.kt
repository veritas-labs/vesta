package uk.veritaslabs.vesta.syncactivity

/**
 * The module ids of Vesta's own features, as stored in `SyncEvent.module` and named by their document types. The strings
 * are persisted and must never change (`DocumentIdsTest`). The sync machinery's own ids (members, mailbox) are not here.
 */
object VestaSyncModules {
    const val Pantry = "pantry"
    const val Shopping = "shopping"
    const val Cookbook = "cookbook"
    const val MealPlan = "mealplan"
    const val Calendar = "calendar"
    const val Tasks = "tasks"
    const val Merchants = "merchants"
}
