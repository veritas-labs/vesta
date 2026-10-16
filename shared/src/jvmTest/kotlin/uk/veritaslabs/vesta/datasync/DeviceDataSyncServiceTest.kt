@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.kits.sync.DeviceDataSyncService
import uk.veritaslabs.kits.docs.SyncEnvelope
import uk.veritaslabs.kits.docs.SyncEventRecorder
import uk.veritaslabs.kits.docs.SyncModules
import uk.veritaslabs.kits.spaces.SpaceRepository

import uk.veritaslabs.vesta.syncactivity.VestaSyncModules
import uk.veritaslabs.vesta.home.homeRepository
import uk.veritaslabs.vesta.getDeviceName
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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
import uk.veritaslabs.vesta.automerge.VestaHomeSeeding
import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.vesta.automerge.CalendarSources
import uk.veritaslabs.vesta.automerge.TaskSources
import uk.veritaslabs.vesta.automerge.privateTasksDocumentId
import uk.veritaslabs.vesta.automerge.tasksDocumentId
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDone
import uk.veritaslabs.vesta.tasks.TaskList
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
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.kits.sync.PairingLink
import uk.veritaslabs.kits.sync.DeviceSyncService
import uk.veritaslabs.kits.x0x.testing.FakeX0xNetwork
import uk.veritaslabs.kits.sync.SyncedDeviceRepository
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.kits.spaces.MemberRepository
import uk.veritaslabs.kits.spaces.RoleChangeResult
import uk.veritaslabs.kits.spaces.MemberRole
import uk.veritaslabs.kits.spaces.LeaveResult
import uk.veritaslabs.kits.spaces.RemoveMemberResult
import uk.veritaslabs.kits.spaces.SpaceJoinMessage
import uk.veritaslabs.kits.spaces.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails
import org.automerge.SyncState
import uk.veritaslabs.kits.spaces.SpaceRegistryDocumentId
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.merchantsDocumentId
import uk.veritaslabs.vesta.automerge.cookbookDocumentId
import uk.veritaslabs.vesta.automerge.mealPlanDocumentId
import uk.veritaslabs.kits.spaces.rosterDocumentId
import uk.veritaslabs.vesta.automerge.shoppingListDocumentId
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.storeEmptyShoppingList
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.screens.MealSlot
import uk.veritaslabs.kits.x0x.X0xDirectMessage

import uk.veritaslabs.kits.spaces.SpaceRegistry
import uk.veritaslabs.kits.spaces.SpaceJoinService
import uk.veritaslabs.kits.spaces.SpaceRosters
class DeviceDataSyncServiceTest {

