package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.getDeviceName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.profile.ProfileRepository

/** Marking a device unpaired across the Homes that list it (docs/plans/unpaired-device-gap-plan.md, slice 1). */
class HomeRostersGoneTest {

    private class Fixture(val database: KitsDatabase, val rosters: HomeRosters, val me: String)

    /** Two Homes. In both, this user has two devices; in "home-1" Sam's device is listed too, and in "home-2" only this user is. */
    private suspend fun fixture(): Fixture {
        val database = openInMemoryDatabase()
        val profiles = ProfileRepository(database, deviceName = ::getDeviceName)
        val rosters = HomeRosters(database, profiles, deviceName = ::getDeviceName)
        val me = profiles.current().id
        listOf("home-1", "home-2").forEach { id ->
            database.homeQueries.insert(id, id, "family", 1L)
            rosters.admit(id, me, "Me", "admin", "agent-me-1")
            rosters.admit(id, me, "Me", "admin", "agent-me-2")
        }
        rosters.admit("home-1", "sam", "Sam", "member", "agent-sam")
        return Fixture(database, rosters, me)
    }

    private suspend fun HomeRosters.devicesOf(homeId: String, memberId: String) = members(homeId).first { it.memberId == memberId }.devices

    @Test
    fun anUnpairedDeviceIsLeftOutOfEveryHomeThatListsIt() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.rosters.markGone("agent-me-2")

        // Assert
        assertEquals(setOf("agent-me-1"), f.rosters.devicesOf("home-1", f.me))
        assertEquals(setOf("agent-me-1"), f.rosters.devicesOf("home-2", f.me))
    }

    @Test
    fun aDeviceOfSomeoneElseIsNotMarked() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.rosters.markGone("agent-sam")

        // Assert
        assertEquals(setOf("agent-sam"), f.rosters.devicesOf("home-1", "sam"))
    }

    @Test
    fun aHomeThatDoesNotListTheDeviceIsLeftAlone() = runBlocking {
        // Arrange
        val f = fixture()
        f.database.homeQueries.insert("home-3", "home-3", "family", 1L)
        f.rosters.admit("home-3", f.me, "Me", "admin", "agent-me-1")
        val changes = mutableListOf<String>()
        val job = launch(Dispatchers.Unconfined) { f.rosters.changes.collect { changes += it } }

        // Act
        f.rosters.markGone("agent-me-2")
        job.cancel()

        // Assert: it is marked in the Homes that list it, and only those announce a change
        assertEquals(setOf("agent-me-1"), f.rosters.devicesOf("home-3", f.me))
        assertEquals(setOf("home-1", "home-2"), changes.toSet())
    }

    @Test
    fun pairingTheDeviceAgainBringsItBackInEveryHome() = runBlocking {
        // Arrange
        val f = fixture()
        f.rosters.markGone("agent-me-2")

        // Act
        f.rosters.clearGone("agent-me-2")

        // Assert
        assertEquals(setOf("agent-me-1", "agent-me-2"), f.rosters.devicesOf("home-1", f.me))
        assertEquals(setOf("agent-me-1", "agent-me-2"), f.rosters.devicesOf("home-2", f.me))
    }
}
