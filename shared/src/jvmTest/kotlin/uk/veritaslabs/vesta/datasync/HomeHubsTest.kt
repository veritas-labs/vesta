package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.automerge.SyncState
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.homegroup.HubResult
import uk.veritaslabs.vesta.homegroup.HubState
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.x0x.X0xApiException

/** A Home's always-on hub (docs/adr/ADR-020, slice 6): enrolling it, recording it in the roster, and removing it. */
class HomeHubsTest {

    private val homeId = "home-1"
    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 100L

    private inner class Device(val agentId: String) {
        val database: VestaDatabase = openInMemoryDatabase()
        val profiles = ProfileRepository(database)
        val rosters = HomeRosters(database, profiles, nowMillis = { clock++ })
        val node = network.node(agentId)
        val groups = HomeGroups(node, database, rosters, profiles, pollInterval = 1.milliseconds, joinTimeout = 100.milliseconds)
        val hubs = HomeHubs(node, database, rosters, profiles, removeRetryDelay = 1.milliseconds, removeAttempts = 2)

        init {
            runBlocking { database.homeQueries.insert(homeId, "Maple Street", "family", 1L) }
            node.onDirect = { message -> scope.launch { groups.handle(message) } }
        }

        suspend fun memberId() = profiles.current().id
        suspend fun groupId() = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.groupId
    }

    private suspend fun sync(a: Device, b: Device) {
        val sa = SyncState()
        val sb = SyncState()
        repeat(10) {
            a.rosters.generate(homeId, sa)?.let { b.rosters.receive(homeId, sb, it) }
            b.rosters.generate(homeId, sb)?.let { a.rosters.receive(homeId, sa, it) }
        }
    }

    private suspend fun eventually(check: suspend () -> Boolean) {
        withTimeout(10.seconds) { while (!check()) delay(20.milliseconds) }
    }

    /** Mark (admin, founder) and Sam (member) in one group with the mailbox open, and a plain x0x daemon [hub] not yet in it. */
    private inner class Household {
        val mark = Device("agent-mark")
        val sam = Device("agent-sam")
        val hub = network.node("agent-hub")

        suspend fun arrange(): Household {
            mark.rosters.admit(homeId, mark.memberId(), "Mark", "admin", mark.agentId)
            mark.rosters.admit(homeId, sam.memberId(), "Sam", "member", sam.agentId)
            sync(mark, sam)
            mark.groups.reconcile()
            sync(mark, sam)
            sam.groups.reconcile()
            eventually { sam.groupId() != null && sam.database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.storeId != null }
            return this
        }

        /** What the admin does on the hub machine: join the group with the invite from the enrolment commands. */
        suspend fun enrolHub() {
            val enrolment = mark.hubs.enrolment(homeId)!!
            val invite = enrolment.commands.lines().first().removePrefix("x0x group join ")
            hub.joinGroup(invite)
            hub.openGroupStore(enrolment.groupId, "mailbox")
        }
    }

    @Test
    fun theEnrolmentCommandsNameTheGroupAndCarryAFreshInvite() = runBlocking {
        // Arrange
        val home = Household().arrange()

        // Act
        val enrolment = home.mark.hubs.enrolment(homeId)

        // Assert
        val groupId = home.mark.groupId()!!
        assertEquals(groupId, enrolment!!.groupId)
        assertEquals(listOf("x0x group join invite:$groupId", "x0x group store create $groupId mailbox"), enrolment.commands.lines())
    }

    @Test
    fun onlyAnAdminOfTheHomeIsGivenTheEnrolmentAndNotBeforeTheMailboxIsReady() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val lone = Device("agent-lone")
        lone.rosters.admit(homeId, lone.memberId(), "Lo", "admin", lone.agentId)

        // Act
        val asMember = home.sam.hubs.enrolment(homeId)
        val beforeAGroup = lone.hubs.enrolment(homeId)

