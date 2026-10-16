package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.getDeviceName
import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.automerge.InviteStatus
import uk.veritaslabs.vesta.automerge.inviteSecretHash
import uk.veritaslabs.vesta.automerge.VestaHomeSeeding
import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.home.StoredHome
import uk.veritaslabs.vesta.homejoin.HomeJoinMessage
import uk.veritaslabs.vesta.homejoin.InviteLink
import uk.veritaslabs.vesta.homejoin.JoinRequest
import uk.veritaslabs.vesta.homejoin.RequestResult
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.kits.x0x.X0xDirectMessage

/** The admin side of invite links (docs/adr/ADR-018-invite-links.md): requests, approval and admission. */
class HomeJoinRequestsTest {

    private val homeId = "home-1"
    private val network = FakeX0xNetwork()
    private var now = 1_000L

    private inner class Admin {
        val database: KitsDatabase = openInMemoryDatabase()
        val profiles = ProfileRepository(database, deviceName = ::getDeviceName)
        val rosters = HomeRosters(database, profiles, nowMillis = { now }, deviceName = ::getDeviceName)
        val node = network.node("agent-admin")
        val join = HomeJoinService(
            node, database, rosters,
            object : HomeChangeListener {
                override suspend fun saved(home: StoredHome) = Unit
                override suspend fun deleted(id: String) = Unit
            },
            profiles, VestaHomeSeeding(VestaHomeSources(database), database), nowMillis = { now },
        )

        /** What the joiner "Sam" (agent-sam) has been sent. */
        val received = mutableListOf<HomeJoinMessage>()

        init {
            network.node("agent-sam").onDirect = { message -> HomeJoinMessage.decode(message.payload)?.let { received += it } }
        }

        suspend fun myId() = profiles.current().id

        suspend fun setUp(): InviteLink {
            database.homeQueries.insert(homeId, "Maple Street", "family", 1L)
            rosters.admit(homeId, myId(), "Mark", "admin", "agent-admin")
            return rosters.createInvite(homeId, myId(), ttlMillis = 10_000L)!!
        }

        suspend fun receive(sender: String, message: HomeJoinMessage) = join.handle(
            X0xDirectMessage(sender = sender, payload = Base64.Default.encode(HomeJoinMessage.encode(message)), verified = true, trustDecision = "unknown"),
        )

        suspend fun requestFromSam(link: InviteLink, memberId: String = "sam", agent: String = "agent-sam", sender: String = agent) =
            receive(sender, HomeJoinMessage.Request(homeId, link.secret, memberId, "Sam", agent))

        suspend fun memberIds() = database.homeMemberQueries.selectByHome(homeId).awaitAsList().map { it.memberId }
    }

    @Test
    fun aRequestWithAValidSecretWaitsForApproval() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()

        // Act
        admin.requestFromSam(link)

