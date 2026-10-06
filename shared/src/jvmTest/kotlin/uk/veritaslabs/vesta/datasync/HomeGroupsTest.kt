package uk.veritaslabs.vesta.datasync

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
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.x0x.X0xApiException

/** A Home's x0x group (docs/adr/ADR-021-home-x0x-group.md) across devices on the fake mesh. */
class HomeGroupsTest {

    private val homeId = "home-1"
    private var clock = 100L
    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One device: its own database, roster and group service on the shared fake mesh, as the member [role]. */
    private inner class Device(val agentId: String) {
        val database: VestaDatabase = openInMemoryDatabase()
        val profiles = ProfileRepository(database)
        val rosters = HomeRosters(database, profiles, nowMillis = { clock++ })
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
}
