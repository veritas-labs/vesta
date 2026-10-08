package uk.veritaslabs.vesta.datasync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.profile.ProfileRepository

class HomeRostersDeviceNameTest {

    private suspend fun rosterFor(name: () -> String): HomeRosters {
        val database = openInMemoryDatabase()
        val profiles = ProfileRepository(database)
        val rosters = HomeRosters(database, profiles, deviceName = name)
        database.homeQueries.insert("home-1", "Home", "family", 1L)
        rosters.admit("home-1", profiles.current().id, "Me", "admin", "agent-me")
        return rosters
    }

    @Test
    fun publishingRecordsTheNameThisDeviceGivesItself() = runBlocking {
        // Arrange
        val rosters = rosterFor { "Pixel 9" }

        // Act
        rosters.publish("home-1", "agent-me", pairedAgents = emptySet())

        // Assert
        assertEquals(mapOf("agent-me" to "Pixel 9"), rosters.deviceNames("home-1"))
    }

    @Test
    fun aRenamedDeviceUpdatesItsName() = runBlocking {
        // Arrange
        var name = "Pixel 9"
        val rosters = rosterFor { name }
        rosters.publish("home-1", "agent-me", pairedAgents = emptySet())

        // Act
        name = "Mark's phone"
        rosters.publish("home-1", "agent-me", pairedAgents = emptySet())

        // Assert
        assertEquals(mapOf("agent-me" to "Mark's phone"), rosters.deviceNames("home-1"))
    }

    @Test
    fun aBlankNameIsNotRecorded() = runBlocking {
        // Arrange
        val rosters = rosterFor { "  " }

        // Act
        rosters.publish("home-1", "agent-me", pairedAgents = emptySet())

        // Assert
        assertEquals(emptyMap(), rosters.deviceNames("home-1"))
    }
}
