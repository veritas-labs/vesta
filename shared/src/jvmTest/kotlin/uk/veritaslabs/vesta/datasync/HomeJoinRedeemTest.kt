package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.home.StoredHome
import uk.veritaslabs.vesta.homejoin.HomeJoinMessage
import uk.veritaslabs.vesta.homejoin.InviteLink
import uk.veritaslabs.vesta.homejoin.JoinLink
import uk.veritaslabs.vesta.homejoin.PendingJoin
import uk.veritaslabs.vesta.homejoin.RedeemResult
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

/** The joiner side of invite links (docs/adr/ADR-018-invite-links.md), alone and against a real admin service. */
class HomeJoinRedeemTest {

    private val homeId = "home-1"
    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var now = 1_000L

    /** One person's device: its own database and join service on the shared fake mesh. */
    private inner class Party(val agentId: String) {
        val database: VestaDatabase = openInMemoryDatabase()
        val profiles = ProfileRepository(database)
        val rosters = HomeRosters(database, profiles, nowMillis = { now })
        val node = network.node(agentId)
        val join = HomeJoinService(
            node, database, rosters,
            object : HomeChangeListener {
                override suspend fun saved(home: StoredHome) = Unit
                override suspend fun deleted(id: String) = Unit
            },
            profiles, nowMillis = { now },
        )

        init {
            node.onDirect = { message -> scope.launch { join.handle(message) } }
        }

        suspend fun myId() = profiles.current().id
        suspend fun homeIds() = database.homeQueries.selectAll().awaitAsList().map { it.id }
        suspend fun memberIds() = database.homeMemberQueries.selectByHome(homeId).awaitAsList().map { it.memberId }

        suspend fun receive(sender: String, message: HomeJoinMessage) = join.handle(
            X0xDirectMessage(sender = sender, payload = Base64.Default.encode(HomeJoinMessage.encode(message)), verified = true, trustDecision = "unknown"),
        )
    }

    /** A Home with an admin device, and its invite link (the Home has two admin agents named). */
    private suspend fun adminWithInvite(): Pair<Party, InviteLink> {
        val admin = Party("agent-admin")
        admin.database.homeQueries.insert(homeId, "Maple Street", "family", 1L)
        admin.rosters.admit(homeId, admin.myId(), "Mark", "admin", "agent-admin")
        val link = admin.rosters.createInvite(homeId, admin.myId(), ttlMillis = 10_000L)!!
        return admin to link
    }

    private fun linkNaming(vararg agents: String) = InviteLink(homeId, "s3cr3t", expiresAt = 10_000L, adminAgentIds = agents.toList())

    private suspend fun eventually(check: suspend () -> Boolean) {
        withTimeout(10.seconds) { while (!check()) delay(20.milliseconds) }
    }

    /** Collects what a stand-in admin agent is sent. */
    private fun capture(agentId: String): MutableList<HomeJoinMessage> {
        val received = mutableListOf<HomeJoinMessage>()
        network.node(agentId).onDirect = { message -> HomeJoinMessage.decode(message.payload)?.let { received += it } }
        return received
    }

    @Test
    fun openingALinkAsksEveryAdminItNames() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        val first = capture("agent-a")
        val second = capture("agent-b")

        // Act
        val result = sam.join.redeemInvite(linkNaming("agent-a", "agent-b"))