        // Assert
        assertEquals(listOf(JoinRequest(homeId, "sam", "Sam", 1_000L, approved = false)), admin.join.joinRequests(homeId))
        assertEquals(emptyList(), admin.received)
        assertEquals(listOf(admin.myId()), admin.memberIds())
    }

    @Test
    fun repeatingARequestDoesNotDuplicateIt() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.requestFromSam(link)

        // Act
        now += 500L
        admin.requestFromSam(link)

        // Assert
        assertEquals(listOf(1_000L), admin.join.joinRequests(homeId).map { it.createdAt })
    }

    @Test
    fun aRequestWithAWrongSecretIsIgnoredSilently() = runBlocking {
        // Arrange
        val admin = Admin()
        admin.setUp()

        // Act
        admin.receive("agent-sam", HomeJoinMessage.Request(homeId, "guess", "sam", "Sam", "agent-sam"))

        // Assert
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
        assertEquals(emptyList(), admin.received)
    }

    @Test
    fun aRequestSentByAnotherAgentThanItNamesIsIgnored() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()

        // Act
        admin.requestFromSam(link, agent = "agent-sam", sender = "agent-mallory")

        // Assert
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
    }

    @Test
    fun aDeviceWhoseUserIsNotAnAdminIgnoresRequests() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.rosters.setRole(homeId, admin.myId(), "member")
        admin.rosters.project(homeId)

        // Act
        admin.requestFromSam(link)

        // Assert
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
        assertEquals(emptyList(), admin.received)
    }

    @Test
    fun aRequestOnAnExpiredInviteIsToldItExpired() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        now += 10_000L

        // Act
        admin.requestFromSam(link)

        // Assert
        assertEquals(listOf<HomeJoinMessage>(HomeJoinMessage.Denied(homeId, HomeJoinMessage.DeniedExpired)), admin.received)
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
    }

    @Test
    fun aRequestOnARevokedInviteIsToldItWasRevoked() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.rosters.revokeInvite(homeId, inviteSecretHash(link.secret))

        // Act
        admin.requestFromSam(link)

        // Assert
        assertEquals(listOf<HomeJoinMessage>(HomeJoinMessage.Denied(homeId, HomeJoinMessage.DeniedRevoked)), admin.received)
    }

    @Test
    fun aRequestOnASpentInviteIsToldItWasUsed() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.rosters.useInvite(homeId, link.secret, "alex")

        // Act
        admin.requestFromSam(link)

        // Assert
        assertEquals(listOf<HomeJoinMessage>(HomeJoinMessage.Denied(homeId, HomeJoinMessage.DeniedUsed)), admin.received)
    }

    @Test
    fun approvingSendsTheJoinerTheHome() = runBlocking {
        // Arrange
        val admin = Admin()
        admin.requestFromSam(admin.setUp())

        // Act
        val result = admin.join.approve(homeId, "sam")

        // Assert
        assertEquals(RequestResult.Done, result)
        assertEquals(
            listOf<HomeJoinMessage>(HomeJoinMessage.Invite(homeId, "Maple Street", "family", 1L, admin.myId(), "Mark")),
            admin.received,
        )
        assertTrue(admin.join.joinRequests(homeId).single().approved)
        assertEquals(listOf(admin.myId()), admin.memberIds())
    }

    @Test
    fun anAcceptedAnswerAdmitsTheApprovedJoinerAndSpendsTheInvite() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.requestFromSam(link)
        admin.join.approve(homeId, "sam")

        // Act
        admin.receive("agent-sam", HomeJoinMessage.Accepted(homeId))

        // Assert
        assertEquals(setOf(admin.myId(), "sam"), admin.memberIds().toSet())
        assertEquals(setOf("agent-sam"), admin.rosters.devices(homeId)["sam"])
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
        assertEquals(InviteStatus.Used, admin.rosters.checkInvite(homeId, link.secret, "someone-else"))
    }

    @Test
    fun anAcceptedAnswerFromSomeoneNotApprovedAdmitsNobody() = runBlocking {
        // Arrange
        val admin = Admin()
        admin.requestFromSam(admin.setUp())

        // Act
        admin.receive("agent-sam", HomeJoinMessage.Accepted(homeId))
        admin.receive("agent-mallory", HomeJoinMessage.Accepted(homeId))

        // Assert
        assertEquals(listOf(admin.myId()), admin.memberIds())
        assertEquals(1, admin.join.joinRequests(homeId).size)
    }

    @Test
    fun anOrdinaryMemberCannotApproveOrDecline() = runBlocking {
        // Arrange
        val admin = Admin()
        admin.requestFromSam(admin.setUp())
        admin.rosters.setRole(homeId, admin.myId(), "member")
        admin.rosters.project(homeId)

        // Act
        val approved = admin.join.approve(homeId, "sam")
        val declined = admin.join.decline(homeId, "sam")

        // Assert
        assertEquals(RequestResult.NotAnAdmin, approved)
        assertEquals(RequestResult.NotAnAdmin, declined)
        assertEquals(emptyList(), admin.received)
    }

    @Test
    fun approvingARequestThatDoesNotExistDoesNothing() = runBlocking {
        // Arrange
        val admin = Admin()
        admin.setUp()

        // Act
        val result = admin.join.approve(homeId, "nobody")

        // Assert
        assertEquals(RequestResult.NoSuchRequest, result)
    }

    @Test
    fun approvingAfterTheInviteWasRevokedRefusesAndTellsTheJoiner() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.requestFromSam(link)
        admin.rosters.revokeInvite(homeId, inviteSecretHash(link.secret))

        // Act
        val result = admin.join.approve(homeId, "sam")

        // Assert
        assertEquals(RequestResult.InviteNoLongerValid, result)
        assertEquals(listOf<HomeJoinMessage>(HomeJoinMessage.Denied(homeId, HomeJoinMessage.DeniedRevoked)), admin.received)
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
    }

    @Test
    fun decliningTellsTheJoinerAndHidesTheRequest() = runBlocking {
        // Arrange
        val admin = Admin()
        admin.requestFromSam(admin.setUp())

        // Act
        val result = admin.join.decline(homeId, "sam")

        // Assert
        assertEquals(RequestResult.Done, result)
        assertEquals(listOf<HomeJoinMessage>(HomeJoinMessage.Denied(homeId, HomeJoinMessage.DeniedDeclined)), admin.received)
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
    }

    @Test
    fun aDeclinedJoinerAskingAgainIsDeniedAgainNotAskedAboutTwice() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.requestFromSam(link)
        admin.join.decline(homeId, "sam")
        admin.received.clear()

        // Act
        admin.requestFromSam(link)

        // Assert
        assertEquals(listOf<HomeJoinMessage>(HomeJoinMessage.Denied(homeId, HomeJoinMessage.DeniedDeclined)), admin.received)
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
    }

    @Test
    fun anApprovedJoinerAskingAgainIsSentTheHomeAgain() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.requestFromSam(link)
        admin.join.approve(homeId, "sam")
        admin.received.clear()

        // Act
        admin.requestFromSam(link)

        // Assert
        assertEquals(1, admin.received.filterIsInstance<HomeJoinMessage.Invite>().size)
    }

    @Test
    fun eachRoundResendsTheHomeToApprovedJoinersUntilTheyAnswer() = runBlocking {
        // Arrange
        val admin = Admin()
        admin.requestFromSam(admin.setUp())
        admin.join.approve(homeId, "sam")
        admin.received.clear()

        // Act
        admin.join.resendPending()
        admin.join.resendPending()
        admin.receive("agent-sam", HomeJoinMessage.Accepted(homeId))
        admin.join.resendPending()

        // Assert
        assertEquals(2, admin.received.filterIsInstance<HomeJoinMessage.Invite>().size)
    }

    @Test
    fun aMemberWhoIsAlreadyInAsksFromTheirDeviceIsSentTheHomeNotQueued() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.rosters.admit(homeId, "sam", "Sam", "member", "agent-sam")

        // Act
        admin.requestFromSam(link)

        // Assert
        assertEquals(1, admin.received.filterIsInstance<HomeJoinMessage.Invite>().size)
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
    }

    @Test
    fun someoneClaimingAnExistingMembersIdFromAnotherDeviceGetsNothing() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.rosters.admit(homeId, "sam", "Sam", "member", "agent-sam")
        network.node("agent-mallory").onDirect = { admin.received += HomeJoinMessage.decode(it.payload)!! }

        // Act
        admin.requestFromSam(link, memberId = "sam", agent = "agent-mallory")

        // Assert
        assertEquals(emptyList(), admin.received)
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
    }

    @Test
    fun aRequestNobodyDecidedOnIsDroppedAfterItsLifetime() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.requestFromSam(link)

        // Act
        now += 15.days.inWholeMilliseconds
        admin.join.resendPending()

        // Assert
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
    }

    @Test
    fun anAdminCanCreateListAndRevokeInviteLinks() = runBlocking {
        // Arrange
        val admin = Admin()
        admin.setUp()

        // Act
        val created = admin.join.createInviteLink(homeId)!!
        val listed = admin.join.inviteLinks(homeId)
        admin.join.revokeInviteLink(homeId, listed.last().id)
        val afterRevoke = admin.join.inviteLinks(homeId)

        // Assert
        assertEquals("Maple Street", created.homeName)
        assertEquals(2, listed.size)
        assertEquals(1, afterRevoke.size)
        assertEquals(0, afterRevoke.single().uses)
    }

    @Test
    fun listedInviteLinksLeaveOutExpiredAndSpentOnes() = runBlocking {
        // Arrange
        val admin = Admin()
        val spent = admin.setUp()
        admin.rosters.useInvite(homeId, spent.secret, "alex")

        // Act
        val whileValid = admin.join.inviteLinks(homeId)
        now += 20_000L
        val afterExpiry = admin.join.inviteLinks(homeId)

        // Assert
        assertEquals(emptyList(), whileValid)
        assertEquals(emptyList(), afterExpiry)
    }

    @Test
    fun anOrdinaryMemberCannotCreateListOrRevokeInviteLinks() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        admin.rosters.setRole(homeId, admin.myId(), "member")
        admin.rosters.project(homeId)

        // Act
        val created = admin.join.createInviteLink(homeId)
        val listed = admin.join.inviteLinks(homeId)
        admin.join.revokeInviteLink(homeId, inviteSecretHash(link.secret))

        // Assert
        assertEquals(null, created)
        assertEquals(emptyList(), listed)
        assertEquals(InviteStatus.Valid, admin.rosters.checkInvite(homeId, link.secret, "newcomer"))
    }

    @Test
    fun observedRequestsFollowTheTable() = runBlocking {
        // Arrange
        val admin = Admin()
        val link = admin.setUp()
        val seen = mutableListOf<List<String>>()
        val job = launch(Dispatchers.Unconfined) { admin.join.observeJoinRequests(homeId).collect { seen += it.map { r -> r.memberId } } }

        // Act
        admin.requestFromSam(link)
        withTimeout(5_000) { while (seen.lastOrNull() != listOf("sam")) delay(20) }
        admin.join.decline(homeId, "sam")
        withTimeout(5_000) { while (seen.lastOrNull() != emptyList<String>()) delay(20) }
        job.cancel()

        // Assert
        assertEquals(listOf("sam"), seen.first { it.isNotEmpty() })
        assertEquals(emptyList(), seen.last())
    }
}
