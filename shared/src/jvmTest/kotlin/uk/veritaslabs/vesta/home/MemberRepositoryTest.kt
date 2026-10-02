package uk.veritaslabs.vesta.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.MemberRole

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
}
