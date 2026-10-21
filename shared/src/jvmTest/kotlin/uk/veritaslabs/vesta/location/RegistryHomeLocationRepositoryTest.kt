package uk.veritaslabs.vesta.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.automerge.SyncState
import uk.veritaslabs.kits.KitsInternalApi
import uk.veritaslabs.kits.spaces.SpaceRegistry
import uk.veritaslabs.kits.spaces.SpaceSeeding
import uk.veritaslabs.kits.spaces.StoredSpace
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase

@OptIn(KitsInternalApi::class)
class RegistryHomeLocationRepositoryTest {

    private val seeding = object : SpaceSeeding {
        override suspend fun seedCreated(spaceId: String) = Unit
        override suspend fun placeholdersForArrived(spaceId: String) = Unit
    }

    private suspend fun registryWithHome(): SpaceRegistry =
        SpaceRegistry(openInMemoryDatabase(), seeding).also { it.saved(StoredSpace("h1", "Home", "family", 1L)) }

    @Test
    fun aLocationRoundTrips() = runBlocking {
        // Arrange
        val repository = RegistryHomeLocationRepository(registryWithHome())

        // Act
        repository.setHome("h1", "GB", "loc-1", "2026-10")
        repository.setArea("h1", setOf("loc-2", "loc-3"))

        // Assert
        assertEquals(HomeLocation("GB", "loc-1", "2026-10", area = setOf("loc-2", "loc-3")), repository.read("h1"))
    }

    @Test
    fun aHomeWithNoLocationAndAnAbsentSpaceReadAsNull() = runBlocking {
        // Arrange
        val repository = RegistryHomeLocationRepository(registryWithHome())

        // Act
        val none = repository.read("h1")
        val absent = repository.read("nope")

        // Assert
        assertNull(none)
        assertNull(absent)
    }

    @Test
    fun anEntryWithOnlyNameAndKindOrPartOfALocationReadsAsNull() = runBlocking {
        // Arrange
        val registry = registryWithHome()
        val repository = RegistryHomeLocationRepository(registry)
        val older = repository.read("h1")
        registry.setAttributes("h1", mapOf("locality.country" to "GB"))

        // Act
        val partial = repository.read("h1")

        // Assert
        assertNull(older)
        assertNull(partial)
    }

    @Test
    fun settingTheHomeClearsTheCellAndTheArea() = runBlocking {
        // Arrange
        val registry = registryWithHome()
        val repository = RegistryHomeLocationRepository(registry)
        repository.setHome("h1", "GB", "loc-1", "2026-10")
        registry.setAttributes("h1", mapOf("locality.cell" to "8a1", "locality.cellSource" to "typed"))
        repository.setArea("h1", setOf("loc-2", "loc-3"))
        repository.setArea("h1", setOf("loc-2")) // leaves a "0" behind for loc-3

        // Act
        repository.setHome("h1", "FR", "loc-9", "2026-11")

        // Assert
        assertEquals(HomeLocation("FR", "loc-9", "2026-11"), repository.read("h1"))
        assertEquals(
            mapOf("locality.country" to "FR", "locality.home" to "loc-9", "locality.release" to "2026-11"),
            registry.attributes("h1"),
        )
    }

    @Test
    fun theAreaIsTickedAndUntickedAsATombstone() = runBlocking {
        // Arrange
        val registry = registryWithHome()
        val repository = RegistryHomeLocationRepository(registry)
        repository.setHome("h1", "GB", "loc-1", "2026-10")
        repository.setArea("h1", setOf("a", "b"))

        // Act
        repository.setArea("h1", setOf("a"))

        // Assert
        assertEquals(setOf("a"), repository.read("h1")?.area)
        assertEquals("1", registry.attributes("h1")["area.a"])
        assertEquals("0", registry.attributes("h1")["area.b"])
    }

    @Test
    fun anUnchangedAreaWritesNothing() = runBlocking {
        // Arrange
        val registry = registryWithHome()
        val repository = RegistryHomeLocationRepository(registry)
        repository.setHome("h1", "GB", "loc-1", "2026-10")
        repository.setArea("h1", setOf("a"))
        var announced = 0
        val collecting = launch(Dispatchers.Unconfined) { registry.changes.collect { announced++ } }

        // Act
        repository.setArea("h1", setOf("a"))
        repository.setArea("h1", setOf("a"))
        collecting.cancel()

        // Assert
        assertEquals(0, announced)
    }

    @Test
    fun clearRemovesTheLocationAndLeavesTheRest() = runBlocking {
        // Arrange
        val registry = registryWithHome()
        val repository = RegistryHomeLocationRepository(registry)
        repository.setHome("h1", "GB", "loc-1", "2026-10")
        repository.setArea("h1", setOf("a"))
        registry.setAttributes("h1", mapOf("other" to "kept"))

        // Act
        repository.clear("h1")

        // Assert
        assertNull(repository.read("h1"))
        assertEquals(mapOf("other" to "kept"), registry.attributes("h1"))
    }

    @Test
    fun theUnavailableRepositoryKnowsNothingAndStoresNothing() = runBlocking {
        // Arrange
        val repository = UnavailableHomeLocationRepository

        // Act
        repository.setHome("h1", "GB", "loc-1", "2026-10")

        // Assert
        assertNull(repository.read("h1"))
    }

    @Test
    fun aLocationAndTwoAreaTicksOnTwoPairedDevicesAllSurvive() = runBlocking {
        // Arrange
        val phone = SpaceRegistry(openInMemoryDatabase(), seeding)
        val tablet = SpaceRegistry(openInMemoryDatabase(), seeding)
        phone.saved(StoredSpace("h1", "Home", "family", 1L))
        val phoneState = SyncState()
        val tabletState = SyncState()
        suspend fun sync() {
            repeat(6) {
                phone.generate(phoneState)?.let { tablet.receive(tabletState, it) }
                tablet.generate(tabletState)?.let { phone.receive(phoneState, it) }
            }
        }
        sync()
        val onPhone = RegistryHomeLocationRepository(phone)
        val onTablet = RegistryHomeLocationRepository(tablet)

        // Act
        onPhone.setHome("h1", "GB", "loc-1", "2026-10")
        sync()
        val seenOnTablet = onTablet.read("h1")
        onPhone.setArea("h1", setOf("from-phone"))
        onTablet.setArea("h1", setOf("from-tablet"))
        sync()

        // Assert
        assertEquals(HomeLocation("GB", "loc-1", "2026-10"), seenOnTablet)
        assertEquals(setOf("from-phone", "from-tablet"), onPhone.read("h1")?.area)
        assertEquals(setOf("from-phone", "from-tablet"), onTablet.read("h1")?.area)
    }
}
