package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.storeEmptyPantry
import uk.veritaslabs.vesta.automerge.upsertPantryItem
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.MemberRepository
import uk.veritaslabs.vesta.home.RoleChangeResult
import uk.veritaslabs.vesta.ui.screens.MemberRole
import uk.veritaslabs.vesta.homejoin.InviteException
import uk.veritaslabs.vesta.homejoin.InviteFailure
import uk.veritaslabs.vesta.homejoin.JoinLink
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

class DeviceDataSyncServiceTest {

    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One of the user's devices: its own x0x node and database, with a running data sync. */
    private inner class Device(val agentId: String) {
        val node = network.node(agentId)
        val database: VestaDatabase = openInMemoryDatabase()
        val devices = SyncedDeviceRepository(database)
        val pantries = PantrySources(database)
        val sync = DeviceDataSyncService(
            node, database, devices, pantries, interval = 50.milliseconds,
            joinResendInterval = 100.milliseconds, joinTimeout = 1.seconds,
        )
        val homes = HomeRepository(database, sync.homeChanges)
        val profiles = ProfileRepository(database)

        init {
            node.onDirect = { message: X0xDirectMessage -> scope.launch { sync.handle(message) } }
        }

        fun start() {
            scope.launch { sync.run() }
        }

        suspend fun pairedWith(other: Device) = apply { devices.add(other.agentId, other.agentId) }

        /** Gives this device a copy of a Home another person made, as joining it will: the Home row and an empty Pantry to fill. */
        suspend fun joinedHome(id: String, name: String, shape: HomeShape) {
            database.homeQueries.insert(id, name, shape.storageValue, 1L)
            storeEmptyPantry(database, id)
        }

        suspend fun profileIds() = database.profileQueries.selectAll().awaitAsList().map { it.id }

        suspend fun roleOf(homeId: String, memberId: String) =
            database.homeMemberQueries.selectByHome(homeId).awaitAsList().firstOrNull { it.memberId == memberId }?.role

        suspend fun memberIds(homeId: String) =
            database.homeMemberQueries.selectByHome(homeId).awaitAsList().map { it.memberId }

        suspend fun homeNames() = database.homeQueries.selectAll().awaitAsList().map { it.name }.sorted()

        suspend fun pantryTitles(homeId: String) =
            pantries.forHome(homeId)!!.contents.first().items.map { it.title }.sorted()
    }

    private suspend fun pair(): Pair<Device, Device> {
        val a = Device("agent-a")
        val b = Device("agent-b")
        a.pairedWith(b)
        b.pairedWith(a)
        return a to b
    }

    private suspend fun eventually(check: suspend () -> Boolean) {
        withTimeout(10.seconds) { while (!check()) delay(20.milliseconds) }
    }

    @Test
    fun aHomeCreatedOnOneDeviceAppearsOnTheOther() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()

        // Act
        val id = a.homes.create("Maple Street", HomeShape.Family)

