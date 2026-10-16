package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.home.homeRepository
import uk.veritaslabs.vesta.getDeviceName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.automerge.VestaHomeSeeding
import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.vesta.automerge.CalendarSources
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.vesta.automerge.MealPlanSources
import uk.veritaslabs.vesta.automerge.MerchantsSources
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.TaskSources
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

/**
 * One of the user's devices on the fake mesh, for the Sync activity tests: its own node and database, with a data sync that runs once
 * [start]ed. Unlike the private device of `DeviceDataSyncServiceTest` it can be given another client (to break the daemon), a clock and a
 * short refresh interval for the snapshot flow.
 */
internal class ActivityDevice(
    network: FakeX0xNetwork,
    private val scope: CoroutineScope,
    val agentId: String,
    interval: Duration = 50.milliseconds,
    activityRefresh: Duration = 20.seconds,
    peerBackoff: Duration = 2.minutes,
    nowMillis: () -> Long = { System.currentTimeMillis() },
    /** False takes the Home's mailbox away, as a daemon that can't open the group's store does: nothing is deposited and nothing fetched. */
    mailbox: Boolean = true,
    wrapClient: (X0xClient) -> X0xClient = { it },
) {
    val node = network.node(agentId)
    val database: KitsDatabase = openInMemoryDatabase()
    val devices = SyncedDeviceRepository(database)
    val pantries = PantrySources(database)
    val shopping = ShoppingListSources(database, pantries)
    val calendars = CalendarSources(database)
    val tasks = TaskSources(database)
    val recorder = SyncEventRecorder(database, nowMillis)
    val sync = DeviceDataSyncService(
        wrapClient(
            if (mailbox) node else object : X0xClient by node {
                override suspend fun openGroupStore(groupId: String, name: String): String = error("the group store cannot be opened")
            },
        ),
        database, devices, ::getDeviceName, VestaHomeSources(database, pantries, shopping, calendars = calendars, tasks = tasks).documentTypes(), interval = interval, peerBackoff = peerBackoff, nowMillis = nowMillis, recorder = recorder,
        activityRefresh = activityRefresh,
        seeding = VestaHomeSeeding(VestaHomeSources(database, pantries), database),
    )
    val homes = homeRepository(database, sync.homeChanges)
    val profiles = ProfileRepository(database, deviceName = ::getDeviceName, changes = sync.profileChanges)

    init {
        node.onDirect = { message: X0xDirectMessage ->
            scope.launch { sync.handle(message) }
        }
    }

    fun start() {
        scope.launch { sync.run() }
    }

    suspend fun pairedWith(other: ActivityDevice) = apply { devices.add(other.agentId, other.agentId) }

    /** Edits the Home's Pantry here, as the user would. */
    suspend fun addToPantry(homeId: String, title: String) {
        pantries.forHome(homeId)!!.mutate {
            uk.veritaslabs.vesta.automerge.upsertPantryItem(it, "Dairy", "Milk", title, "2 pint", barcode = null)
        }
    }
}
