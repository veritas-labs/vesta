package uk.veritaslabs.vesta.profile

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.vesta.db.openInMemoryDatabase

class ProfileRepositoryTest {

    @Test
    fun createsAProfileNamedAfterTheDeviceOnFirstUse() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = ProfileRepository(db, deviceName = { "Mark's laptop" })

        // Act
        val profile = repository.current()

        // Assert
        assertEquals("Mark's laptop", profile.displayName)
        assertEquals(listOf(profile.id), db.profileQueries.selectAll().awaitAsList().map { it.id })
    }

    @Test
    fun returnsTheSameProfileOnLaterCalls() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = ProfileRepository(db, deviceName = { "Mark's laptop" })
        val first = repository.current()

        // Act
        val second = ProfileRepository(db, deviceName = { "Renamed" }).current()

        // Assert
        assertEquals(first, second)
        assertEquals(1, db.profileQueries.selectAll().awaitAsList().size)
    }

    @Test
    fun adoptingAnotherProfileTakesItsIdAndMovesTheMemberships() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = ProfileRepository(db, deviceName = { "Tablet" })
        val mine = repository.current()
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeMemberQueries.insert("home-1", mine.id, "Tablet", null, "admin", 1L)

        // Act
        repository.adopt(CurrentProfile("shared-id", "Mark", null))

        // Assert
        assertEquals(CurrentProfile("shared-id", "Mark", null), repository.current())
        val member = db.homeMemberQueries.selectByHome("home-1").awaitAsList().single()
        assertEquals("shared-id", member.memberId)
        assertEquals("Mark", member.name)
        assertEquals("admin", member.role)
    }

    @Test
    fun adoptingTheProfileItAlreadyHasChangesNothing() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = ProfileRepository(db, deviceName = { "Tablet" })
        val mine = repository.current()

        // Act
        repository.adopt(mine)

        // Assert
        assertEquals(mine, repository.current())
        assertEquals(1, db.profileQueries.selectAll().awaitAsList().size)
    }

    @Test
    fun adoptingARenamedProfileRenamesItsMemberships() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = ProfileRepository(db, deviceName = { "Tablet" })
        val mine = repository.current()
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeMemberQueries.insert("home-1", mine.id, "Tablet", null, "admin", 1L)

        // Act
        repository.adopt(mine.copy(displayName = "Mark"))

        // Assert
        assertEquals("Mark", db.homeMemberQueries.selectByHome("home-1").awaitAsList().single().name)
    }
}
