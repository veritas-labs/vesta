package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.encoding.Base64
import uk.veritaslabs.vesta.automerge.CalendarSources
import uk.veritaslabs.vesta.automerge.privateCalendarDocumentId
import uk.veritaslabs.vesta.automerge.calendarDocumentId
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.vesta.automerge.MealPlanSources
import uk.veritaslabs.vesta.automerge.MerchantsSources
import uk.veritaslabs.vesta.ui.screens.Merchant
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.automerge.storeEmptyPantry
import uk.veritaslabs.vesta.automerge.upsertPantryItem
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicepairing.PairingLink
import uk.veritaslabs.vesta.devicesync.DeviceSyncService
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.MemberRepository
import uk.veritaslabs.vesta.home.RoleChangeResult
import uk.veritaslabs.vesta.ui.screens.MemberRole
import uk.veritaslabs.vesta.home.LeaveResult
import uk.veritaslabs.vesta.home.RemoveMemberResult
import uk.veritaslabs.vesta.homejoin.HomeJoinMessage
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.shoppingListDocumentId
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.storeEmptyShoppingList
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.screens.MealSlot
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

class DeviceDataSyncServiceTest {

    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One of the user's devices: its own x0x node and database, with a running data sync. */
    private inner class Device(val agentId: String, interval: Duration = 50.milliseconds) {
        val node = network.node(agentId)
        val database: VestaDatabase = openInMemoryDatabase()
        val devices = SyncedDeviceRepository(database)
        val pantries = PantrySources(database)
        val shopping = ShoppingListSources(database, pantries)
        val cookbook = CookbookSources(database)
        val merchants = MerchantsSources(database)
        val mealPlans = MealPlanSources(database)
        val calendars = CalendarSources(database)
        /** The id of every document a sync message has reached this device for. */
        val receivedDocuments = ConcurrentLinkedQueue<String>()
        val sync = DeviceDataSyncService(
            node, database, devices, pantries, shopping, cookbook, merchants, mealPlans, calendars, interval = interval,
        )
        /** The pairing service, for the tests that unpair a device; it only handles messages once [routeDeviceSyncMessages] is set. */
        val deviceSync: DeviceSyncService by lazy {
            DeviceSyncService(
                client = node, database = database, devices = devices, deviceName = { agentId },
                pollInterval = 1.milliseconds, joinTimeout = 5.seconds, ownViewTimeout = 20.milliseconds,
                resendInterval = 5.milliseconds, removeRetryDelay = 1.milliseconds,
                onDeviceUnpaired = sync::deviceUnpaired, onDevicePaired = sync::devicePaired,
            )
        }
        var routeDeviceSyncMessages = false
        val homes = HomeRepository(database, sync.homeChanges)
        val profiles = ProfileRepository(database, changes = sync.profileChanges)

        init {
            node.onDirect = { message: X0xDirectMessage ->
                message.payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() }?.let { SyncEnvelope.decode(it) }
                    ?.let { receivedDocuments.add(it.documentId) }
                scope.launch {
                    if (routeDeviceSyncMessages) deviceSync.handle(message)
                    sync.handle(message)
                }
            }
        }

        fun start() {
            scope.launch { sync.run() }
        }

        suspend fun pairedWith(other: Device) = apply { devices.add(other.agentId, other.agentId) }

        /** Gives this device a copy of a Home another person made, as joining it will: the Home row and an empty Pantry to fill. */
        suspend fun joinedHome(id: String, name: String, shape: HomeShape) {
            database.homeQueries.insert(id, name, shape.storageValue, 1L)
            storeEmptyPantry(database, id)
            storeEmptyShoppingList(database, id)
        }

        suspend fun profileIds() = database.profileQueries.selectAll().awaitAsList().map { it.id }

        suspend fun roleOf(homeId: String, memberId: String) =
            database.homeMemberQueries.selectByHome(homeId).awaitAsList().firstOrNull { it.memberId == memberId }?.role

        suspend fun memberIds(homeId: String) =
            database.homeMemberQueries.selectByHome(homeId).awaitAsList().map { it.memberId }

        suspend fun homeNames() = database.homeQueries.selectAll().awaitAsList().map { it.name }.sorted()

