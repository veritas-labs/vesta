package uk.veritaslabs.vesta.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.MemberListItem
import uk.veritaslabs.vesta.ui.screens.MemberRole
import uk.veritaslabs.vesta.ui.screens.HomeDetails
import uk.veritaslabs.vesta.ui.screens.HomeListItem
import uk.veritaslabs.vesta.ui.screens.HomeShape

class HomeRepositoryTest {

    @Test
    fun emitsEmptyWhenThereAreNoHomes() = runTest {
        // Arrange
        val repository = HomeRepository(openInMemoryDatabase())

        // Act
        val homes = repository.homes.first()

        // Assert
        assertEquals(emptyList(), homes)
    }

    @Test
    fun emitsStoredHomes() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.homeQueries.insert("the-johnsons", "The Johnsons", "family", 1L)
        val repository = HomeRepository(db)

        // Act
        val homes = repository.homes.first()

        // Assert
        assertEquals(listOf(HomeListItem(id = "the-johnsons", name = "The Johnsons")), homes)
    }

    @Test
    fun reEmitsWhenAHomeIsInserted() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = HomeRepository(db)
        val before = repository.homes.first()

        // Act
        db.homeQueries.insert("flat-4b", "Flat 4B", "flatshare", 1L)
        val after = repository.homes.first()

        // Assert
        assertEquals(0, before.size)
        assertEquals(listOf("Flat 4B"), after.map { it.name })
    }

    @Test
    fun createStoresTheHomeAndReturnsItsId() = runTest {
        // Arrange
        val repository = HomeRepository(openInMemoryDatabase(), nowMillis = { 42L })

        // Act
        val id = repository.create("Flat 4B", HomeShape.Flatshare)

        // Assert
        assertEquals(HomeDetails(id = id, name = "Flat 4B", shape = HomeShape.Flatshare), repository.get(id))
    }

    @Test
    fun createMakesTheCurrentProfileTheHomesAdminMember() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark's laptop" })
        val repository = HomeRepository(db, profiles = profiles, nowMillis = { 42L })

        // Act
        val id = repository.create("Flat 4B", HomeShape.Flatshare)

        // Assert
        val me = profiles.current()
        val members = MemberRepository(db, profiles).members(id).first()
        assertEquals(listOf(MemberListItem(me.id, "Mark's laptop", MemberRole.Admin, isYou = true)), members)
    }

    @Test
    fun getReturnsNullForAnUnknownHome() = runTest {
        // Arrange
        val repository = HomeRepository(openInMemoryDatabase())

        // Act
        val home = repository.get("missing")

        // Assert
        assertNull(home)
    }

    @Test
    fun updateChangesTheNameAndShape() = runTest {
        // Arrange
        val repository = HomeRepository(openInMemoryDatabase())
        val id = repository.create("Flat 4B", HomeShape.Flatshare)

        // Act
        repository.update(id, "Family Home", HomeShape.Family)

        // Assert
        assertEquals(HomeDetails(id = id, name = "Family Home", shape = HomeShape.Family), repository.get(id))
    }

    @Test
    fun deleteRemovesTheHome() = runTest {
        // Arrange
        val repository = HomeRepository(openInMemoryDatabase())
        val id = repository.create("Flat 4B", HomeShape.Flatshare)

        // Act
        repository.delete(id)

        // Assert
        assertNull(repository.get(id))
        assertEquals(emptyList(), repository.homes.first())
    }
}
