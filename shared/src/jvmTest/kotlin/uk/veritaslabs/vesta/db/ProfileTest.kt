package uk.veritaslabs.vesta.db

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ProfileTest {

    @Test
    fun insertedProfileIsReadBack() = runTest {
        // Arrange
        val db = openInMemoryDatabase()

        // Act
        db.profileQueries.insert(
            id = "profile-1",
            displayName = "Arthur",
            avatarResourceId = null,
            x0xInstanceName = "default",
        )
        val profiles = db.profileQueries.selectAll().awaitAsList()

        // Assert
        assertEquals(1, profiles.size)
        assertEquals("Arthur", profiles.single().displayName)
    }

    @Test
    fun selectAllOrdersByDisplayNameCaseInsensitively() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.profileQueries.insert("profile-1", "bob", null, "default")
        db.profileQueries.insert("profile-2", "Arthur", null, "default")
        db.profileQueries.insert("profile-3", "Charlie", null, "default")

        // Act
        val names = db.profileQueries.selectAll().awaitAsList().map { it.displayName }

        // Assert
        assertEquals(listOf("Arthur", "bob", "Charlie"), names)
    }

    @Test
    fun selectByIdReturnsNullWhenProfileDoesNotExist() = runTest {
        // Arrange
        val db = openInMemoryDatabase()

        // Act
        val profile = db.profileQueries.selectById("missing-profile").awaitAsOneOrNull()

        // Assert
        assertNull(profile)
    }

    @Test
    fun updateProfileChangesDisplayNameAvatarAndInstance() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.profileQueries.insert("profile-1", "Arthur", null, "default")

        // Act
        db.profileQueries.updateProfile(
            displayName = "Arthur Weasley",
            avatarResourceId = "avatar-42",
            x0xInstanceName = "arthur-instance",
            id = "profile-1",
        )
        val profile = db.profileQueries.selectById("profile-1").awaitAsOne()

        // Assert
        assertEquals("Arthur Weasley", profile.displayName)
        assertEquals("avatar-42", profile.avatarResourceId)
        assertEquals("arthur-instance", profile.x0xInstanceName)
    }

    @Test
    fun deleteByIdRemovesOnlyTheTargetedProfile() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.profileQueries.insert("profile-1", "Arthur", null, "default")
        db.profileQueries.insert("profile-2", "Molly", null, "default")

        // Act
        db.profileQueries.deleteById("profile-1")
        val remaining = db.profileQueries.selectAll().awaitAsList()

        // Assert
        assertEquals(1, remaining.size)
        assertEquals("profile-2", remaining.single().id)
    }

    @Test
    fun countAllReflectsCurrentNumberOfProfiles() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.profileQueries.insert("profile-1", "Arthur", null, "default")
        db.profileQueries.insert("profile-2", "Molly", null, "default")

        // Act
        db.profileQueries.deleteById("profile-1")
        val count = db.profileQueries.countAll().awaitAsOne()

        // Assert
        assertEquals(1L, count)
    }
}
