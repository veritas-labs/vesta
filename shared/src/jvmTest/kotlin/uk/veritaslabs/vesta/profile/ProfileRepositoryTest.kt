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
}
