package uk.veritaslabs.vesta.datasync

/**
 * What the app does with its own documents when a Home appears, so the Home machinery need not know which documents
 * an app keeps (docs/plans/kits-extraction-implementation-plan.md, "Decisions for step 3").
 */
interface HomeSeeding {
    /** This device created the Home: seed the documents whose seed must exist before any other device hears of it. */
    suspend fun seedCreated(homeId: String)

    /** The Home arrived from another device (joined, or learned from the Home registry): store the unseeded placeholders. */
    suspend fun placeholdersForArrived(homeId: String)
}
