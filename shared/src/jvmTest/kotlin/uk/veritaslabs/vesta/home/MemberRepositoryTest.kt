package uk.veritaslabs.vesta.home

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.kits.spaces.ProfileRepository
import uk.veritaslabs.kits.spaces.MemberRole

class MemberRepositoryTest {

    @Test
    fun listsTheCurrentUserFirstAndMarksThem() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        val me = profiles.current()
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeMemberQueries.insert("home-1", "sam", "Sam", null, "admin", 1L)
        db.homeMemberQueries.insert("home-1", me.id, "Mark", null, "member", 2L)

        // Act
        val members = MemberRepository(db, profiles).members("home-1").first()

        // Assert
        assertEquals(listOf("Mark", "Sam"), members.map { it.name })
        assertEquals(listOf(true, false), members.map { it.isYou })
    }

    @Test
    fun treatsAnUnrecognisedRoleAsMember() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeMemberQueries.insert("home-1", "sam", "Sam", null, "wizard", 1L)

        // Act
        val members = MemberRepository(db, profiles).members("home-1").first()

        // Assert
        assertEquals(listOf(MemberRole.Member), members.map { it.role })
    }

    @Test
    fun onlyListsTheRequestedHomesMembers() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeQueries.insert("home-2", "Parents", "family", 1L)
        db.homeMemberQueries.insert("home-1", "sam", "Sam", null, "member", 1L)
        db.homeMemberQueries.insert("home-2", "pat", "Pat", null, "member", 1L)

        // Act
        val members = MemberRepository(db, profiles).members("home-2").first()

        // Assert
        assertEquals(listOf("Pat"), members.map { it.name })
    }

    private class Recorder : RosterChangeListener {
        val changes = mutableListOf<Triple<String, String, String>>()
        val removed = mutableListOf<Pair<String, String>>()
        override suspend fun roleChanged(homeId: String, memberId: String, role: String) {
            changes += Triple(homeId, memberId, role)
        }
        override suspend fun memberRemoved(homeId: String, memberId: String) {
            removed += homeId to memberId
        }
        override suspend fun memberLeft(homeId: String, memberId: String) = Unit
    }

    private suspend fun adminHomeWith(db: uk.veritaslabs.kits.store.KitsDatabase, profiles: ProfileRepository, vararg others: Pair<String, String>) {
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeMemberQueries.insert("home-1", profiles.current().id, "Mark", null, "admin", 1L)
        others.forEach { (id, role) -> db.homeMemberQueries.insert("home-1", id, id, null, role, 2L) }
    }

    @Test
    fun anAdminCanMakeAMemberAnAdmin() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        val recorder = Recorder()
        adminHomeWith(db, profiles, "sam" to "member")

        // Act
        val result = MemberRepository(db, profiles, recorder).setRole("home-1", "sam", MemberRole.Admin)

        // Assert
        assertEquals(RoleChangeResult.Changed, result)
        assertEquals("admin", db.homeMemberQueries.selectByHome("home-1").awaitAsList().first { it.memberId == "sam" }.role)
        assertEquals(listOf(Triple("home-1", "sam", "admin")), recorder.changes)
    }

    @Test
    fun anOrdinaryMemberCannotChangeRoles() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        val recorder = Recorder()
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeMemberQueries.insert("home-1", "sam", "Sam", null, "admin", 1L)
        db.homeMemberQueries.insert("home-1", profiles.current().id, "Mark", null, "member", 2L)

        // Act
        val result = MemberRepository(db, profiles, recorder).setRole("home-1", profiles.current().id, MemberRole.Admin)

        // Assert
        assertEquals(RoleChangeResult.NotAnAdmin, result)
        assertEquals(emptyList(), recorder.changes)
    }

    @Test
    fun theLastAdminCannotBeDemoted() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        adminHomeWith(db, profiles, "sam" to "member")

        // Act
        val result = MemberRepository(db, profiles).setRole("home-1", profiles.current().id, MemberRole.Member)

        // Assert
        assertEquals(RoleChangeResult.LastAdmin, result)
    }

    @Test
    fun anAdminCanStepDownWhenAnotherAdminRemains() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        adminHomeWith(db, profiles, "sam" to "admin")

        // Act
        val result = MemberRepository(db, profiles).setRole("home-1", profiles.current().id, MemberRole.Member)

        // Assert
        assertEquals(RoleChangeResult.Changed, result)
    }

    @Test
    fun changingARoleToWhatItAlreadyIsDoesNotTellAnyone() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        val recorder = Recorder()
        adminHomeWith(db, profiles, "sam" to "admin")

        // Act
        val result = MemberRepository(db, profiles, recorder).setRole("home-1", "sam", MemberRole.Admin)

        // Assert
        assertEquals(RoleChangeResult.Changed, result)
        assertEquals(emptyList(), recorder.changes)
    }

    @Test
    fun anUnknownMemberCannotBeChanged() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        adminHomeWith(db, profiles)

        // Act
        val result = MemberRepository(db, profiles).setRole("home-1", "ghost", MemberRole.Admin)

        // Assert
        assertEquals(RoleChangeResult.NoSuchMember, result)
    }

    @Test
    fun anAdminCanRemoveAMember() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        val recorder = Recorder()
        adminHomeWith(db, profiles, "sam" to "member")

        // Act
        val result = MemberRepository(db, profiles, recorder).removeMember("home-1", "sam")

        // Assert
        assertEquals(RemoveMemberResult.Removed, result)
        assertEquals(listOf(profiles.current().id), db.homeMemberQueries.selectByHome("home-1").awaitAsList().map { it.memberId })
        assertEquals(listOf("home-1" to "sam"), recorder.removed)
    }

    @Test
    fun anOrdinaryMemberCannotRemoveAnyone() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        val recorder = Recorder()
        db.homeQueries.insert("home-1", "Flat 4B", "flatshare", 1L)
        db.homeMemberQueries.insert("home-1", "boss", "Boss", null, "admin", 1L)
        db.homeMemberQueries.insert("home-1", profiles.current().id, "Mark", null, "member", 2L)
        db.homeMemberQueries.insert("home-1", "sam", "Sam", null, "member", 3L)

        // Act
        val result = MemberRepository(db, profiles, recorder).removeMember("home-1", "sam")

        // Assert
        assertEquals(RemoveMemberResult.NotAnAdmin, result)
        assertEquals(emptyList(), recorder.removed)
        assertEquals(3, db.homeMemberQueries.selectByHome("home-1").awaitAsList().size)
    }

    @Test
    fun anAdminCannotRemoveThemselves() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        adminHomeWith(db, profiles, "sam" to "admin")

        // Act
        val result = MemberRepository(db, profiles).removeMember("home-1", profiles.current().id)

        // Assert
        assertEquals(RemoveMemberResult.IsYou, result)
    }

    @Test
    fun anUnknownMemberCannotBeRemoved() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val profiles = ProfileRepository(db, deviceName = { "Mark" })
        adminHomeWith(db, profiles)

        // Act
        val result = MemberRepository(db, profiles).removeMember("home-1", "ghost")

        // Assert
        assertEquals(RemoveMemberResult.NoSuchMember, result)
    }
}
