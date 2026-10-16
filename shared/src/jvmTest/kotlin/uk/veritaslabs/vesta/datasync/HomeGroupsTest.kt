package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.getDeviceName
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
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
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.kits.x0x.testing.FakeX0xNetwork
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.kits.x0x.X0xApiException

/** A Home's x0x group (docs/adr/ADR-021-home-x0x-group.md) across devices on the fake mesh. */
class HomeGroupsTest {

    private val homeId = "home-1"
    private var clock = 100L
    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One device: its own database, roster and group service on the shared fake mesh, as the member [role]. */
    private inner class Device(val agentId: String) {
        val database: KitsDatabase = openInMemoryDatabase()
        val profiles = ProfileRepository(database, deviceName = ::getDeviceName)
        val rosters = HomeRosters(database, profiles, nowMillis = { clock++ }, deviceName = ::getDeviceName)
        val node = network.node(agentId)
        val groups = HomeGroups(node, database, rosters, profiles, pollInterval = 1.milliseconds, joinTimeout = 100.milliseconds)

        init {
            runBlocking { database.homeQueries.insert(homeId, "Maple Street", "family", 1L) }
            node.onDirect = { message -> scope.launch { groups.handle(message) } }
        }

        suspend fun memberId() = profiles.current().id
        suspend fun row() = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()
        suspend fun groupId() = rosters.groupId(homeId)
    }

    /** Writes [member] into [on]'s roster (joined at an increasing time, so the first one added is the earliest). */
    private suspend fun enrol(on: Device, member: Device, name: String, role: String) =
        on.rosters.admit(homeId, member.memberId(), name, role, member.agentId)

    /** Brings [a]'s and [b]'s rosters to the same state. */
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

    private fun membersOf(groupId: String) = network.groups.getValue(groupId).members

    /** Founder Mark (admin) and Sam (member), each on one device, with their rosters in step. */
    private inner class Household {
        val mark = Device("agent-mark")
        val sam = Device("agent-sam")

        suspend fun arrange(): Household {
            enrol(mark, mark, "Mark", "admin")
            enrol(mark, sam, "Sam", "member")
            sync(mark, sam)
            return this
        }
    }

    @Test
    fun theFoundersDeviceCreatesTheGroupAndOpensTheStore() = runBlocking {
        // Arrange
        val home = Household().arrange()

        // Act
        home.mark.groups.reconcile()

        // Assert
        val groupId = assertNotNull(home.mark.groupId())
        assertEquals(groupId, home.mark.row()?.groupId)
        assertNotNull(home.mark.row()?.storeId)
        assertEquals(setOf("agent-mark"), membersOf(groupId).keys)
    }

    @Test
    fun aDeviceThatIsNotTheFounderCreatesNothing() = runBlocking {
        // Arrange
        val home = Household().arrange()

        // Act
        home.sam.groups.reconcile()

        // Assert
        assertNull(home.sam.groupId())
        assertNull(home.sam.row())
        assertTrue(network.groups.isEmpty())
    }

    @Test
    fun anotherMembersDeviceAsksForAnInviteAndJoinsAndOpensTheStore() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.mark.groups.reconcile()
        sync(home.mark, home.sam)

        // Act
        home.sam.groups.reconcile()
        eventually { home.sam.row()?.storeId != null }

