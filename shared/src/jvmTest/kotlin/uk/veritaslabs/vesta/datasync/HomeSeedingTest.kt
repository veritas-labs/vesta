@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.automerge.SyncState
import uk.veritaslabs.kits.spaces.SpaceRegistryDocumentId
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.kits.spaces.StoredSpace

import uk.veritaslabs.kits.spaces.SpaceRegistry
import uk.veritaslabs.kits.spaces.SpaceSeeding
/** The seeding hook (docs/plans/kits-extraction-implementation-plan.md, WP4): when it is called, and in what order. */
class HomeSeedingTest {

    private class Recording(private val database: KitsDatabase) : SpaceSeeding {
        val calls = mutableListOf<String>()

        override suspend fun seedCreated(homeId: String) {
            val registryWritten = database.userDocumentQueries.selectById(SpaceRegistryDocumentId).awaitAsOneOrNull() != null
            calls += "created:$homeId:registryWritten=$registryWritten"
        }

        override suspend fun placeholdersForArrived(homeId: String) {
            val rowThere = database.homeQueries.selectById(homeId).awaitAsOneOrNull() != null
            calls += "arrived:$homeId:homeRowThere=$rowThere"
        }
    }

    @Test
    fun aHomeCreatedHereIsSeededOnceBeforeTheRegistryRecordsIt() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        val seeding = Recording(database)
        val registry = SpaceRegistry(database, seeding)

        // Act
        registry.saved(StoredSpace("home-1", "Maple Street", "family", 1L))

        // Assert
        assertEquals(listOf("created:home-1:registryWritten=false"), seeding.calls)
        assertEquals(true, database.userDocumentQueries.selectById(SpaceRegistryDocumentId).awaitAsOneOrNull() != null)
    }

    @Test
    fun aHomeLearnedFromTheRegistryGetsPlaceholdersOnceAfterItsRowIsInserted() = runBlocking {
        // Arrange
        val source = SpaceRegistry(openInMemoryDatabase(), Recording(openInMemoryDatabase()))
        source.saved(StoredSpace("home-1", "Maple Street", "family", 1L))
        val database = openInMemoryDatabase()
        val seeding = Recording(database)
        val registry = SpaceRegistry(database, seeding)
        val fromSource = SyncState()
        val fromRegistry = SyncState()
        repeat(10) {
            source.generate(fromSource)?.let { registry.receive(fromRegistry, it) }
            registry.generate(fromRegistry)?.let { source.receive(fromSource, it) }
        }

        // Act
        registry.project()
        registry.project()

        // Assert
        assertEquals(listOf("arrived:home-1:homeRowThere=true"), seeding.calls)
    }
}