        // Assert
        eventually { b.homeNames() == listOf("Maple Street") }
        assertEquals(id, b.database.homeQueries.selectAll().awaitAsList().single().id)
        scope.cancel()
    }

    @Test
    fun aHomeThatExistedBeforePairingIsSyncedOnceDevicesAreRunning() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.homes.create("Old Home", HomeShape.Flatshare)

        // Act
        a.start()
        b.start()

        // Assert
        eventually { b.homeNames() == listOf("Old Home") }
        scope.cancel()
    }

    @Test
    fun pantryItemsAddedOnEitherDeviceReachBoth() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames().isNotEmpty() }

        // Act
        a.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null) }
        eventually { b.pantryTitles(homeId) == listOf("Whole Milk") }
        b.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Meat", null, "Beef Mince", "500g", barcode = null) }

        // Assert
        eventually { a.pantryTitles(homeId) == listOf("Beef Mince", "Whole Milk") }
        assertEquals(listOf("Beef Mince", "Whole Milk"), b.pantryTitles(homeId))
        scope.cancel()
    }

    @Test
    fun renamingAndDeletingAHomeReachesTheOtherDevice() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val id = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames() == listOf("Maple Street") }

        // Act
        b.homes.update(id, "Oak Avenue", HomeShape.Family)
        eventually { a.homeNames() == listOf("Oak Avenue") }
        a.homes.delete(id)

        // Assert
        eventually { b.homeNames().isEmpty() }
        assertTrue(b.database.automergeDocumentQueries.selectByHome(id).awaitAsList().isEmpty())
        scope.cancel()
    }

    @Test
    fun aDeviceThatIsNotPairedIsIgnored() = runBlocking {
        // Arrange
        val a = Device("agent-a")
        val stranger = Device("agent-stranger")
        stranger.pairedWith(a)
        a.start()
        stranger.start()

        // Act
        stranger.homes.create("Not Yours", HomeShape.Other)
        delay(500.milliseconds)

        // Assert
        assertEquals(emptyList(), a.homeNames())
        scope.cancel()
    }

    @Test
    fun aDeviceThatWasOfflineCatchesUpWhenItReturns() = runBlocking {
        // Arrange
        val (a, b) = pair()
        network.offline += b.agentId
        a.start()
        b.start()

        // Act
        a.homes.create("While Away", HomeShape.Family)
        delay(300.milliseconds)
        network.offline -= b.agentId

        // Assert
        eventually { b.homeNames() == listOf("While Away") }
        scope.cancel()
    }

    @Test
    fun pairedDevicesSettleOnTheOlderDevicesProfileAndMoveTheirMembershipsToIt() = runBlocking {
        // Arrange
        val (a, b) = pair()
        network.offline += b.agentId // b is not reachable until it starts, so it makes its own Profile first
        a.start()
        eventually { a.profileIds().isNotEmpty() }
        val aProfile = a.profileIds().single()
        delay(20.milliseconds) // so b's Profile is unambiguously the younger
        val bHome = b.homes.create("Made On B", HomeShape.Other)
        val bProfileBefore = b.profileIds().single()

        // Act
        network.offline -= b.agentId
        b.start()

        // Assert
        eventually { b.profileIds() == listOf(aProfile) }
        eventually { b.memberIds(bHome) == listOf(aProfile) }
        assertEquals(listOf(aProfile), a.profileIds())
        assertTrue(bProfileBefore != aProfile)
        scope.cancel()
    }

    @Test
    fun aMembersDevicesAreOneMemberOnBothDevicesAndBothAreInTheirRoster() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        val aProfile = a.profileIds().single()
        delay(20.milliseconds)

        // Act
        b.start()

        // Assert
        eventually { b.memberIds(homeId) == listOf(aProfile) }
        eventually { a.sync.rosterDevices(homeId) == mapOf(aProfile to setOf("agent-a", "agent-b")) }
        assertEquals(listOf(aProfile), a.memberIds(homeId))
        scope.cancel()
    }

    @Test
    fun aHomeMadeBeforeASecondDeviceTookOnTheSharedProfileEndsUpWithOneMember() = runBlocking {
        // Arrange
        val (a, b) = pair()
        network.offline += b.agentId
        a.start()
        eventually { a.profileIds().isNotEmpty() }
        val aProfile = a.profileIds().single()
        delay(20.milliseconds)
        val bHome = b.homes.create("Made On B", HomeShape.Other)

        // Act
        network.offline -= b.agentId
        b.start()

        // Assert
        eventually { a.homeNames() == listOf("Made On B") }
        eventually { a.memberIds(bHome) == listOf(aProfile) && b.memberIds(bHome) == listOf(aProfile) }
        eventually { b.sync.rosterDevices(bHome) == mapOf(aProfile to setOf("agent-a", "agent-b")) }
        scope.cancel()
    }

    @Test
    fun removingADeviceDropsItFromTheRosterButTheMemberStays() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        val aProfile = a.profileIds().single()
        delay(20.milliseconds)
        b.start()
        eventually { a.sync.rosterDevices(homeId) == mapOf(aProfile to setOf("agent-a", "agent-b")) }

        // Act
        a.devices.remove(b.agentId)

        // Assert
        eventually { a.sync.rosterDevices(homeId) == mapOf(aProfile to setOf("agent-a")) }
        assertEquals(listOf(aProfile), a.memberIds(homeId))
        scope.cancel()
    }

    /** Two people who are not paired: X made the Home and admitted Y; Y has the Home and knows X invited it. */
    private suspend fun twoPeopleInOneHome(): Triple<Device, Device, String> {
        val x = Device("agent-x")
        val y = Device("agent-y")
        val homeId = x.homes.create("Maple Street", HomeShape.Family)
        val xMember = x.profiles.current()
        val yMember = y.profiles.current()
        y.joinedHome(homeId, "Maple Street", HomeShape.Family)
        x.sync.admit(homeId, yMember.id, yMember.displayName, "member", y.agentId)
        y.sync.admit(homeId, xMember.id, xMember.displayName, "admin", x.agentId)
        return Triple(x, y, homeId)
    }

    @Test
    fun membersOfAHomeWhoAreNotPairedSyncItsPantry() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()

        // Act
        x.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null) }
        eventually { y.pantryTitles(homeId) == listOf("Whole Milk") }
        y.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Meat", null, "Beef Mince", "500g", barcode = null) }

        // Assert
        eventually { x.pantryTitles(homeId) == listOf("Beef Mince", "Whole Milk") }
        scope.cancel()
    }

    @Test
    fun aHomeSyncsItsMembersButNotTheRestOfEachOthersData() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()

        // Act
        val private = x.homes.create("X Only", HomeShape.Other)
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }
        delay(300.milliseconds)

        // Assert
        assertEquals(listOf("Maple Street"), y.homeNames())
        assertEquals(2, x.homeNames().size)
        assertTrue(private !in y.database.homeQueries.selectAll().awaitAsList().map { it.id })
        scope.cancel()
    }

    @Test
    fun theRosterShowsBothPeopleOnBothDevices() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()

        // Act
        x.start()
        y.start()

        // Assert
        eventually { x.memberIds(homeId).size == 2 && y.memberIds(homeId).size == 2 }
        assertEquals(x.memberIds(homeId).sorted(), y.memberIds(homeId).sorted())
        scope.cancel()
    }

    @Test
    fun aRemovedMemberNoLongerSyncsTheHome() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        x.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null) }
        eventually { y.pantryTitles(homeId) == listOf("Whole Milk") }
        val yMember = y.profiles.current().id

        // Act
        x.sync.removeMember(homeId, yMember)
        y.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Meat", null, "Beef Mince", "500g", barcode = null) }
        delay(500.milliseconds)

        // Assert
        assertEquals(listOf("Whole Milk"), x.pantryTitles(homeId))
        assertEquals(listOf(x.profiles.current().id), x.memberIds(homeId))
        scope.cancel()
    }

    @Test
    fun aDeviceThatIsNotInTheRosterCannotSyncAHome() = runBlocking {
        // Arrange
        val (x, _, homeId) = twoPeopleInOneHome()
        val stranger = Device("agent-stranger")
        stranger.joinedHome(homeId, "Maple Street", HomeShape.Family)
        x.start()
        stranger.start()

        // Act
        stranger.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Meat", null, "Beef Mince", "500g", barcode = null) }
        delay(500.milliseconds)

        // Assert
        assertEquals(emptyList(), x.pantryTitles(homeId))
        scope.cancel()
    }

    @Test
    fun aPersonWhoShowsAJoinLinkIsInvitedIntoAHomeAndSyncsIt() = runBlocking {
        // Arrange
        val x = Device("agent-x")
        val y = Device("agent-y")
        val homeId = x.homes.create("Maple Street", HomeShape.Family)
        x.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null) }
        x.start()
        y.start()
        val link = y.sync.joining.openLink()

        // Act
        val result = x.sync.joining.invite(homeId, link)

        // Assert
        assertTrue(result.isSuccess)
        eventually { y.homeNames() == listOf("Maple Street") }
        eventually { x.memberIds(homeId).size == 2 && y.memberIds(homeId).size == 2 }
        assertEquals(x.memberIds(homeId).sorted(), y.memberIds(homeId).sorted())
        eventually { y.pantryTitles(homeId) == listOf("Whole Milk") }
        scope.cancel()
    }

    @Test
    fun aDeviceThatIsNotShowingAJoinLinkIgnoresAnInvitation() = runBlocking {
        // Arrange
        val x = Device("agent-x")
        val y = Device("agent-y")
        val homeId = x.homes.create("Maple Street", HomeShape.Family)
        x.start()
        y.start()
        val link = y.sync.joining.openLink()
        y.sync.joining.cancelLink()

        // Act
        val result = x.sync.joining.invite(homeId, link)

        // Assert
        assertEquals(InviteFailure.NoResponse, (result.exceptionOrNull() as InviteException).reason)
        assertEquals(emptyList(), y.homeNames())
        assertEquals(1, x.memberIds(homeId).size)
        scope.cancel()
    }

    @Test
    fun onlyAnAdminCanInvite() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        val newcomer = Device("agent-new")
        x.start()
        y.start()
        newcomer.start()
        val link = newcomer.sync.joining.openLink()

        // Act
        val result = y.sync.joining.invite(homeId, link) // Y is an ordinary member

        // Assert
        assertEquals(InviteFailure.NotAnAdmin, (result.exceptionOrNull() as InviteException).reason)
        scope.cancel()
    }

    @Test
    fun invitingAnExistingMemberFails() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        val link = y.sync.joining.openLink()

        // Act
        val result = x.sync.joining.invite(homeId, link)

        // Assert
        assertEquals(InviteFailure.AlreadyAMember, (result.exceptionOrNull() as InviteException).reason)
        scope.cancel()
    }

    @Test
    fun anExpiredLinkCannotBeUsed() = runBlocking {
        // Arrange
        val x = Device("agent-x")
        val homeId = x.homes.create("Maple Street", HomeShape.Family)
        val expired = JoinLink("agent-y", "member-y", expiresAt = 0L, name = "Sam")

        // Act
        val result = x.sync.joining.invite(homeId, expired)

        // Assert
        assertEquals(InviteFailure.LinkExpired, (result.exceptionOrNull() as InviteException).reason)
        scope.cancel()
    }

    @Test
    fun anAdminsPromotionOfAMemberReachesTheMembersOwnDevice() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }
        val members = MemberRepository(x.database, x.profiles, x.sync.rosterChanges)

        // Act
        val result = members.setRole(homeId, y.profiles.current().id, MemberRole.Admin)

        // Assert
        assertEquals(RoleChangeResult.Changed, result)
        eventually { y.roleOf(homeId, y.profiles.current().id) == "admin" }
        assertEquals("admin", x.roleOf(homeId, y.profiles.current().id))
        scope.cancel()
    }
}