        suspend fun shoppingTitles(homeId: String) =
            shopping.forHome(homeId)!!.items.first().map { it.title }.sorted()

        suspend fun recipeTitles(homeId: String) =
            cookbook.forHome(homeId)!!.recipes.first().map { it.title }.sorted()

        suspend fun plannedDinners(homeId: String, date: CalendarDate) =
            mealPlans.forHome(homeId)!!.plan.first().recipes(date, MealSlot.Dinner).sorted()

        suspend fun homeEventTitles(homeId: String) =
            calendars.forHome(homeId)!!.events.first().map { it.title }.sorted()

        suspend fun privateEventTitles(homeId: String) = calendars.privateForHome(homeId)!!.events.first().map { it.title }.sorted()

        suspend fun merchantNames(homeId: String) =
            merchants.forHome(homeId)!!.merchants.first().map { it.name }.sorted()

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
    fun aPeerThatHasSyncedReportsWhenItLastDidAndAStrangerDoesNot() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames() == listOf("Maple Street") }
        eventually { a.sync.agentDetails(homeId, "agent-b").name != null } // b's roster entry reaches a a round later

        // Act
        val synced = a.sync.agentDetails(homeId, "agent-b")
        val stranger = a.sync.agentDetails(homeId, "agent-z")

        // Assert
        assertTrue(synced.lastSyncedMsAgo != null && synced.lastSyncedMsAgo!! < 10_000)
        assertTrue(synced.name != null) // learnt from the roster, which agent-b wrote
        assertEquals(null, stranger.name)
        assertEquals(null, stranger.lastSyncedMsAgo)
        assertEquals(null, stranger.machineId)
        assertEquals(null, stranger.lastSeenMsAgo)
        scope.cancel()
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
    fun shoppingListItemsAddedOnEitherDeviceReachBothAndLandInTheirPantries() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames().isNotEmpty() }

        // Act
        a.shopping.forHome(homeId)!!.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk"))
        eventually { b.shoppingTitles(homeId) == listOf("Whole Milk") }
        b.shopping.forHome(homeId)!!.save(ShoppingItemDetails(primaryCategory = "Meat", title = "Beef Mince"))

        // Assert
        eventually { a.shoppingTitles(homeId) == listOf("Beef Mince", "Whole Milk") }
        eventually { a.pantryTitles(homeId) == listOf("Beef Mince", "Whole Milk") }
        eventually { b.pantryTitles(homeId) == listOf("Beef Mince", "Whole Milk") }
        scope.cancel()
    }

    @Test
    fun recipesAddedOnEitherDeviceReachBoth() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames().isNotEmpty() }

        // Act
        a.cookbook.forHome(homeId)!!.save(Recipe(title = "Shepherd's Pie"))
        eventually { b.recipeTitles(homeId) == listOf("Shepherd's Pie") }
        b.cookbook.forHome(homeId)!!.save(Recipe(title = "Apple Crumble"))

        // Assert
        eventually { a.recipeTitles(homeId) == listOf("Apple Crumble", "Shepherd's Pie") }
        scope.cancel()
    }

    @Test
    fun plannedMealsAddedOnEitherDeviceReachBoth() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames().isNotEmpty() }
        val tuesday = CalendarDate(2026, 10, 13)

        // Act
        a.mealPlans.forHome(homeId)!!.add(tuesday, MealSlot.Dinner, "pie")
        eventually { b.plannedDinners(homeId, tuesday) == listOf("pie") }
        b.mealPlans.forHome(homeId)!!.add(tuesday, MealSlot.Dinner, "curry")

        // Assert
        eventually { a.plannedDinners(homeId, tuesday) == listOf("curry", "pie") }
        scope.cancel()
    }


    private fun event(id: String, title: String, calendar: EventCalendar) =
        CalendarEvent(id = id, calendar = calendar, title = title, allDay = true, startMillis = 0L, endMillis = 0L)

    @Test
    fun aHomesCalendarEventsReachItsOtherMembers() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()

        // Act
        x.calendars.forHome(homeId)!!.save(event("e1", "Bin day", EventCalendar.Home))
        eventually { y.homeEventTitles(homeId) == listOf("Bin day") }
        y.calendars.forHome(homeId)!!.save(event("e2", "Dentist", EventCalendar.Home))

        // Assert
        eventually { x.homeEventTitles(homeId) == listOf("Bin day", "Dentist") }
        scope.cancel()
    }

    @Test
    fun aPrivateCalendarReachesTheUsersOwnDevicesAndNeverAnotherMemberOfTheHome() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        val x2 = Device("agent-x2")
        x.pairedWith(x2)
        x2.pairedWith(x)
        x.start()
        x2.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 }

        // Act
        x.calendars.privateForHome(homeId)!!.save(event("p1", "Surprise party planning", EventCalendar.Private))
        x.calendars.forHome(homeId)!!.save(event("e1", "Bin day", EventCalendar.Home))
        eventually { x2.privateEventTitles(homeId) == listOf("Surprise party planning") }
        eventually { y.homeEventTitles(homeId) == listOf("Bin day") }
        delay(500.milliseconds) // long enough for several sync rounds to have passed

        // Assert
        assertTrue(privateCalendarDocumentId(homeId) !in y.receivedDocuments)
        assertEquals(emptyList(), y.privateEventTitles(homeId))
        assertTrue(calendarDocumentId(homeId) in y.receivedDocuments)
        scope.cancel()
    }

    @Test
    fun aPrivateCalendarEventEditedOnOneDeviceUpdatesTheOther() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames().isNotEmpty() }
        a.calendars.privateForHome(homeId)!!.save(event("p1", "Gym", EventCalendar.Private))
        eventually { b.privateEventTitles(homeId) == listOf("Gym") }

        // Act
        b.calendars.privateForHome(homeId)!!.save(event("p1", "Gym session", EventCalendar.Private))

        // Assert
        eventually { a.privateEventTitles(homeId) == listOf("Gym session") }
        scope.cancel()
    }

    @Test
    fun merchantsAddedOnEitherDeviceReachBoth() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family)
        eventually { b.homeNames().isNotEmpty() }

        // Act
        a.merchants.forHome(homeId)!!.save(Merchant(name = "Hargreaves"))
        eventually { b.merchantNames(homeId) == listOf("Hargreaves") }
        b.merchants.forHome(homeId)!!.save(Merchant(name = "Aldi"))

        // Assert
        eventually { a.merchantNames(homeId) == listOf("Aldi", "Hargreaves") }
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
    fun aDeviceThatComesOnlineIsSyncedAtOnceNotAtTheNextRound() = runBlocking {
        // Arrange: the timer is an hour, so only the presence event can deliver the Home in time
        val a = Device("agent-a", interval = 1.hours)
        val b = Device("agent-b", interval = 1.hours)
        a.pairedWith(b)
        b.pairedWith(a)
        network.offline += b.agentId
        a.start()
        b.start()
        delay(300.milliseconds) // both have run their first round and opened their presence streams
        a.homes.create("Maple Street", HomeShape.Family)
        delay(300.milliseconds)
        assertTrue(b.homeNames().isEmpty())

        // Act
        network.comeOnline(b.agentId)

        // Assert
        eventually { b.homeNames() == listOf("Maple Street") }
        scope.cancel()
    }

    @Test
    fun aSyncRequestedWhenTheAppReturnsToTheForegroundRunsARoundNow() = runBlocking {
        // Arrange
        val a = Device("agent-a", interval = 1.hours)
        val b = Device("agent-b", interval = 1.hours)
        a.pairedWith(b)
        b.pairedWith(a)
        network.offline += b.agentId
        a.start()
        b.start()
        delay(300.milliseconds)
        a.homes.create("Maple Street", HomeShape.Family)
        delay(300.milliseconds)
        network.offline -= b.agentId // back, but no presence event arrives

        // Act
        a.sync.syncNow()

        // Assert
        eventually { b.homeNames() == listOf("Maple Street") }
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
    fun membersOfAHomeWhoAreNotPairedShareItsShoppingList() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()

        // Act
        x.shopping.forHome(homeId)!!.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk"))
        eventually { y.shoppingTitles(homeId) == listOf("Whole Milk") }
        y.shopping.forHome(homeId)!!.save(ShoppingItemDetails(primaryCategory = "Meat", title = "Beef Mince"))

        // Assert
        eventually { x.shoppingTitles(homeId) == listOf("Beef Mince", "Whole Milk") }
        scope.cancel()
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
    fun aPersonWhoOpensAnInviteLinkIsApprovedIntoAHomeAndSyncsIt() = runBlocking {
        // Arrange
        val x = Device("agent-x")
        val y = Device("agent-y")
        val homeId = x.homes.create("Maple Street", HomeShape.Family)
        x.pantries.forHome(homeId)!!.mutate { upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null) }
        x.start()
        y.start()
        val link = x.sync.joining.createInviteLink(homeId)!!

        // Act
        y.sync.joining.redeemInvite(link)
        eventually { x.sync.joining.joinRequests(homeId).isNotEmpty() }
        x.sync.joining.approve(homeId, y.profiles.current().id)

        // Assert
        eventually { y.homeNames() == listOf("Maple Street") }
        eventually { x.memberIds(homeId).size == 2 && y.memberIds(homeId).size == 2 }
        assertEquals(x.memberIds(homeId).sorted(), y.memberIds(homeId).sorted())
        eventually { y.pantryTitles(homeId) == listOf("Whole Milk") }
        scope.cancel()
    }

    @Test
    fun aJoinerAnswersAgainWhenTheInviterRepeatsAnInvitationItAlreadyAccepted() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }
        val xMember = x.profiles.current()
        val invite = HomeJoinMessage.encode(HomeJoinMessage.Invite(homeId, "Maple Street", "family", 1L, xMember.id, xMember.displayName))
        y.node.sentTo.clear()

        // Act
        x.node.sendDirect(y.agentId, invite)

        // Assert
        eventually { x.agentId in y.node.sentTo }
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

    @Test
    fun aMemberRemovedByAnAdminLosesTheHomeOnTheirDevice() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }
        val members = MemberRepository(x.database, x.profiles, x.sync.rosterChanges)

        // Act
        val result = members.removeMember(homeId, y.profiles.current().id)

        // Assert
        assertEquals(RemoveMemberResult.Removed, result)
        eventually { y.homeNames().isEmpty() }
        assertEquals(listOf(x.profiles.current().id), x.memberIds(homeId))
        assertEquals(mapOf(x.profiles.current().id to setOf("agent-x")), x.sync.rosterDevices(homeId))
        scope.cancel()
    }

    @Test
    fun aMemberWhoLeavesIsDroppedFromTheOthersRoster() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }
        val leaver = HomeRepository(y.database, y.sync.homeChanges, y.profiles, y.sync.rosterChanges)

        // Act
        val result = leaver.leave(homeId)

        // Assert
        assertEquals(LeaveResult.Left, result)
        eventually { x.memberIds(homeId) == listOf(x.profiles.current().id) }
        assertEquals(emptyList(), y.homeNames())
        scope.cancel()
    }

    @Test
    fun aMemberRemovedWhileTheirDeviceIsOfflineLosesTheHomeWhenItIsBack() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }
        val members = MemberRepository(x.database, x.profiles, x.sync.rosterChanges)
        network.offline += y.agentId

        // Act
        members.removeMember(homeId, y.profiles.current().id)
        delay(300.milliseconds)
        assertEquals(listOf("Maple Street"), y.homeNames())
        network.offline -= y.agentId

        // Assert
        eventually { y.homeNames().isEmpty() }
        eventually { x.database.pendingMessageQueries.selectAll().awaitAsList().isEmpty() }
        scope.cancel()
    }

    @Test
    fun aDepartureIsToldToADeviceThatWasOfflineWhenTheMemberLeft() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }
        val leaver = HomeRepository(y.database, y.sync.homeChanges, y.profiles, y.sync.rosterChanges)
        network.offline += x.agentId

        // Act
        leaver.leave(homeId)
        delay(300.milliseconds)
        assertEquals(2, x.memberIds(homeId).size)
        network.offline -= x.agentId

        // Assert
        eventually { x.memberIds(homeId) == listOf(x.profiles.current().id) }
        eventually { y.database.pendingMessageQueries.selectAll().awaitAsList().isEmpty() }
        scope.cancel()
    }

    @Test
    fun aMessageNobodyAcknowledgesIsGivenUpOnAfterItsLifetime() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        var now = 1_000L
        val join = HomeJoinService(
            x.node, x.database, HomeRosters(x.database, x.profiles), x.sync.homeChanges, x.profiles,
            nowMillis = { now }, messageLifetime = 10.seconds,
        )
        network.offline += y.agentId
        join.removeMember(homeId, y.profiles.current().id)

        // Act
        now += 5_000L
        join.resendPending()
        val before = x.database.pendingMessageQueries.selectAll().awaitAsList().size
        now += 6_000L
        join.resendPending()

        // Assert
        assertEquals(1, before)
        assertEquals(0, x.database.pendingMessageQueries.selectAll().awaitAsList().size)
        scope.cancel()
    }

    @Test
    fun anOrdinaryMemberCannotRemoveAnotherMemberBySendingTheMessageThemselves() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }

        // Act: Y is an ordinary member, and says X has been removed from Home
        y.node.sendDirect(x.agentId, HomeJoinMessage.encode(HomeJoinMessage.Removed(homeId)))
        delay(300.milliseconds)

        // Assert
        assertEquals(listOf("Maple Street"), x.homeNames())
        scope.cancel()
    }

    @Test
    fun aMemberCannotBeDroppedByADeviceThatIsNotTheirs() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }

        // Act: Y claims X has left
        y.node.sendDirect(x.agentId, HomeJoinMessage.encode(HomeJoinMessage.Left(homeId, x.profiles.current().id)))
        delay(300.milliseconds)

        // Assert
        assertEquals(2, x.memberIds(homeId).size)
        scope.cancel()
    }

    @Test
    fun aNameChosenOnOneDeviceReachesTheirOtherDevice() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        eventually { a.profileIds().isNotEmpty() }
        delay(20.milliseconds)
        b.start()
        eventually { b.profileIds() == a.profileIds() }

        // Act
        a.profiles.rename("Mark")

        // Assert
        eventually { b.profiles.current().displayName == "Mark" && b.profiles.current().named }
        scope.cancel()
    }

    @Test
    fun aNameChosenBeforePairingBeatsTheOlderDevicesPlaceholder() = runBlocking {
        // Arrange
        val (a, b) = pair()
        network.offline += b.agentId
        a.start()
        eventually { a.profileIds().isNotEmpty() }
        delay(20.milliseconds)
        b.profiles.rename("Mark")
        val bProfile = b.profileIds().single()

        // Act
        network.offline -= b.agentId
        b.start()

        // Assert
        eventually { a.profileIds() == listOf(bProfile) }
        assertEquals("Mark", a.profiles.current().displayName)
        assertEquals(listOf(bProfile), b.profileIds())
        scope.cancel()
    }

    @Test
    fun aMembersNewNameReachesTheOtherMembersOfAHome() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        eventually { y.memberIds(homeId).size == 2 && x.memberIds(homeId).size == 2 }
        val xMember = x.profiles.current().id

        // Act
        x.profiles.rename("Xavier")

        // Assert
        eventually { y.database.homeMemberQueries.selectByHome(homeId).awaitAsList().first { it.memberId == xMember }.name == "Xavier" }
        scope.cancel()
    }

    // --- The mailbox (ADR-020) ---

    /** Waits until both devices of [homeId] are in the Home's group with its mailbox store open, then adds an always-on hub to it. */
    private suspend fun withHub(x: Device, y: Device, homeId: String): String {
        eventually { x.mailboxStore(homeId) != null && y.mailboxStore(homeId) != null }
        val groupId = x.database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()!!.groupId
        val hub = network.node("agent-hub")
        hub.joinGroup(x.node.createGroupInvite(groupId))
        hub.openGroupStore(groupId, "mailbox")
        return groupId
    }

    private suspend fun Device.mailboxStore(homeId: String) = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.storeId

    /** Whether everything this device has in [homeId]'s Pantry is already in the mailbox. */
    private suspend fun Device.pantryDeposited(homeId: String): Boolean {
        val heads = pantries.forHome(homeId)!!.heads()
        return database.mailboxQueries.selectHeads(homeId, pantryDocumentId(homeId)).awaitAsOneOrNull()?.contentEquals(heads) == true
    }

    /** Whether everything this device has in [homeId]'s shopping list is already in the mailbox. */
    private suspend fun Device.shoppingDeposited(homeId: String): Boolean {
        val heads = shopping.forHome(homeId)!!.heads()
        return database.mailboxQueries.selectHeads(homeId, shoppingListDocumentId(homeId)).awaitAsOneOrNull()?.contentEquals(heads) == true
    }

    @Test
    fun twoMembersWhoAreNeverOnlineTogetherStillSyncThroughTheHub() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        withHub(x, y, homeId)
        network.offline += y.agentId

        // Act: X edits while Y is away, and is itself gone by the time Y is back, so no direct message can carry it
        x.shopping.forHome(homeId)!!.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk"))
        eventually { x.shoppingDeposited(homeId) }
        network.offline += x.agentId
        network.comeOnline(y.agentId)

        // Assert
        eventually { y.shoppingTitles(homeId) == listOf("Whole Milk") }
        scope.cancel()
    }

    @Test
    fun aChangeMadeWhileTheOtherMemberIsAwayReachesThemWhenTheirAppIsOpenedAgain() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        withHub(x, y, homeId)
        network.offline += x.agentId

        // Act
        y.pantries.forHome(homeId)!!.save(PantryItemDetails(primaryCategory = "Meat", title = "Beef Mince", packageSize = "500g"))
        eventually { y.pantryDeposited(homeId) && y.pantryTitles(homeId) == listOf("Beef Mince") }
        network.offline += y.agentId
        network.comeOnline(x.agentId)
        x.sync.syncNow()

        // Assert
        eventually { x.pantryTitles(homeId) == listOf("Beef Mince") }
        scope.cancel()
    }

    @Test
    fun aMemberWithNoGroupYetIsUnaffectedAndStillSyncsByDirectMessage() = runBlocking {
        // Arrange: the creator is not running, so nobody can invite Y into a group
        val (x, y, homeId) = twoPeopleInOneHome()
        y.start()
        x.start()
        network.offline += x.agentId

        // Act
        y.pantries.forHome(homeId)!!.save(PantryItemDetails(primaryCategory = "Meat", title = "Beef Mince", packageSize = "500g"))
        delay(300.milliseconds)
        network.comeOnline(x.agentId)

        // Assert: the direct-message path still delivers it
        eventually { x.pantryTitles(homeId) == listOf("Beef Mince") }
        scope.cancel()
    }

    // --- Peers that do not answer (ADR-020 by-hand run: an offline peer delayed the mailbox by minutes) ---

    /** Waits until [check] holds, but for no longer than [within]; false if it never did. */
    private suspend fun within(within: Duration, check: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(within) { while (!check()) delay(20.milliseconds); true } ?: false

    @Test
    fun aChangeIsDepositedWithoutWaitingForAPeerThatDoesNotAnswer() = runBlocking {
        // Arrange: Y's path has gone stale, so every direct message to it waits before failing
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        withHub(x, y, homeId)
        network.hangFor = 3.seconds
        network.hanging += y.agentId

        // Act
        x.pantries.forHome(homeId)!!.save(PantryItemDetails(primaryCategory = "Meat", title = "Beef Mince", packageSize = "500g"))

        // Assert: the mailbox has it long before one send to Y has timed out
        assertTrue(within(1500.milliseconds) { x.pantryDeposited(homeId) })
        scope.cancel()
    }

    @Test
    fun aPeerThatDidNotAnswerIsTriedOnceNotOncePerDocument() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        network.hangFor = 100.milliseconds
        network.hanging += y.agentId

        // Act: one full round, which has the Home's Pantry, shopping list and roster to send
        x.sync.syncEverything()
        val first = x.node.sentTo.count { it == y.agentId }
        x.sync.syncEverything()

        // Assert: one failed send is enough to leave Y alone for a while, in this round and the next
        assertEquals(1, first)
        assertEquals(1, x.node.sentTo.count { it == y.agentId })
        scope.cancel()
    }

    @Test
    fun aPeerThatCameBackIsSyncedAtOnceEvenAfterItWasGivenUpOn() = runBlocking {
        // Arrange: X gives up on Y after a failed send, and then edits
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        network.hangFor = 100.milliseconds
        network.hanging += y.agentId
        x.sync.syncEverything()
        x.pantries.forHome(homeId)!!.save(PantryItemDetails(primaryCategory = "Meat", title = "Beef Mince", packageSize = "500g"))

        // Act: Y comes back; X sees it come online
        network.hanging -= y.agentId
        network.comeOnline(y.agentId)

        // Assert
        assertTrue(within(5.seconds) { y.pantryTitles(homeId) == listOf("Beef Mince") })
        scope.cancel()
    }

    @Test
    fun aDeviceThatIsOpenedReadsTheMailboxWithoutWaitingForPeersThatDoNotAnswer() = runBlocking {
        // Arrange: X leaves a change in the mailbox and is gone; Y is then opened, with X's path stale
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        withHub(x, y, homeId)
        network.offline += y.agentId
        x.pantries.forHome(homeId)!!.save(PantryItemDetails(primaryCategory = "Meat", title = "Beef Mince", packageSize = "500g"))
        eventually { x.pantryDeposited(homeId) }
        network.hangFor = 3.seconds
        network.hanging += x.agentId
        network.offline += x.agentId
        y.sync.syncNow()

        // Act
        network.comeOnline(y.agentId)

        // Assert: Y reads it from the hub well before a send to X could have timed out
        assertTrue(within(1500.milliseconds) { y.pantryTitles(homeId) == listOf("Beef Mince") })
        scope.cancel()
    }

    // --- Unpairing a device (docs/plans/unpaired-device-gap-plan.md, slice 3) ---

    @Test
    fun unpairingADeviceLeavesItOutOfTheRosterOfEveryHomeAndPairingItAgainBringsItBack() = runBlocking {
        // Arrange: X's Home lists a second device of X's
        val x = Device("agent-x")
        val homeId = x.homes.create("Maple Street", HomeShape.Family)
        x.sync.admit(homeId, x.profiles.current().id, "X", "admin", "agent-x")
        x.sync.admit(homeId, x.profiles.current().id, "X", "admin", "agent-x-tablet")
        val before = x.sync.rosterDevices(homeId).values.single()

        // Act
        x.sync.deviceUnpaired("agent-x-tablet")
        val whileUnpaired = x.sync.rosterDevices(homeId).values.single()
        x.sync.devicePaired("agent-x-tablet")
        val afterPairing = x.sync.rosterDevices(homeId).values.single()

        // Assert
        assertEquals(setOf("agent-x", "agent-x-tablet"), before)
        assertEquals(setOf("agent-x"), whileUnpaired)
        assertEquals(setOf("agent-x", "agent-x-tablet"), afterPairing)
        scope.cancel()
    }

    @Test
    fun unpairingADeviceFromThePairingScreenTakesItOutOfTheHomesGroup() = runBlocking {
        // Arrange: two devices of one user, paired for real, sharing a Home whose group both are in
        val first = Device("agent-1")
        val second = Device("agent-2")
        first.routeDeviceSyncMessages = true
        second.routeDeviceSyncMessages = true
        val link = PairingLink(agentId = second.agentId, expiresAt = System.currentTimeMillis() + 60_000, deviceName = "Second")
        second.database.devicePairingLinkQueries.insert(link.agentId, link.agentId, System.currentTimeMillis(), link.expiresAt)
        assertTrue(first.deviceSync.pairWith(link).isSuccess)
        eventually { second.devices.agentIds().contains(first.agentId) }
        first.start()
        second.start()
        val homeId = first.homes.create("Maple Street", HomeShape.Family)
        eventually { second.homeNames() == listOf("Maple Street") && first.profiles.current().id == second.profiles.current().id }
        eventually { first.mailboxStore(homeId) != null && second.mailboxStore(homeId) != null }
        val groupId = first.database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()!!.groupId
        assertTrue("agent-2" in network.groups.getValue(groupId).members.keys)

        // Act: the user removes the second device on the first
        val result = first.deviceSync.removeDevice("agent-2")

        // Assert: the Home's group lets it go, not only the pairing list
        assertTrue(result.isSuccess)
        eventually { "agent-2" !in network.groups.getValue(groupId).members.keys }
        assertTrue("agent-1" in network.groups.getValue(groupId).members.keys)
        scope.cancel()
    }
}