        // Assert
        val groupId = home.mark.groupId()!!
        assertEquals(groupId, home.sam.row()?.groupId)
        assertEquals(setOf("agent-mark", "agent-sam"), membersOf(groupId).keys)
    }

    @Test
    fun aNewDeviceOfAMemberJoinsTheSameWay() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.mark.groups.reconcile()
        val tablet = Device("agent-sam-tablet")
        // The tablet is another device of Sam's: it has the Home and the roster, and the roster lists it for Sam.
        home.sam.rosters.admit(homeId, home.sam.memberId(), "Sam", "member", "agent-sam-tablet")
        sync(home.sam, home.mark)
        sync(home.mark, tablet)

        // Act
        tablet.groups.reconcile()
        eventually { tablet.row()?.storeId != null }

        // Assert
        assertTrue("agent-sam-tablet" in membersOf(home.mark.groupId()!!).keys)
    }

    @Test
    fun anAgentTheRosterDoesNotListIsNotInvited() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.mark.groups.reconcile()
        val stranger = Device("agent-stranger")
        stranger.rosters.admit(homeId, "stranger-member", "Eve", "admin", "agent-stranger")
        stranger.rosters.setGroup(homeId, home.mark.groupId()!!)

        // Act
        stranger.groups.reconcile()
        delay(300.milliseconds)

        // Assert
        assertNull(stranger.row())
        assertTrue("agent-stranger" !in membersOf(home.mark.groupId()!!).keys)
    }

    @Test
    fun aVestaAdminsDeviceBecomesAnX0xAdmin() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val ann = Device("agent-ann")
        enrol(home.mark, ann, "Ann", "admin")
        home.mark.groups.reconcile()
        sync(home.mark, ann)
        ann.groups.reconcile()
        eventually { ann.row()?.storeId != null }

        // Act
        home.mark.groups.reconcile()

        // Assert
        assertEquals("admin", membersOf(home.mark.groupId()!!).getValue("agent-ann").role)
    }

    @Test
    fun aMemberWhoStopsBeingAnAdminLosesTheX0xRole() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val ann = Device("agent-ann")
        enrol(home.mark, ann, "Ann", "admin")
        home.mark.groups.reconcile()
        sync(home.mark, ann)
        ann.groups.reconcile()
        eventually { ann.row()?.storeId != null }
        home.mark.groups.reconcile()

        // Act
        home.mark.rosters.setRole(homeId, ann.memberId(), "member")
        home.mark.groups.reconcile()

        // Assert
        assertEquals("member", membersOf(home.mark.groupId()!!).getValue("agent-ann").role)
    }

    @Test
    fun aRemovedMembersDevicesLeaveTheGroupAndLoseTheStore() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.mark.groups.reconcile()
        sync(home.mark, home.sam)
        home.sam.groups.reconcile()
        eventually { home.sam.row()?.storeId != null }
        val storeId = home.sam.row()!!.storeId!!

        // Act
        home.mark.rosters.remove(homeId, home.sam.memberId())
        home.mark.groups.reconcile()

        // Assert
        assertEquals(setOf("agent-mark"), membersOf(home.mark.groupId()!!).keys)
        val denied = assertFailsWith<X0xApiException> { home.sam.node.getValue(storeId, "any") }
        assertEquals(404, denied.status)
    }

    @Test
    fun removingTheGroupsCreatorReplacesTheGroupAndTheOthersFollow() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val ann = Device("agent-ann")
        enrol(home.mark, ann, "Ann", "admin")
        home.mark.groups.reconcile()
        sync(home.mark, home.sam)
        sync(home.mark, ann)
        home.sam.groups.reconcile()
        ann.groups.reconcile()
        eventually { home.sam.row()?.storeId != null && ann.row()?.storeId != null }
        home.mark.groups.reconcile() // promotes Ann
        val oldGroup = home.mark.groupId()!!

        // Act
        ann.rosters.remove(homeId, home.mark.memberId())
        ann.groups.reconcile()
        sync(ann, home.sam)
        home.sam.groups.reconcile()
        eventually { home.sam.row()?.groupId != null && home.sam.row()?.groupId != oldGroup && home.sam.row()?.storeId != null }

        // Assert
        val newGroup = ann.groupId()!!
        assertNotEquals(oldGroup, newGroup)
        assertEquals(newGroup, ann.row()?.groupId)
        assertEquals(newGroup, home.sam.row()?.groupId)
        assertEquals(setOf("agent-ann", "agent-sam"), membersOf(newGroup).keys)
    }

    @Test
    fun aHomeDeletedOnADeviceMakesItLeaveTheGroup() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.mark.groups.reconcile()
        sync(home.mark, home.sam)
        home.sam.groups.reconcile()
        eventually { home.sam.row()?.storeId != null }
        val groupId = home.mark.groupId()!!

        // Act
        home.sam.database.homeQueries.deleteById(homeId)
        home.sam.groups.reconcile()

        // Assert
        assertNull(home.sam.row())
        assertTrue("agent-sam" !in membersOf(groupId).keys)
    }

    @Test
    fun anAdminDeviceInvitesWhileTheFounderIsOffline() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val ann = Device("agent-ann")
        val joiner = Device("agent-joe")
        enrol(home.mark, ann, "Ann", "admin")
        enrol(home.mark, joiner, "Joe", "member")
        home.mark.groups.reconcile()
        sync(home.mark, ann)
        ann.groups.reconcile()
        eventually { ann.row()?.storeId != null }
        home.mark.groups.reconcile() // promotes Ann
        sync(home.mark, joiner)
        network.offline += "agent-mark"

        // Act
        joiner.groups.reconcile()
        eventually { joiner.row()?.storeId != null }

        // Assert
        assertTrue("agent-joe" in membersOf(home.mark.groupId()!!).keys)
    }

    @Test
    fun everyHubIsKeptAnX0xAdmin() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.mark.groups.reconcile()
        val groupId = home.mark.groupId()!!
        listOf("agent-hub-1", "agent-hub-2").forEach { network.node(it).joinGroup(home.mark.node.createGroupInvite(groupId)) }
        home.mark.rosters.addHub(homeId, "agent-hub-1")
        home.mark.rosters.addHub(homeId, "agent-hub-2")

        // Act
        home.mark.groups.reconcile()

        // Assert
        assertEquals("admin", membersOf(groupId).getValue("agent-hub-1").role)
        assertEquals("admin", membersOf(groupId).getValue("agent-hub-2").role)
    }

    @Test
    fun aHubThatIsAlsoAPlainMembersDeviceIsNotDemoted() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.mark.groups.reconcile()
        sync(home.mark, home.sam)
        home.sam.groups.reconcile()
        eventually { home.sam.row()?.storeId != null }
        home.mark.rosters.addHub(homeId, "agent-sam")

        // Act
        home.mark.groups.reconcile()
        home.mark.groups.reconcile()

        // Assert
        assertEquals("admin", membersOf(home.mark.groupId()!!).getValue("agent-sam").role)
    }

    @Test
    fun anAdminsDeviceStaysAnAdminWhenItStopsBeingAHub() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val ann = Device("agent-ann")
        enrol(home.mark, ann, "Ann", "admin")
        home.mark.groups.reconcile()
        sync(home.mark, ann)
        ann.groups.reconcile()
        eventually { ann.row()?.storeId != null }
        home.mark.rosters.addHub(homeId, "agent-ann")
        home.mark.groups.reconcile()

        // Act
        home.mark.rosters.removeHub(homeId, "agent-ann")
        home.mark.groups.reconcile()

        // Assert
        assertEquals("admin", membersOf(home.mark.groupId()!!).getValue("agent-ann").role)
    }

    @Test
    fun aHubThatIsARemovedMembersDeviceLeavesTheGroupAndStopsBeingAHub() = runBlocking {
        // Arrange
        val home = Household().arrange()
        home.mark.groups.reconcile()
        sync(home.mark, home.sam)
        home.sam.groups.reconcile()
        eventually { home.sam.row()?.storeId != null }
        home.mark.rosters.addHub(homeId, "agent-sam")
        home.mark.groups.reconcile()

        // Act
        home.mark.rosters.remove(homeId, home.sam.memberId())
        home.mark.groups.reconcile()
        home.mark.groups.reconcile()

        // Assert
        assertEquals(setOf("agent-mark"), membersOf(home.mark.groupId()!!).keys)
        assertTrue(home.mark.rosters.hubs(homeId).isEmpty())
    }

    @Test
    fun removingACreatorThatIsAlsoAHubReplacesTheGroupAndClearsItsHubFlag() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val ann = Device("agent-ann")
        enrol(home.mark, ann, "Ann", "admin")
        home.mark.groups.reconcile()
        sync(home.mark, home.sam)
        sync(home.mark, ann)
        home.sam.groups.reconcile()
        ann.groups.reconcile()
        eventually { home.sam.row()?.storeId != null && ann.row()?.storeId != null }
        home.mark.rosters.addHub(homeId, "agent-mark")
        home.mark.groups.reconcile() // promotes Ann
        sync(home.mark, ann)
        val oldGroup = home.mark.groupId()!!

        // Act
        ann.rosters.remove(homeId, home.mark.memberId())
        ann.groups.reconcile()

        // Assert
        val newGroup = ann.groupId()!!
        assertNotEquals(oldGroup, newGroup)
        assertTrue("agent-mark" !in membersOf(newGroup).keys)
        assertTrue(ann.rosters.hubs(homeId).isEmpty())
    }

    // --- A device the user unpaired (docs/plans/unpaired-device-gap-plan.md, slice 2) ---

    /** Sam's tablet, in the Home's group with the mailbox open, and the roster in step on all three devices. Returns the tablet and its store. */
    private suspend fun Household.withTabletInTheGroup(): Pair<Device, String> {
        mark.groups.reconcile()
        sync(mark, sam)
        sam.groups.reconcile()
        eventually { sam.row()?.storeId != null }
        val tablet = Device("agent-sam-tablet")
        sam.rosters.admit(homeId, sam.memberId(), "Sam", "member", "agent-sam-tablet")
        sync(sam, mark)
        sync(mark, tablet)
        tablet.groups.reconcile()
        eventually { tablet.row()?.storeId != null }
        return tablet to tablet.row()!!.storeId!!
    }

    /** Sam unpairs the tablet on his phone, and the admin's device hears of it. */
    private suspend fun Household.unpairTablet(tablet: Device) {
        sam.rosters.markGone(tablet.agentId)
        sync(sam, mark)
        sync(mark, tablet)
    }

    @Test
    fun anUnpairedDeviceIsTakenOutOfTheGroupAndLosesTheStore() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val (tablet, storeId) = home.withTabletInTheGroup()

        // Act
        home.unpairTablet(tablet)
        home.mark.groups.reconcile()

        // Assert
        assertTrue("agent-sam-tablet" !in membersOf(home.mark.groupId()!!).keys)
        assertTrue("agent-sam" in membersOf(home.mark.groupId()!!).keys)
        val denied = assertFailsWith<X0xApiException> { tablet.node.getValue(storeId, "any") }
        assertEquals(404, denied.status)
    }

    @Test
    fun aDeviceThatIsInTheGroupButWasNeverMarkedIsNotRemovedEvenIfTheRosterDoesNotListIt() = runBlocking {
        // Arrange: a device that joined a moment ago, whose claim has not reached this admin device yet
        val home = Household().arrange()
        home.mark.groups.reconcile()
        val groupId = home.mark.groupId()!!
        network.node("agent-joining").joinGroup(home.mark.node.createGroupInvite(groupId))

        // Act
        home.mark.groups.reconcile()
        home.mark.groups.reconcile()

        // Assert
        assertTrue("agent-joining" in membersOf(groupId).keys)
    }

    @Test
    fun anUnpairedDeviceThatIsAHubStaysInTheGroup() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val (tablet, _) = home.withTabletInTheGroup()
        home.mark.rosters.addHub(homeId, tablet.agentId)

        // Act
        home.unpairTablet(tablet)
        home.mark.groups.reconcile()

        // Assert
        assertTrue("agent-sam-tablet" in membersOf(home.mark.groupId()!!).keys)
    }

    @Test
    fun anUnpairedDeviceThatGetsBackInWithAnInviteIsRemovedAgain() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val (tablet, _) = home.withTabletInTheGroup()
        home.unpairTablet(tablet)
        home.mark.groups.reconcile()
        val groupId = home.mark.groupId()!!

        // Act: it joins again with an unused invite (ADR-020 Spike 9), and the next round finds it
        tablet.node.joinGroup(home.mark.node.createGroupInvite(groupId))
        val listedAgain = "agent-sam-tablet" in membersOf(groupId).keys
        home.mark.groups.reconcile()

        // Assert
        assertTrue(listedAgain)
        assertTrue("agent-sam-tablet" !in membersOf(groupId).keys)
    }

    @Test
    fun anUnpairedDeviceIsNotInvitedBackWhenItAsks() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val (tablet, _) = home.withTabletInTheGroup()
        home.unpairTablet(tablet)
        home.mark.groups.reconcile()

        // Act: it notices it is out, and asks an admin device for an invite
        repeat(3) { tablet.groups.reconcile() }
        delay(300.milliseconds)

        // Assert
        assertNull(tablet.row())
        assertTrue("agent-sam-tablet" !in membersOf(home.mark.groupId()!!).keys)
    }

    @Test
    fun pairingTheDeviceAgainLetsItJoinTheGroupAgain() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val (tablet, _) = home.withTabletInTheGroup()
        home.unpairTablet(tablet)
        home.mark.groups.reconcile()
        repeat(2) { tablet.groups.reconcile() }

        // Act: Sam pairs it again, which clears the mark
        home.sam.rosters.clearGone(tablet.agentId)
        sync(home.sam, home.mark)
        sync(home.mark, tablet)
        tablet.groups.reconcile()
        eventually { tablet.row()?.storeId != null }

        // Assert
        assertTrue("agent-sam-tablet" in membersOf(home.mark.groupId()!!).keys)
    }

    @Test
    fun anUnpairedDeviceThatCreatedTheGroupReplacesIt() = runBlocking {
        // Arrange: Mark's device made the group and Ann, another admin, is in it; Mark unpairs his own first device
        val home = Household().arrange()
        val ann = Device("agent-ann")
        enrol(home.mark, ann, "Ann", "admin")
        home.mark.groups.reconcile()
        sync(home.mark, home.sam)
        sync(home.mark, ann)
        home.sam.groups.reconcile()
        ann.groups.reconcile()
        eventually { home.sam.row()?.storeId != null && ann.row()?.storeId != null }
        home.mark.groups.reconcile() // promotes Ann
        val oldGroup = home.mark.groupId()!!

        // Act
        home.mark.rosters.markGone(home.mark.agentId)
        sync(home.mark, ann)
        ann.groups.reconcile()
        sync(ann, home.sam)
        home.sam.groups.reconcile()
        eventually { home.sam.row()?.groupId != null && home.sam.row()?.groupId != oldGroup && home.sam.row()?.storeId != null }

        // Assert
        val newGroup = ann.groupId()!!
        assertNotEquals(oldGroup, newGroup)
        assertEquals(setOf("agent-ann", "agent-sam"), membersOf(newGroup).keys)
    }

    // --- Invites are short-lived and made for one device (docs/plans/unpaired-device-gap-plan.md, slice 2b) ---

    @Test
    fun aDeviceIsInvitedWithAnInviteMadeForItAloneThatLapsesInAnHour() = runBlocking {
        // Arrange
        val home = Household().arrange()

        // Act
        val (tablet, _) = home.withTabletInTheGroup()

        // Assert: every invite the group's admin made went to one named device, and none lives a week
        val mine = network.minted.filter { it.mintedBy == "agent-mark" }
        assertTrue(mine.isNotEmpty())
        assertTrue(mine.all { it.expirySecs == 3600L && it.intendedJoiner != null })
        assertTrue(mine.any { it.intendedJoiner == tablet.agentId })
    }

    @Test
    fun anInviteMadeForTheTabletIsUselessToAnotherDevice() = runBlocking {
        // Arrange
        val home = Household().arrange()
        val (tablet, _) = home.withTabletInTheGroup()
        val invite = home.mark.node.createGroupInvite(home.mark.groupId()!!, expirySecs = 3600, intendedJoiner = tablet.agentId)

        // Act
        val refused = assertFailsWith<X0xApiException> { network.node("agent-other").joinGroup(invite) }

        // Assert
        assertEquals(409, refused.status)
        assertTrue("agent-other" !in membersOf(home.mark.groupId()!!).keys)
    }
}