    private val network = FakeX0xNetwork()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One of the user's devices: its own x0x node and database, with a running data sync. */
    private inner class Device(
        val agentId: String,
        interval: Duration = 50.milliseconds,
        peerBackoff: Duration = 2.minutes,
        /** Gives the device a Sync activity recorder that fails every time it is asked for the time, to show recording never matters to a sync. */
        brokenRecorder: Boolean = false,
    ) {
        val node = network.node(agentId)
        val database: KitsDatabase = openInMemoryDatabase()
        val devices = SyncedDeviceRepository(database)
        val pantries = PantrySources(database)
        val shopping = ShoppingListSources(database, pantries)
        val cookbook = CookbookSources(database)
        val merchants = MerchantsSources(database)
        val mealPlans = MealPlanSources(database)
        val calendars = CalendarSources(database)
        val tasks = TaskSources(database)
        /** The id of every document a sync message has reached this device for. */
        val receivedDocuments = ConcurrentLinkedQueue<String>()
        val sync = DeviceDataSyncService(
            node, database, devices, ::getDeviceName, VestaHomeSources(database, pantries, shopping, cookbook, merchants, mealPlans, calendars, tasks).documentTypes(), interval = interval,
            seeding = VestaHomeSeeding(VestaHomeSources(database, pantries), database),
            peerBackoff = peerBackoff,
            recorder = if (brokenRecorder) SyncEventRecorder(database, nowMillis = { error("the clock is broken") }) else SyncEventRecorder(database),
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
        val homes = homeRepository(database, sync.spaceChanges)
        val profiles = ProfileRepository(database, deviceName = ::getDeviceName, changes = sync.profileChanges)


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

        suspend fun activity(homeId: String) = database.syncEventQueries.selectPage(homeId, Long.MAX_VALUE, 100).awaitAsList()



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

        suspend fun homeTaskTitles(homeId: String) = tasks.forHome(homeId)!!.snapshot.first().tasks.map { it.title }.sorted()

        suspend fun privateTaskTitles(homeId: String) = tasks.privateForHome(homeId)!!.snapshot.first().tasks.map { it.title }.sorted()

        suspend fun doneOccurrences(homeId: String) = tasks.forHome(homeId)!!.snapshot.first().done.map { "${it.taskId}|${it.occurrence}" }.sorted()

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
    fun pantryItemsAddedOnEitherDeviceReachBoth() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
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
    fun shoppingListItemsAddedOnEitherDeviceReachBoth() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        eventually { b.homeNames().isNotEmpty() }

        // Act
        a.shopping.forHome(homeId)!!.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk"))
        eventually { b.shoppingTitles(homeId) == listOf("Whole Milk") }
        b.shopping.forHome(homeId)!!.save(ShoppingItemDetails(primaryCategory = "Meat", title = "Beef Mince"))

        // Assert
        eventually { a.shoppingTitles(homeId) == listOf("Beef Mince", "Whole Milk") }
        scope.cancel()
    }

    @Test
    fun recipesAddedOnEitherDeviceReachBoth() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
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
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
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
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        eventually { b.homeNames().isNotEmpty() }
        a.calendars.privateForHome(homeId)!!.save(event("p1", "Gym", EventCalendar.Private))
        eventually { b.privateEventTitles(homeId) == listOf("Gym") }

        // Act
        b.calendars.privateForHome(homeId)!!.save(event("p1", "Gym session", EventCalendar.Private))

        // Assert
        eventually { a.privateEventTitles(homeId) == listOf("Gym session") }
        scope.cancel()
    }

    private fun task(id: String, title: String, list: TaskList) = Task(id = id, list = list, title = title)

    @Test
    fun aHomesTasksAndWhatWasDoneReachItsOtherMembers() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()

        // Act
        x.tasks.forHome(homeId)!!.save(task("t1", "Bins", TaskList.Home))
        eventually { y.homeTaskTitles(homeId) == listOf("Bins") }
        y.tasks.forHome(homeId)!!.setDone(TaskDone("t1", "2026-10-14", 5L, "m:y"))

