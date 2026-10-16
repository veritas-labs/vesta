package uk.veritaslabs.kits.spaces

/**
 * What the app does with its own documents when a Space appears, so the Space machinery need not know which documents
 * an app keeps (docs/plans/kits-extraction-implementation-plan.md, "Decisions for step 3").
 */
interface SpaceSeeding {
    /** This device created the Space: seed the documents whose seed must exist before any other device hears of it. */
    suspend fun seedCreated(spaceId: String)

    /** The Space arrived from another device (joined, or learned from the Space registry): store the unseeded placeholders. */
    suspend fun placeholdersForArrived(spaceId: String)
}