        // Assert
        val expected = HomeJoinMessage.Request(homeId, "s3cr3t", sam.myId(), sam.profiles.current().displayName, "agent-sam")
        assertEquals(RedeemResult.Started, result)
        assertEquals(listOf<HomeJoinMessage>(expected), first)
        assertEquals(listOf<HomeJoinMessage>(expected), second)
        assertEquals(listOf(PendingJoin(homeId, 10_000L, denial = null)), sam.join.pendingJoins())
    }

    @Test
    fun anExpiredLinkIsNotOpened() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        val admin = capture("agent-a")
        now = 10_000L

        // Act
        val result = sam.join.redeemInvite(linkNaming("agent-a"))

        // Assert
        assertEquals(RedeemResult.Expired, result)
        assertEquals(emptyList(), sam.join.pendingJoins())
        assertEquals(emptyList(), admin)
    }

    @Test
    fun aLinkToAHomeThisDeviceHasIsNotOpened() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        sam.database.homeQueries.insert(homeId, "Maple Street", "family", 1L)

        // Act
        val result = sam.join.redeemInvite(linkNaming("agent-a"))

        // Assert
        assertEquals(RedeemResult.AlreadyInHome, result)
        assertEquals(emptyList(), sam.join.pendingJoins())
    }

    @Test
    fun anApprovedRequestJoinsTheHomeEndToEnd() = runBlocking {
        // Arrange
        val (admin, link) = adminWithInvite()
        val sam = Party("agent-sam")
        sam.join.redeemInvite(link)
        eventually { admin.join.joinRequests(homeId).isNotEmpty() }

        // Act
        admin.join.approve(homeId, sam.myId())
        val joined = sam.join.joined.first()

        // Assert
        assertEquals(homeId, joined.homeId)
        eventually { admin.memberIds().toSet() == setOf(admin.myId(), sam.myId()) }
        assertEquals(setOf(admin.myId(), sam.myId()), sam.memberIds().toSet())
        assertEquals(listOf(homeId), sam.homeIds())
        assertEquals(emptyList(), sam.join.pendingJoins())
        assertEquals(emptyList(), admin.join.joinRequests(homeId))
        scope.cancel()
    }

    @Test
    fun aJoinerWhoseAdminWasOfflineAsksAgainOnTheNextRound() = runBlocking {
        // Arrange
        val (admin, link) = adminWithInvite()
        val sam = Party("agent-sam")
        network.offline += admin.agentId
        sam.join.redeemInvite(link)

        // Act
        network.offline -= admin.agentId
        sam.join.resendPending()

        // Assert
        eventually { admin.join.joinRequests(homeId).size == 1 }
        scope.cancel()
    }

    @Test
    fun anInviteFromAnAgentTheLinkDidNotNameIsIgnored() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        sam.join.redeemInvite(linkNaming("agent-a"))

        // Act
        sam.receive("agent-mallory", HomeJoinMessage.Invite(homeId, "Maple Street", "family", 1L, "mallory", "Mallory"))

        // Assert
        assertEquals(emptyList(), sam.homeIds())
        assertEquals(1, sam.join.pendingJoins().size)
    }

    @Test
    fun anInviteForAHomeWeNeverAskedAboutIsIgnored() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        sam.join.redeemInvite(linkNaming("agent-a"))

        // Act
        sam.receive("agent-a", HomeJoinMessage.Invite("other-home", "Elsewhere", "family", 1L, "mark", "Mark"))

        // Assert
        assertEquals(emptyList(), sam.homeIds())
    }

    @Test
    fun anInviteAfterCancellingIsIgnored() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        sam.join.redeemInvite(linkNaming("agent-a"))
        sam.join.cancelPendingJoin(homeId)

        // Act
        sam.receive("agent-a", HomeJoinMessage.Invite(homeId, "Maple Street", "family", 1L, "mark", "Mark"))

        // Assert
        assertEquals(emptyList(), sam.homeIds())
        assertEquals(emptyList(), sam.join.pendingJoins())
    }

    @Test
    fun aDeniedAnswerFromAnAdminIsRemembered() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        sam.join.redeemInvite(linkNaming("agent-a"))

        // Act
        sam.receive("agent-a", HomeJoinMessage.Denied(homeId, HomeJoinMessage.DeniedDeclined))

        // Assert
        assertEquals(listOf(PendingJoin(homeId, 10_000L, HomeJoinMessage.DeniedDeclined)), sam.join.pendingJoins())
    }

    @Test
    fun aDeniedAnswerFromAStrangerIsIgnored() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        sam.join.redeemInvite(linkNaming("agent-a"))

        // Act
        sam.receive("agent-mallory", HomeJoinMessage.Denied(homeId, HomeJoinMessage.DeniedRevoked))

        // Assert
        assertEquals(null, sam.join.pendingJoins().single().denial)
    }

    @Test
    fun aRefusedJoinStopsAskingAndIsNotJoinedByALateInvite() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        val admin = capture("agent-a")
        sam.join.redeemInvite(linkNaming("agent-a"))
        sam.receive("agent-a", HomeJoinMessage.Denied(homeId, HomeJoinMessage.DeniedRevoked))
        admin.clear()

        // Act
        sam.join.resendPending()
        sam.receive("agent-a", HomeJoinMessage.Invite(homeId, "Maple Street", "family", 1L, "mark", "Mark"))

        // Assert
        assertEquals(emptyList(), admin)
        assertEquals(emptyList(), sam.homeIds())
    }

    @Test
    fun eachRoundAsksAgainWhileWaiting() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        val admin = capture("agent-a")
        sam.join.redeemInvite(linkNaming("agent-a"))

        // Act
        sam.join.resendPending()
        sam.join.resendPending()

        // Assert
        assertEquals(3, admin.filterIsInstance<HomeJoinMessage.Request>().size)
    }

    @Test
    fun anExpiredLinkIsNotAskedAgainButStillAcceptsTheHomeAnAdminApproved() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        val admin = capture("agent-a")
        sam.join.redeemInvite(linkNaming("agent-a"))
        admin.clear()
        now = 10_000L

        // Act
        sam.join.resendPending()
        sam.receive("agent-a", HomeJoinMessage.Invite(homeId, "Maple Street", "family", 1L, "mark", "Mark"))

        // Assert
        assertEquals(emptyList(), admin.filterIsInstance<HomeJoinMessage.Request>())
        assertEquals(listOf(homeId), sam.homeIds())
    }

    @Test
    fun aPendingJoinIsForgottenLongAfterItsLinkExpired() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        sam.join.redeemInvite(linkNaming("agent-a"))

        // Act
        now = 10_000L + 15.days.inWholeMilliseconds
        sam.join.resendPending()

        // Assert
        assertEquals(emptyList(), sam.join.pendingJoins())
    }

    @Test
    fun aPendingJoinIsForgottenOnceTheHomeArrivesAnotherWay() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        sam.join.redeemInvite(linkNaming("agent-a"))
        sam.database.homeQueries.insert(homeId, "Maple Street", "family", 1L)

        // Act
        sam.join.resendPending()

        // Assert
        assertEquals(emptyList(), sam.join.pendingJoins())
    }

    @Test
    fun theShortLivedJoinLinkStillWorksAlongsideInviteLinks() = runBlocking {
        // Arrange
        val sam = Party("agent-sam")
        val link: JoinLink = sam.join.openLink()

        // Act
        sam.receive("agent-admin", HomeJoinMessage.Invite(homeId, "Maple Street", "family", 1L, "mark", "Mark"))

        // Assert
        assertEquals(listOf(homeId), sam.homeIds())
        assertTrue(link.memberId in sam.memberIds())
    }
}