        // Assert
        eventually { x.doneOccurrences(homeId) == listOf("t1|2026-10-14") }
        scope.cancel()
    }

    @Test
    fun twoMembersTickingTheSameOccurrenceMergeToOneRecord() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()
        x.tasks.forHome(homeId)!!.save(task("t1", "Bins", TaskList.Home))
        eventually { y.homeTaskTitles(homeId) == listOf("Bins") }

        // Act
        x.tasks.forHome(homeId)!!.setDone(TaskDone("t1", "2026-10-14", 5L, "m:x"))
        y.tasks.forHome(homeId)!!.setDone(TaskDone("t1", "2026-10-14", 6L, "m:y"))
        delay(500.milliseconds)

        // Assert
        eventually { x.doneOccurrences(homeId) == listOf("t1|2026-10-14") && y.doneOccurrences(homeId) == listOf("t1|2026-10-14") }
        scope.cancel()
    }

    @Test
    fun privateTasksReachTheUsersOwnDevicesAndNeverAnotherMemberOfTheHome() = runBlocking {
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
        x.tasks.privateForHome(homeId)!!.save(task("p1", "Buy a present", TaskList.Private))
        x.tasks.forHome(homeId)!!.save(task("t1", "Bins", TaskList.Home))
        eventually { x2.privateTaskTitles(homeId) == listOf("Buy a present") }
        eventually { y.homeTaskTitles(homeId) == listOf("Bins") }
        delay(500.milliseconds) // long enough for several sync rounds to have passed

        // Assert
        assertTrue(privateTasksDocumentId(homeId) !in y.receivedDocuments)
        assertEquals(emptyList(), y.privateTaskTitles(homeId))
        assertTrue(tasksDocumentId(homeId) in y.receivedDocuments)
        scope.cancel()
    }

    @Test
    fun merchantsAddedOnEitherDeviceReachBoth() = runBlocking {
        // Arrange
        val (a, b) = pair()
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        eventually { b.homeNames().isNotEmpty() }

        // Act
        a.merchants.forHome(homeId)!!.save(Merchant(name = "Hargreaves"))
        eventually { b.merchantNames(homeId) == listOf("Hargreaves") }
        b.merchants.forHome(homeId)!!.save(Merchant(name = "Aldi"))

        // Assert
        eventually { a.merchantNames(homeId) == listOf("Aldi", "Hargreaves") }
        scope.cancel()
    }










    /** Two people who are not paired: X made the Home and admitted Y; Y has the Home and knows X invited it. */
    private suspend fun twoPeopleInOneHome(suffix: String = ""): Triple<Device, Device, String> {
        val x = Device("agent-x$suffix")
        val y = Device("agent-y$suffix")
        val homeId = x.homes.create("Maple Street", HomeShape.Family.storageValue)
        val xMember = x.profiles.current()
        val yMember = y.profiles.current()
        y.joinedHome(homeId, "Maple Street", HomeShape.Family)
        x.sync.admit(homeId, yMember.id, yMember.displayName, "member", y.agentId)
        y.sync.admit(homeId, xMember.id, xMember.displayName, "admin", x.agentId)
        return Triple(x, y, homeId)
    }




















    // --- The mailbox (ADR-020) ---

    /** Waits until both devices of [homeId] are in the Home's group with its mailbox store open, then adds an always-on hub to it. */
    private suspend fun withHub(x: Device, y: Device, homeId: String, hubName: String = "agent-hub"): String {
        eventually { x.mailboxStore(homeId) != null && y.mailboxStore(homeId) != null }
        val groupId = x.database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()!!.groupId
        val hub = network.node(hubName)
        hub.joinGroup(x.node.createGroupInvite(groupId))
        hub.openGroupStore(groupId, "mailbox")
        return groupId
    }

    private suspend fun Device.mailboxStore(homeId: String) = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.storeId






    // --- Peers that do not answer (ADR-020 by-hand run: an offline peer delayed the mailbox by minutes) ---

    /** Waits until [check] holds, but for no longer than [within]; false if it never did. */
    private suspend fun within(within: Duration, check: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(within) { while (!check()) delay(20.milliseconds); true } ?: false





    // --- Unpairing a device (docs/plans/unpaired-device-gap-plan.md, slice 3) ---



    @Test
    fun aTasksEditIsLoggedAsSentOnTheEditorAndReceivedOnTheOtherMember() = runBlocking {
        // Arrange
        val (x, y, homeId) = twoPeopleInOneHome()
        x.start()
        y.start()

        // Act
        x.tasks.forHome(homeId)!!.save(task("t1", "Bins", TaskList.Home))
        eventually { y.homeTaskTitles(homeId) == listOf("Bins") }
        eventually { y.activity(homeId).any { it.kind == "received" } && x.activity(homeId).any { it.kind == "sent" } }

        // Assert
        val received = y.activity(homeId).first { it.kind == "received" && it.module == "tasks" }
        assertEquals("agent-x", received.peerAgent)
        val sent = x.activity(homeId).first { it.kind == "sent" && it.module == "tasks" }
        assertEquals("agent-y", sent.peerAgent)
        scope.cancel()
    }







    // --- Every Home document, one test per behaviour (docs/plans/document-registry-and-source-collapse-plan.md, WP0) ---

    /** One kind of Home document: how to edit it on a device, how to see that edit on another, and where it is named. */
    private inner class DocumentCase(
        val name: String,
        val idFor: (String) -> String,
        val module: String,
        val private: Boolean,
        val edit: suspend Device.(homeId: String) -> Unit,
        val seen: suspend Device.(homeId: String) -> Boolean,
        val heads: suspend Device.(homeId: String) -> ByteArray?,
        /** The document's side of a sync exchange on a device, null when the device has no such document. */
        val peer: suspend Device.(homeId: String) -> SyncPeer?,
        /** Gives a device with a bare Home row the document, for those that don't exist until something writes them. */
        val prime: suspend Device.(homeId: String) -> Unit = {},
    )

    private class SyncPeer(val generate: suspend (SyncState) -> ByteArray?, val receive: suspend (SyncState, ByteArray) -> Unit)

    private val tuesday = CalendarDate(2026, 10, 13)

    private val documentCases = listOf(
        DocumentCase(
            "pantry", ::pantryDocumentId, VestaSyncModules.Pantry, private = false,
            edit = { pantries.forHome(it)!!.mutate { doc -> upsertPantryItem(doc, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null) } },
            seen = { pantryTitles(it) == listOf("Whole Milk") },
            heads = { pantries.forHome(it)?.heads() },
            peer = { pantries.forHome(it)?.let { s -> SyncPeer(s::generateSync, s::receiveSync) } },
        ),
        DocumentCase(
            "shopping-list", ::shoppingListDocumentId, VestaSyncModules.Shopping, private = false,
            edit = { shopping.forHome(it)!!.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk")) },
            seen = { shoppingTitles(it) == listOf("Whole Milk") },
            heads = { shopping.forHome(it)?.heads() },
            peer = { shopping.forHome(it)?.let { s -> SyncPeer(s::generateSync, s::receiveSync) } },
        ),
        DocumentCase(
            "cookbook", ::cookbookDocumentId, VestaSyncModules.Cookbook, private = false,
            edit = { cookbook.forHome(it)!!.save(Recipe(title = "Shepherd's Pie")) },
            seen = { recipeTitles(it) == listOf("Shepherd's Pie") },
            heads = { cookbook.forHome(it)?.heads() },
            peer = { cookbook.forHome(it)?.let { s -> SyncPeer(s::generateSync, s::receiveSync) } },
        ),
        DocumentCase(
            "merchants", ::merchantsDocumentId, VestaSyncModules.Merchants, private = false,
            edit = { merchants.forHome(it)!!.save(Merchant(name = "Hargreaves")) },
            seen = { merchantNames(it) == listOf("Hargreaves") },
            heads = { merchants.forHome(it)?.heads() },
            peer = { merchants.forHome(it)?.let { s -> SyncPeer(s::generateSync, s::receiveSync) } },
        ),
        DocumentCase(
            "mealplan", ::mealPlanDocumentId, VestaSyncModules.MealPlan, private = false,
            edit = { mealPlans.forHome(it)!!.add(tuesday, MealSlot.Dinner, "pie") },
            seen = { plannedDinners(it, tuesday) == listOf("pie") },
            heads = { mealPlans.forHome(it)?.heads() },
            peer = { mealPlans.forHome(it)?.let { s -> SyncPeer(s::generateSync, s::receiveSync) } },
        ),
        DocumentCase(
            "calendar", ::calendarDocumentId, VestaSyncModules.Calendar, private = false,
            edit = { calendars.forHome(it)!!.save(event("e1", "Bin day", EventCalendar.Home)) },
            seen = { homeEventTitles(it) == listOf("Bin day") },
            heads = { calendars.forHome(it)?.heads() },
            peer = { calendars.forHome(it)?.let { s -> SyncPeer(s::generateSync, s::receiveSync) } },
        ),
        DocumentCase(
            "private-calendar", ::privateCalendarDocumentId, VestaSyncModules.Calendar, private = true,
            edit = { calendars.privateForHome(it)!!.save(event("p1", "Surprise party planning", EventCalendar.Private)) },
            seen = { privateEventTitles(it) == listOf("Surprise party planning") },
            heads = { calendars.privateForHome(it)?.heads() },
            peer = { calendars.privateForHome(it)?.let { s -> SyncPeer(s::generateSync, s::receiveSync) } },
        ),
        DocumentCase(
            "tasks", ::tasksDocumentId, VestaSyncModules.Tasks, private = false,
            edit = { tasks.forHome(it)!!.save(task("t1", "Bins", TaskList.Home)) },
            seen = { homeTaskTitles(it) == listOf("Bins") },
            heads = { tasks.forHome(it)?.heads() },
            peer = { tasks.forHome(it)?.let { s -> SyncPeer(s::generateSync, s::receiveSync) } },
        ),
        DocumentCase(
            "private-tasks", ::privateTasksDocumentId, VestaSyncModules.Tasks, private = true,
            edit = { tasks.privateForHome(it)!!.save(task("p1", "Buy a present", TaskList.Private)) },
            seen = { privateTaskTitles(it) == listOf("Buy a present") },
            heads = { tasks.privateForHome(it)?.heads() },
            peer = { tasks.privateForHome(it)?.let { s -> SyncPeer(s::generateSync, s::receiveSync) } },
        ),
        DocumentCase(
            "roster", ::rosterDocumentId, SyncModules.Members, private = false,
            edit = { profiles.rename("Xavier") },
            seen = { memberNames(it).contains("Xavier") },
            heads = { SpaceRosters(database, profiles, deviceName = ::getDeviceName).heads(it) },
            peer = {
                val rosters = SpaceRosters(database, profiles, deviceName = ::getDeviceName)
                rosters.heads(it)?.let { _ -> SyncPeer({ state -> rosters.generate(it, state) }, { state, message -> rosters.receive(it, state, message) }) }
            },
            prime = { SpaceRosters(database, profiles, deviceName = ::getDeviceName).publish(it, agentId, emptySet()) },
        ),
    )

    private fun directMessage(sender: String, documentId: String, message: ByteArray) = X0xDirectMessage(
        sender = sender, payload = Base64.Default.encode(SyncEnvelope(documentId, message).encode()), verified = true, trustDecision = "accept",
    )

    private suspend fun Device.memberNames(homeId: String) =
        database.homeMemberQueries.selectByHome(homeId).awaitAsList().map { it.name }

    /** Whether everything this device has of [case]'s document in [homeId] is already in the mailbox. */
    private suspend fun Device.deposited(case: DocumentCase, homeId: String): Boolean {
        val heads = case.heads(this, homeId) ?: return false
        return database.mailboxQueries.selectHeads(homeId, case.idFor(homeId)).awaitAsOneOrNull()?.contentEquals(heads) == true
    }

    private suspend fun Device.eventCount(homeId: String, kind: String, module: String) =
        activity(homeId).filter { it.kind == kind && it.module == module }.sumOf { it.count }

    /** [eventually], but a check that throws counts as not yet true, and a timeout names the document and what never happened. */
    private suspend fun eventuallyFor(case: DocumentCase, what: String, check: suspend () -> Boolean) {
        try {
            eventually { runCatching { check() }.getOrDefault(false) }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("${case.name}: $what never happened", e)
        }
    }

    /** Each case gets its own scope, so one case's devices stop before the next case's start. */
    private fun freshScope() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Test
    fun everyDocumentCatchesUpAMemberWhoWasAwayThroughTheMailbox() = runBlocking {
        documentCases.filterNot { it.private }.forEachIndexed { index, case ->
            // Arrange: private documents are not in the mailbox, so only the shared ones are here
            freshScope()
            val (x, y, homeId) = twoPeopleInOneHome("-mailbox$index")
            x.start()
            y.start()
            withHub(x, y, homeId, "agent-hub-mailbox$index")
            network.offline += y.agentId

            // Act: X edits while Y is away, and is gone by the time Y is back, so no direct message can carry it
            case.edit(x, homeId)
            eventuallyFor(case, "deposit in the mailbox") { x.deposited(case, homeId) }
            network.offline += x.agentId
            network.comeOnline(y.agentId)

            // Assert
            eventuallyFor(case, "catching up through the mailbox") { case.seen(y, homeId) }
        }
        scope.cancel()
    }

    @Test
    fun everySharedDocumentThatArrivesBeforeItsHomeIsKeptAndAppliedWhenTheHomeArrives() = runBlocking {
        documentCases.filterNot { it.private }.forEachIndexed { index, case ->
            // Arrange: neither A nor B is running; every message is built and handed to B by hand. A message that carries changes
            // can't come from a real round before B has the Home (B never replies to a document it can't open), so A's document
            // and A's Home registry are each primed with the "have" of an empty replica on a third device first.
            freshScope()
            val a = Device("agent-a-waiting$index")
            val b = Device("agent-b-waiting$index")
            b.pairedWith(a)
            val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
            case.edit(a, homeId)
            val replica = Device("agent-e-waiting$index")
            replica.database.homeQueries.insert(homeId, "Maple Street", HomeShape.Family.storageValue, 1L) // a bare Home row: its documents start empty, with no history in common with A's
            replica.pantries.forHome(homeId) // the shopping list needs the Pantry to exist
            case.prime(replica, homeId)
            val aDocument = case.peer(a, homeId)!!
            val aState = SyncState()
            aDocument.receive(aState, case.peer(replica, homeId)!!.generate(SyncState())!!)
            val documentMessages = mutableListOf<ByteArray>()
            while (true) documentMessages += aDocument.generate(aState) ?: break
            val aRegistry = SpaceRegistry(a.database, VestaHomeSeeding(VestaHomeSources(a.database, a.pantries), a.database))
            val registryState = SyncState()
            aRegistry.receive(registryState, SpaceRegistry(replica.database, VestaHomeSeeding(VestaHomeSources(replica.database, replica.pantries), replica.database)).generate(SyncState())!!)
            val registryMessages = mutableListOf<ByteArray>()
            while (true) registryMessages += aRegistry.generate(registryState) ?: break
            val sender = a.agentId
            documentMessages.forEach { b.sync.handle(directMessage(sender, case.idFor(homeId), it)) }
            val homesBeforeRegistry = b.homeNames()

            // Act
            registryMessages.forEach { b.sync.handle(directMessage(sender, SpaceRegistryDocumentId, it)) }

            // Assert
            assertTrue(documentMessages.isNotEmpty() && registryMessages.isNotEmpty(), "${case.name}: nothing to hand to B")
            assertEquals(emptyList(), homesBeforeRegistry, "${case.name}: B had the Home before the registry arrived")
            assertEquals(listOf("Maple Street"), b.homeNames())
            eventuallyFor(case, "being applied once the Home arrived") { case.seen(b, homeId) }
        }
        scope.cancel()
    }

    @Test
    fun everyDocumentsEditIsLoggedInSyncActivityUnderItsModule() = runBlocking {
        documentCases.forEachIndexed { index, case ->
            // Arrange: X and Y share a Home, and X2 is X's own second device, which is who a private document goes to
            freshScope()
            val (x, y, homeId) = twoPeopleInOneHome("-activity$index")
            val x2 = Device("agent-x2-activity$index")
            x.pairedWith(x2)
            x2.pairedWith(x)
            x.start()
            x2.start()
            y.start()
            eventuallyFor(case, "everyone having the Home") { y.memberIds(homeId).size == 2 && x2.homeNames().isNotEmpty() }
            delay(400.milliseconds) // let the first exchanges settle, so only this case's edit adds to its module
            val observer = if (case.private) x2 else y
            val receivedBefore = observer.eventCount(homeId, "received", case.module)

            // Act
            case.edit(x, homeId)
            eventuallyFor(case, "the edit reaching the other device") { case.seen(observer, homeId) }

            // Assert: a sent row is not proof, as a handshake is logged as sent too; a received row is, as it needs the document to change
            eventuallyFor(case, "a received event in ${case.module}") { observer.eventCount(homeId, "received", case.module) > receivedBefore }
            eventuallyFor(case, "a sent event in ${case.module}") { x.eventCount(homeId, "sent", case.module) > 0 }
        }
        scope.cancel()
    }

    @Test
    fun theEngineSyncsAHomesDocumentsInOneFixedOrderWithTheRosterLast() = runBlocking {
        // Arrange: documentIds() is private, so its order shows in the order a round's first messages reach a peer
        val (a, b) = pair()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        a.sync.syncEverything()

        // Assert
        val order = b.receivedDocuments.filter { it.startsWith("$homeId:") }.distinct()
        assertEquals(
            listOf(
                "$homeId:pantry", "$homeId:shopping-list", "$homeId:cookbook", "$homeId:merchants", "$homeId:mealplan",
                "$homeId:calendar", "$homeId:private-calendar", "$homeId:tasks", "$homeId:private-tasks", "$homeId:roster",
            ),
            order,
        )
        scope.cancel()
    }
}
