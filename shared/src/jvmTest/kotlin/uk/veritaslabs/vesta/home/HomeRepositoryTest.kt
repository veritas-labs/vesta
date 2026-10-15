package uk.veritaslabs.vesta.home

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.home.MemberListItem
import uk.veritaslabs.vesta.home.MemberRole
import uk.veritaslabs.vesta.home.HomeDetails
import uk.veritaslabs.vesta.home.HomeListItem
import uk.veritaslabs.vesta.ui.screens.HomeShape

class HomeRepositoryTest {

    @Test
    fun emitsEmptyWhenThereAreNoHomes() = runTest {
        // Arrange
        val repository = homeRepository(openInMemoryDatabase())

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
        val repository = homeRepository(db)

        // Act
        val homes = repository.homes.first()

        // Assert
        assertEquals(listOf(HomeListItem(id = "the-johnsons", name = "The Johnsons")), homes)
    }

    @Test
    fun reEmitsWhenAHomeIsInserted() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = homeRepository(db)
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
        val repository = homeRepository(openInMemoryDatabase(), nowMillis = { 42L })

        // Act
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)

        // Assert
        assertEquals(HomeDetails(id = id, name = "Flat 4B", shape = HomeShape.Flatshare.storageValue), repository.get(id))
    }

    @Test
    fun createMakesTheCurrentProfileTheHomesAdminMember() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark's laptop" })
        val repository = HomeRepository(db, profiles = profiles, nowMillis = { 42L })

        // Act
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)

        // Assert
        val me = profiles.current()
        val members = MemberRepository(db, profiles).members(id).first()
        assertEquals(listOf(MemberListItem(me.id, "Mark's laptop", MemberRole.Admin, isYou = true)), members)
    }

    @Test
    fun getReturnsNullForAnUnknownHome() = runTest {
        // Arrange
        val repository = homeRepository(openInMemoryDatabase())

        // Act
        val home = repository.get("missing")

        // Assert
        assertNull(home)
    }

    @Test
    fun updateChangesTheNameAndShape() = runTest {
        // Arrange
        val repository = homeRepository(openInMemoryDatabase())
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)

        // Act
        repository.update(id, "Family Home", HomeShape.Family.storageValue)

        // Assert
        assertEquals(HomeDetails(id = id, name = "Family Home", shape = HomeShape.Family.storageValue), repository.get(id))
    }

    @Test
    fun deleteRemovesTheHome() = runTest {
        // Arrange
        val repository = homeRepository(openInMemoryDatabase())
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)

        // Act
        repository.delete(id)

        // Assert
        assertNull(repository.get(id))
        assertEquals(emptyList(), repository.homes.first())
    }

    @Test
    fun leavingAsTheOnlyMemberEndsTheHome() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = HomeRepository(db, profiles = ProfileRepository(db, deviceName = { "Mark" }))
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)

        // Act
        val result = repository.leave(id)

        // Assert
        assertEquals(LeaveResult.Left, result)
        assertNull(repository.get(id))
    }

    @Test
    fun theOnlyAdminOfAHomeWithOtherMembersCannotLeave() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = HomeRepository(db, profiles = ProfileRepository(db, deviceName = { "Mark" }))
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)
        db.homeMemberQueries.insert(id, "sam", "Sam", null, "member", 2L)

        // Act
        val result = repository.leave(id)

        // Assert
        assertEquals(LeaveResult.OnlyAdmin, result)
        assertEquals("Flat 4B", repository.get(id)?.name)
    }

    @Test
    fun anAdminCanLeaveWhenAnotherMemberIsAlsoAnAdmin() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = HomeRepository(db, profiles = ProfileRepository(db, deviceName = { "Mark" }))
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)
        db.homeMemberQueries.insert(id, "sam", "Sam", null, "admin", 2L)

        // Act
        val result = repository.leave(id)

        // Assert
        assertEquals(LeaveResult.Left, result)
        assertNull(repository.get(id))
    }

    @Test
    fun anOrdinaryMemberCanLeave() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        val repository = HomeRepository(db, profiles = profiles)
        val me = profiles.current().id
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeMemberQueries.insert("home-1", "sam", "Sam", null, "admin", 1L)
        db.homeMemberQueries.insert("home-1", me, "Mark", null, "member", 2L)

        // Act
        val result = repository.leave("home-1")

        // Assert
        assertEquals(LeaveResult.Left, result)
        assertNull(repository.get("home-1"))
    }

    @Test
    fun leavingTellsTheOtherMembersBeforeTheHomeIsDeleted() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        val told = mutableListOf<Triple<String, String, Boolean>>()
        val listener = object : RosterChangeListener {
            override suspend fun roleChanged(homeId: String, memberId: String, role: String) = Unit
            override suspend fun memberRemoved(homeId: String, memberId: String) = Unit
            override suspend fun memberLeft(homeId: String, memberId: String) {
                told += Triple(homeId, memberId, db.homeQueries.selectById(homeId).awaitAsOneOrNull() != null)
            }
        }
        val repository = HomeRepository(db, profiles = profiles, rosterChanges = listener)
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)

        // Act
        repository.leave(id)

        // Assert
        assertEquals(listOf(Triple(id, profiles.current().id, true)), told)
        assertNull(repository.get(id))
    }

    @Test
    fun beingRefusedLeavingTellsNoOne() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        var told = 0
        val listener = object : RosterChangeListener {
            override suspend fun roleChanged(homeId: String, memberId: String, role: String) = Unit
            override suspend fun memberRemoved(homeId: String, memberId: String) = Unit
            override suspend fun memberLeft(homeId: String, memberId: String) { told++ }
        }
        val repository = HomeRepository(db, profiles = profiles, rosterChanges = listener)
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)
        db.homeMemberQueries.insert(id, "sam", "Sam", null, "member", 2L)

        // Act
        val result = repository.leave(id)

        // Assert
        assertEquals(LeaveResult.OnlyAdmin, result)
        assertEquals(0, told)
    }
}
