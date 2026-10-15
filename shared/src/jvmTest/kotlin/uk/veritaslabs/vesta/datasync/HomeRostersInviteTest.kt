package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.getDeviceName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.automerge.InviteStatus
import uk.veritaslabs.vesta.automerge.inviteSecretHash
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.homejoin.InviteLink
import uk.veritaslabs.vesta.profile.ProfileRepository

class HomeRostersInviteTest {

    private val homeId = "home-1"
    private var now = 1_000L

    private class Fixture(val database: VestaDatabase, val rosters: HomeRosters)

    /** A Home with admin "mark" (two devices), admin "jo" and ordinary member "sam", each with their device(s). */
    private suspend fun fixture(): Fixture {
        val database = openInMemoryDatabase()
        val rosters = HomeRosters(database, ProfileRepository(database, deviceName = ::getDeviceName), nowMillis = { now }, deviceName = ::getDeviceName)
        database.homeQueries.insert(homeId, "Maple Street", "family", 1L)
        rosters.admit(homeId, "mark", "Mark", "admin", "agent-mark-1")
        rosters.admit(homeId, "mark", "Mark", "admin", "agent-mark-2")
        rosters.admit(homeId, "jo", "Jo", "admin", "agent-jo")
        rosters.admit(homeId, "sam", "Sam", "member", "agent-sam")
        return Fixture(database, rosters)
    }

    @Test
    fun anAdminsInviteNamesTheAdminDevicesWithTheirsFirst() = runBlocking {
        // Arrange
        val rosters = fixture().rosters

        // Act
        val link = rosters.createInvite(homeId, "jo", ttlMillis = 5_000L)

        // Assert
        assertEquals(
            InviteLink(homeId, link!!.secret, expiresAt = 6_000L, adminAgentIds = listOf("agent-jo", "agent-mark-1", "agent-mark-2"), homeName = "Maple Street"),
            link,
        )
    }

    @Test
    fun anOrdinaryMemberCannotCreateAnInvite() = runBlocking {
        // Arrange
        val rosters = fixture().rosters

        // Act
        val link = rosters.createInvite(homeId, "sam", ttlMillis = 5_000L)

        // Assert
        assertNull(link)
        assertEquals(emptyList(), rosters.invites(homeId))
    }

    @Test
    fun aRemovedAdminCannotCreateAnInvite() = runBlocking {
        // Arrange
        val rosters = fixture().rosters
        rosters.remove(homeId, "jo")

        // Act
        val link = rosters.createInvite(homeId, "jo", ttlMillis = 5_000L)

        // Assert
        assertNull(link)
    }

    @Test
    fun theRosterKeepsTheHashOfTheSecretNotTheSecret() = runBlocking {
        // Arrange
        val rosters = fixture().rosters

        // Act
        val link = rosters.createInvite(homeId, "mark", ttlMillis = 5_000L)!!

        // Assert
        val invite = rosters.invites(homeId).single()
        assertEquals(inviteSecretHash(link.secret), invite.secretHash)
        assertEquals("mark", invite.createdBy)
    }

    @Test
    fun aFreshInviteIsValidForSomeoneNew() = runBlocking {
        // Arrange
        val rosters = fixture().rosters
        val link = rosters.createInvite(homeId, "mark", ttlMillis = 5_000L)!!

        // Act
        val status = rosters.checkInvite(homeId, link.secret, "newcomer")

        // Assert
        assertEquals(InviteStatus.Valid, status)
    }

    @Test
    fun aWrongSecretIsUnknown() = runBlocking {
        // Arrange
        val rosters = fixture().rosters
        rosters.createInvite(homeId, "mark", ttlMillis = 5_000L)

        // Act
        val status = rosters.checkInvite(homeId, "guess", "newcomer")

        // Assert
        assertNull(status)
    }

    @Test
    fun anInviteForAnotherHomeIsUnknown() = runBlocking {
        // Arrange
        val rosters = fixture().rosters
        val link = rosters.createInvite(homeId, "mark", ttlMillis = 5_000L)!!

        // Act
        val status = rosters.checkInvite("other-home", link.secret, "newcomer")

        // Assert
        assertNull(status)
    }

    @Test
    fun anInviteExpiresAfterItsLifetime() = runBlocking {
        // Arrange
        val rosters = fixture().rosters
        val link = rosters.createInvite(homeId, "mark", ttlMillis = 5_000L)!!

        // Act
        now += 5_000L
        val status = rosters.checkInvite(homeId, link.secret, "newcomer")

        // Assert
        assertEquals(InviteStatus.Expired, status)
    }

