package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.getDeviceName
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
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
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.openInMemoryDatabase
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
        val database: KitsDatabase = openInMemoryDatabase()
        val profiles = ProfileRepository(database, deviceName = ::getDeviceName)
        val rosters = HomeRosters(database, profiles, nowMillis = { clock++ }, deviceName = ::getDeviceName)
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
        assertEquals(listOf(HubState("agent-hub", inGroup = true)), home.mark.hubs.hubs(homeId))
        assertEquals(listOf(HubState("agent-hub", inGroup = true)), home.sam.hubs.hubs(homeId))
        assertEquals(emptyList(), home.mark.hubs.candidates(homeId))
    }

    @Test
    fun aDeviceOfAPlainMemberOrAnAgentNotInTheGroupCannotBeTheHub() = runBlocking {
        // Arrange
        val home = Household().arrange()

        // Act
        val member = home.mark.hubs.useAsHub(homeId, "agent-sam")
        val outsider = home.mark.hubs.useAsHub(homeId, "agent-nobody")
        val byMember = home.sam.hubs.useAsHub(homeId, "agent-hub")

        // Assert
        assertEquals(HubResult.NotAnAdminDevice, member)
        assertEquals(HubResult.NotInGroup, outsider)
        assertEquals(HubResult.NotAnAdmin, byMember)
        assertTrue(home.mark.rosters.hubs(homeId).isEmpty())
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
        val result = home.mark.hubs.removeHub(homeId, "agent-hub")

        // Assert
        assertEquals(HubResult.Done, result)
        assertTrue(home.mark.rosters.hubs(homeId).isEmpty())
        assertEquals(emptyList(), home.mark.hubs.hubs(homeId))
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
        val result = ann.hubs.removeHub(homeId, "agent-hub")

        // Assert
        assertEquals(HubResult.Failed, result)
        assertTrue("agent-hub" in ann.rosters.hubs(homeId))
        assertTrue("agent-hub" in network.groups.getValue(home.mark.groupId()!!).members)
    }

    @Test
    fun severalHubsAreRecordedAndEachIsMadeAnAdmin() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.enrolHub()
        val second = network.node("agent-hub-2")
        val enrolment = home.mark.hubs.enrolment(homeId)!!
        second.joinGroup(enrolment.commands.lines().first().removePrefix("x0x group join "))
        second.openGroupStore(enrolment.groupId, "mailbox")

        // Act
        val first = home.mark.hubs.useAsHub(homeId, "agent-hub")
        val other = home.mark.hubs.useAsHub(homeId, "agent-hub-2")
        sync(home.mark, home.sam)

        // Assert
        assertEquals(HubResult.Done, first)
        assertEquals(HubResult.Done, other)
        val members = network.groups.getValue(home.mark.groupId()!!).members
        assertEquals("admin", members.getValue("agent-hub").role)
        assertEquals("admin", members.getValue("agent-hub-2").role)
        assertEquals(
            listOf(HubState("agent-hub", inGroup = true), HubState("agent-hub-2", inGroup = true)),
            home.sam.hubs.hubs(homeId),
        )
    }

    @Test
    fun removingOneOfTwoHubsLeavesTheOtherServingTheMailbox() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.enrolHub()
        val second = network.node("agent-hub-2")
        val enrolment = home.mark.hubs.enrolment(homeId)!!
        second.joinGroup(enrolment.commands.lines().first().removePrefix("x0x group join "))
        val storeId = second.openGroupStore(enrolment.groupId, "mailbox")
        home.mark.hubs.useAsHub(homeId, "agent-hub")
        home.mark.hubs.useAsHub(homeId, "agent-hub-2")
        home.mark.node.putValue(storeId, "entry", byteArrayOf(1, 2, 3))

        // Act
        val result = home.mark.hubs.removeHub(homeId, "agent-hub")

        // Assert
        assertEquals(HubResult.Done, result)
        assertEquals(setOf("agent-hub-2"), home.mark.rosters.hubs(homeId))
        assertTrue("agent-hub-2" in network.groups.getValue(home.mark.groupId()!!).members)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(second.getValue(storeId, "entry")))
    }

    @Test
    fun anAdminsOwnDeviceCanBeAHubAndStaysAMemberWhenItStopsBeingOne() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val before = home.mark.hubs.ownDeviceCandidates(homeId)

        // Act
        val chosen = home.mark.hubs.useAsHub(homeId, "agent-mark")
        val whileAHub = home.mark.hubs.hubs(homeId)
        val offered = home.mark.hubs.ownDeviceCandidates(homeId)
        val removed = home.mark.hubs.removeHub(homeId, "agent-mark")

        // Assert
        assertEquals(listOf("agent-mark"), before)
        assertEquals(HubResult.Done, chosen)
        assertEquals(listOf(HubState("agent-mark", inGroup = true, isDevice = true)), whileAHub)
        assertEquals(emptyList(), offered)
        assertEquals(HubResult.Done, removed)
        assertTrue(home.mark.rosters.hubs(homeId).isEmpty())
        assertTrue("agent-mark" in network.groups.getValue(home.mark.groupId()!!).members)
    }

    @Test
    fun aDeviceThatIsAHubIsNotOfferedAsAnEnrolledMachine() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.mark.hubs.useAsHub(homeId, "agent-mark")

        // Act
        val candidates = home.mark.hubs.candidates(homeId)

        // Assert
        assertEquals(emptyList(), candidates)
    }

    @Test
    fun anAdminCanAlsoUseAnotherAdminsDeviceButNotAPlainMembersAsAHub() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val ann = Device("agent-ann")
        home.mark.rosters.admit(homeId, ann.memberId(), "Ann", "admin", ann.agentId)
        sync(home.mark, ann)
        ann.groups.reconcile()
        eventually { ann.groupId() != null }
        home.mark.groups.reconcile()
        eventually { network.groups.getValue(home.mark.groupId()!!).members.containsKey("agent-ann") }

        // Act
        val admins = home.mark.hubs.useAsHub(homeId, "agent-ann")
        val plain = home.mark.hubs.useAsHub(homeId, "agent-sam")

        // Assert
        assertEquals(HubResult.Done, admins)
        assertEquals(HubResult.NotAnAdminDevice, plain)
        assertEquals(setOf("agent-ann"), home.mark.rosters.hubs(homeId))
    }

    @Test
    fun aHubThatIsLaterPairedAsAMembersDeviceKeepsBothRoles() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.enrolHub()
        home.mark.hubs.useAsHub(homeId, "agent-hub")

        // Act: Vesta is installed on the hub's machine and paired as one of Mark's devices
        home.mark.rosters.admit(homeId, home.mark.memberId(), "Mark", "admin", "agent-hub")
        home.mark.groups.reconcile()

        // Assert
        assertEquals(listOf(HubState("agent-hub", inGroup = true, isDevice = true)), home.mark.hubs.hubs(homeId))
        assertEquals("admin", network.groups.getValue(home.mark.groupId()!!).members.getValue("agent-hub").role)
    }

    @Test
    fun unpairingADeviceThatIsAHubKeepsItAHubAndInTheGroup() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val second = network.node("agent-mark-2")
        second.joinGroup(home.mark.node.createGroupInvite(home.mark.groupId()!!))
        home.mark.rosters.admit(homeId, home.mark.memberId(), "Mark", "admin", "agent-mark-2")
        home.mark.hubs.useAsHub(homeId, "agent-mark-2")

        // Act: Mark's first device no longer lists the second as paired
        home.mark.rosters.publish(homeId, "agent-mark", pairedAgents = emptySet())
        home.mark.groups.reconcile()

        // Assert
        assertEquals(setOf("agent-mark"), home.mark.rosters.members(homeId).first { !it.removed && "agent-mark" in it.devices }.devices)
        assertEquals(listOf(HubState("agent-mark-2", inGroup = true, isDevice = false)), home.mark.hubs.hubs(homeId))
        assertEquals("admin", network.groups.getValue(home.mark.groupId()!!).members.getValue("agent-mark-2").role)
    }

    @Test
    fun aHubThatWasUnpairedIsTakenOutOfTheGroupWhenItIsRemovedAsAHub() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val second = network.node("agent-mark-2")
        second.joinGroup(home.mark.node.createGroupInvite(home.mark.groupId()!!))
        home.mark.rosters.admit(homeId, home.mark.memberId(), "Mark", "admin", "agent-mark-2")
        home.mark.hubs.useAsHub(homeId, "agent-mark-2")
        home.mark.rosters.publish(homeId, "agent-mark", pairedAgents = emptySet())

        // Act
        val result = home.mark.hubs.removeHub(homeId, "agent-mark-2")

        // Assert
        assertEquals(HubResult.Done, result)
        assertTrue("agent-mark-2" !in network.groups.getValue(home.mark.groupId()!!).members)
        assertTrue(home.mark.rosters.hubs(homeId).isEmpty())
    }

    @Test
    fun theEnrolmentInviteLapsesAfterAnHourAndIsNotTiedToAnAgent() = runBlocking {
        // Arrange
        val home = Household().arrange()

        // Act
        home.mark.hubs.enrolment(homeId)

        // Assert: the machine that will join is not known yet, so only the lifetime is set
        val minted = network.minted.last { it.mintedBy == "agent-mark" }
        assertEquals(3600L, minted.expirySecs)
        assertNull(minted.intendedJoiner)
    }
}
