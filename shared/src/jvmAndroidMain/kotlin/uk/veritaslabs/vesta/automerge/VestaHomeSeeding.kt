package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import uk.veritaslabs.vesta.datasync.HomeSeeding
import uk.veritaslabs.kits.store.KitsDatabase

/**
 * Vesta's [HomeSeeding]: a Home this device creates gets its Pantry seeded (before any other device hears of the
 * Home, so the seed is the only one); a Home that arrives gets empty Pantry and shopping-list placeholders that fill
 * from the creator's documents (ADR-008, ADR-013).
 */
class VestaHomeSeeding(
    private val sources: VestaHomeSources,
    private val database: KitsDatabase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : HomeSeeding {

    override suspend fun seedCreated(homeId: String) {
        sources.pantries.forHome(homeId)
    }

    override suspend fun placeholdersForArrived(homeId: String) {
        storeEmptyPantry(database, homeId, nowMillis)
        storeEmptyShoppingList(database, homeId, nowMillis)
    }
}