        // Assert
        assertNull(asMember)
        assertNull(beforeAGroup)
    }

    @Test
    fun anAgentThatJoinsTheGroupIsOfferedAsACandidateAndOnlyThat() = runBlocking {
        // Arrange
        val home = Household().arrange()
        assertEquals(emptyList(), home.mark.hubs.candidates(homeId))

        // Act
        home.enrolHub()

        // Assert
        assertEquals(listOf("agent-hub"), home.mark.hubs.candidates(homeId))
    }

    @Test
    fun choosingTheHubRecordsItInTheRosterAndMakesItAnAdminAndEveryMemberSeesIt() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.enrolHub()

        // Act
        val result = home.mark.hubs.useAsHub(homeId, "agent-hub")
        sync(home.mark, home.sam)

        // Assert
        assertEquals(HubResult.Done, result)
        assertEquals("admin", network.groups.getValue(home.mark.groupId()!!).members.getValue("agent-hub").role)
        assertEquals(HubState("agent-hub", inGroup = true), home.mark.hubs.state(homeId))
        assertEquals(HubState("agent-hub", inGroup = true), home.sam.hubs.state(homeId))
        assertEquals(emptyList(), home.mark.hubs.candidates(homeId))
    }

    @Test
    fun anAgentThatActsForAMemberOrIsNotInTheGroupCannotBeTheHub() = runBlocking {
        // Arrange
        val home = Household().arrange()

        // Act
        val member = home.mark.hubs.useAsHub(homeId, "agent-sam")
        val outsider = home.mark.hubs.useAsHub(homeId, "agent-nobody")
        val byMember = home.sam.hubs.useAsHub(homeId, "agent-hub")

        // Assert
        assertEquals(HubResult.ActsForAMember, member)
        assertEquals(HubResult.NotInGroup, outsider)
        assertEquals(HubResult.NotAnAdmin, byMember)
        assertNull(home.mark.rosters.hub(homeId))
    }

    @Test
    fun theHubStaysAnAdminAndInTheGroupThroughLaterRounds() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.enrolHub()
        home.mark.hubs.useAsHub(homeId, "agent-hub")
        val groupId = home.mark.groupId()!!
        home.mark.node.setGroupMemberRole(groupId, "agent-hub", "member") // something resets its role

        // Act
        home.mark.groups.reconcile()

        // Assert
        val members = network.groups.getValue(groupId).members
        assertEquals("admin", members.getValue("agent-hub").role)
        assertTrue("agent-sam" in members)
    }

    @Test
    fun theHubCanAdmitANewMemberWhileEveryoneElseIsOffline() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.enrolHub()
        home.mark.hubs.useAsHub(homeId, "agent-hub")
        network.offline += "agent-mark"
        network.offline += "agent-sam"
        val newcomer = network.node("agent-new")

        // Act
        newcomer.joinGroup(home.hub.createGroupInvite(home.mark.groupId()!!))

        // Assert
        assertTrue("agent-new" in network.groups.getValue(home.mark.groupId()!!).members)
    }

    @Test
    fun removingTheHubTakesItOutOfTheGroupAndForgetsIt() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.enrolHub()
        home.mark.hubs.useAsHub(homeId, "agent-hub")
        val storeId = home.hub.openGroupStore(home.mark.groupId()!!, "mailbox")

        // Act
        val result = home.mark.hubs.removeHub(homeId)

        // Assert
        assertEquals(HubResult.Done, result)
        assertNull(home.mark.rosters.hub(homeId))
        assertEquals(HubState(null, inGroup = false), home.mark.hubs.state(homeId))
        assertTrue("agent-hub" !in network.groups.getValue(home.mark.groupId()!!).members)
        val cutOff = runCatching { home.hub.getValue(storeId, "any") }.exceptionOrNull()
        assertEquals(404, (cutOff as X0xApiException).status)
    }

    @Test
    fun aHubThatCouldNotBeRemovedStaysRecordedSoItCanBeTriedAgain() = runBlocking {
        // Arrange: Ann is an admin of the Home but not yet of the group, so x0x refuses her removal
        val home = Household().arrange()
        val ann = Device("agent-ann")
        home.mark.rosters.admit(homeId, ann.memberId(), "Ann", "admin", ann.agentId)
        sync(home.mark, ann)
        ann.groups.reconcile()
        eventually { ann.groupId() != null }
        home.enrolHub()
        home.mark.hubs.useAsHub(homeId, "agent-hub")
        sync(home.mark, ann)

        // Act
        val result = ann.hubs.removeHub(homeId)

        // Assert
        assertEquals(HubResult.Failed, result)
        assertNotNull(ann.rosters.hub(homeId))
        assertTrue("agent-hub" in network.groups.getValue(home.mark.groupId()!!).members)
    }
}
