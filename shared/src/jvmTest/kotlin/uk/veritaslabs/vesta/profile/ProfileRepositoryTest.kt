package uk.veritaslabs.vesta.profile

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.kits.store.openInMemoryDatabase

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

    private class Recorder : ProfileChangeListener {
        var changes = 0
        override suspend fun changed() {
            changes++
        }
    }

    @Test
    fun aNewProfileIsNotNamedYet() = runTest {
        // Arrange
        val repository = ProfileRepository(openInMemoryDatabase(), deviceName = { "Tablet" })

        // Act
        val profile = repository.current()

        // Assert
        assertEquals(false, profile.named)
    }

    @Test
    fun renamingSetsTheNameMarksItChosenAndRenamesTheMemberships() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val recorder = Recorder()
        val repository = ProfileRepository(db, deviceName = { "Tablet" }, changes = recorder)
        val mine = repository.current()
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeMemberQueries.insert("home-1", mine.id, "Tablet", null, "admin", 1L)

        // Act
        val result = repository.rename("  Mark  ")

        // Assert
        assertEquals(RenameResult.Renamed, result)
        assertEquals(CurrentProfile(mine.id, "Mark", null, named = true), repository.current())
        assertEquals("Mark", db.homeMemberQueries.selectByHome("home-1").awaitAsList().single().name)
        assertEquals(1, recorder.changes)
    }

    @Test
    fun aNameThatIsTooShortOrTooLongIsRefused() = runTest {
        // Arrange
        val recorder = Recorder()
        val repository = ProfileRepository(openInMemoryDatabase(), deviceName = { "Tablet" }, changes = recorder)

        // Act
        val tooShort = repository.rename(" a ")
        val tooLong = repository.rename("x".repeat(41))
        val longest = repository.rename("x".repeat(40))

        // Assert
        assertEquals(RenameResult.InvalidName, tooShort)
        assertEquals(RenameResult.InvalidName, tooLong)
        assertEquals(RenameResult.Renamed, longest)
        assertEquals(1, recorder.changes)
    }

    @Test
    fun observingEmitsTheProfileAndAgainWhenItIsRenamed() = runTest {
        // Arrange
        val repository = ProfileRepository(openInMemoryDatabase(), deviceName = { "Tablet" })
        val first = repository.observe().first()

        // Act
        repository.rename("Mark")
        val second = repository.observe().first()

        // Assert
        assertEquals("Tablet", first.displayName)
        assertEquals(CurrentProfile(first.id, "Mark", null, named = true), second)
    }

    @Test
    fun adoptingANamedProfileMarksTheDeviceNamed() = runTest {
        // Arrange
        val repository = ProfileRepository(openInMemoryDatabase(), deviceName = { "Tablet" })
        repository.current()

        // Act
        repository.adopt(CurrentProfile("shared-id", "Mark", null, named = true))

        // Assert
        assertEquals(CurrentProfile("shared-id", "Mark", null, named = true), repository.current())
    }

    @Test
    fun aPlaceholderFromTheSharedProfileDoesNotUndoANameChosenHere() = runTest {
        // Arrange
        val repository = ProfileRepository(openInMemoryDatabase(), deviceName = { "Tablet" })
        val mine = repository.current()
        repository.rename("Mark")

        // Act
        val replaced = repository.adopt(CurrentProfile(mine.id, "Tablet", null, named = false))

        // Assert
        assertEquals(null, replaced)
        assertEquals(CurrentProfile(mine.id, "Mark", null, named = true), repository.current())
    }
}