    @Test
    fun aRevokedInviteIsNoLongerValid() = runBlocking {
        // Arrange
        val rosters = fixture().rosters
        val link = rosters.createInvite(homeId, "mark", ttlMillis = 5_000L)!!

        // Act
        rosters.revokeInvite(homeId, inviteSecretHash(link.secret))
        val status = rosters.checkInvite(homeId, link.secret, "newcomer")

        // Assert
        assertEquals(InviteStatus.Revoked, status)
    }

    @Test
    fun aSingleUseInviteIsSpentAfterOneAdmission() = runBlocking {
        // Arrange
        val rosters = fixture().rosters
        val link = rosters.createInvite(homeId, "mark", ttlMillis = 5_000L)!!

        // Act
        rosters.useInvite(homeId, link.secret, "newcomer")
        val other = rosters.checkInvite(homeId, link.secret, "someone-else")
        val same = rosters.checkInvite(homeId, link.secret, "newcomer")

        // Assert
        assertEquals(InviteStatus.Used, other)
        assertEquals(InviteStatus.Valid, same)
    }

    @Test
    fun aMultiUseInviteAdmitsUpToItsLimit() = runBlocking {
        // Arrange
        val rosters = fixture().rosters
        val link = rosters.createInvite(homeId, "mark", ttlMillis = 5_000L, maxUses = 2)!!

        // Act
        rosters.useInvite(homeId, link.secret, "first")
        val afterOne = rosters.checkInvite(homeId, link.secret, "second")
        rosters.useInvite(homeId, link.secret, "second")
        val afterTwo = rosters.checkInvite(homeId, link.secret, "third")

        // Assert
        assertEquals(InviteStatus.Valid, afterOne)
        assertEquals(InviteStatus.Used, afterTwo)
    }

    @Test
    fun creatingAnInviteReportsTheRosterAsChanged() = runBlocking {
        // Arrange
        val rosters = fixture().rosters
        val changed = mutableListOf<String>()
        val job = launch(Dispatchers.Unconfined) { rosters.changes.collect { changed += it } }

        // Act
        rosters.createInvite(homeId, "mark", ttlMillis = 5_000L)
        job.cancel()

        // Assert
        assertEquals(listOf(homeId), changed)
    }

    /** A Home as just created: its creator is an admin in the roster, but no device of theirs has been published yet. */
    private suspend fun justMadeHome(publishedMember: Boolean = true): Triple<HomeRosters, String, VestaDatabase> {
        val database = openInMemoryDatabase()
        val profiles = ProfileRepository(database, deviceName = ::getDeviceName)
        val rosters = HomeRosters(database, profiles, nowMillis = { now }, deviceName = ::getDeviceName)
        val me = profiles.current().id
        database.homeQueries.insert(homeId, "Maple Street", "family", 1L)
        database.homeMemberQueries.upsert(homeId, me, "Mark", null, "admin", 1L)
        if (publishedMember) rosters.publish(homeId, agentId = null, pairedAgents = emptySet())
        return Triple(rosters, me, database)
    }

    @Test
    fun aJustMadeHomeNamesTheCreatorsOwnDeviceEvenBeforeItWasPublished() = runBlocking {
        // Arrange
        val (rosters, me) = justMadeHome()

        // Act
        val link = rosters.createInvite(homeId, me, ttlMillis = 5_000L, ownAgentId = "agent-mark")

        // Assert
        assertEquals(listOf("agent-mark"), link!!.adminAgentIds)
        assertEquals(setOf("agent-mark"), rosters.devices(homeId)[me])
    }

    @Test
    fun aHomeWhoseRosterHasNothingYetStillGetsTheCreatorAsAdminAndDevice() = runBlocking {
        // Arrange
        val (rosters, me) = justMadeHome(publishedMember = false)

        // Act
        val link = rosters.createInvite(homeId, me, ttlMillis = 5_000L, ownAgentId = "agent-mark")

        // Assert
        assertEquals(listOf("agent-mark"), link!!.adminAgentIds)
        assertEquals(setOf("agent-mark"), rosters.devices(homeId)[me])
    }

    @Test
    fun withNoDeviceKnownAtAllNoInviteIsMade() = runBlocking {
        // Arrange
        val (rosters, me) = justMadeHome()

        // Act
        val link = rosters.createInvite(homeId, me, ttlMillis = 5_000L)

        // Assert
        assertNull(link)
    }
}
