package uk.veritaslabs.vesta

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.agent.AgentAccessRepository
import uk.veritaslabs.vesta.agent.AgentApprovalHost
import uk.veritaslabs.vesta.db.DatabaseDriverFactory
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.createVestaDatabase
import uk.veritaslabs.vesta.devicepairing.DevicePairingViewModel
import uk.veritaslabs.vesta.devicesync.DeviceDataSync
import uk.veritaslabs.vesta.devicesync.DeviceSyncService
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.ui.flows.DevicePairingFlow
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.createX0xClient
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.SelectedHome
import uk.veritaslabs.vesta.ui.screens.HomeDashboardScreen
import uk.veritaslabs.vesta.ui.screens.HomeEditScreen
import uk.veritaslabs.vesta.ui.screens.HomeEditViewModel
import uk.veritaslabs.vesta.ui.screens.HomeListItem
import uk.veritaslabs.vesta.ui.screens.HomePickerScreen
import uk.veritaslabs.vesta.ui.screens.HomePickerViewModel
import uk.veritaslabs.vesta.ui.screens.PantryCategoriesScreen
import uk.veritaslabs.vesta.ui.screens.PantryCategoriesViewModel
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails
import uk.veritaslabs.vesta.ui.screens.PantryItemEditPackageScreen
import uk.veritaslabs.vesta.ui.screens.PantryItemEditScreen
import uk.veritaslabs.vesta.ui.screens.PantryListItem
import uk.veritaslabs.vesta.ui.screens.PantrySource
import uk.veritaslabs.vesta.ui.screens.PantryViewModel
import uk.veritaslabs.vesta.ui.screens.PantryScreen
import uk.veritaslabs.vesta.ui.screens.SettingsScreen
import uk.veritaslabs.vesta.ui.screens.SettingsSyncedDevicesScreen
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.rememberX0xConnectivity

/** The app-wide currently selected Home; read `LocalSelectedHome.current.homeId` from any composable under [App]. */
val LocalSelectedHome = compositionLocalOf<SelectedHome> { error("No SelectedHome provided; call within App()") }

/** The user's dark theme choice and how to change it; provided by [App], which owns the theme. */
class DarkThemeSetting(val enabled: Boolean, val onChange: (Boolean) -> Unit)

val LocalDarkThemeSetting = compositionLocalOf<DarkThemeSetting> { error("No DarkThemeSetting provided; call within App()") }

/**
 * Which Home the edit screen is open on: [homeId] is null when creating a new Home. [session] is unique
 * per opening, so each visit gets a fresh view model rather than a finished one left in the store.
 */
private data class HomeEditTarget(val homeId: String?, val session: Int)

/**
 * App root. Shows the Home picker until a Home is selected, then [homeContent] for that Home's id and name.
 * [homes] is the list the picker offers. With a [homeRepository], the picker's create/edit/delete
 * actions are live; without one (previews) they do nothing. With [agentAccess], an agent waiting for the user's
 * approval is offered over whatever screen is showing. With [deviceSync], the Synced devices screen lists and
 * removes the user's real devices; without one (previews) it is always empty.
 */
@Composable
@Preview
fun App(
    homes: Flow<List<HomeListItem>> = flowOf(emptyList()),
    selectedHome: SelectedHome = remember { SelectedHome() },
    homeRepository: HomeRepository? = null,
    agentAccess: AgentAccessRepository? = null,
    deviceSync: DeviceSyncHost? = null,
    homeContent: @Composable (homeId: String, homeName: String) -> Unit = { id, name ->
        HomeContent(id, name, deviceSync = deviceSync)
    },
) {
    val homeId by selectedHome.homeId.collectAsStateWithLifecycle()
    val homeList by homes.collectAsStateWithLifecycle(emptyList())
    var editTarget by remember { mutableStateOf<HomeEditTarget?>(null) }
    var pairingSession by remember { mutableStateOf<Int?>(null) }
    var pairingSessions by remember { mutableIntStateOf(0) }
    var editSessions by remember { mutableIntStateOf(0) }
    fun openEditor(homeId: String?) {
        editTarget = HomeEditTarget(homeId, session = editSessions++)
    }
    val scope = rememberCoroutineScope()
    // Drop the selection if its Home goes away (deleted, or left).
    LaunchedEffect(homes, selectedHome) {
        homes.collect { list -> selectedHome.homeId.value?.let { id -> if (list.none { it.id == id }) selectedHome.clear() } }
    }
    // Follows the system until the user flips the switch in Settings; not persisted yet.
    val systemDark = isSystemInDarkTheme()
    var darkOverride by remember { mutableStateOf<Boolean?>(null) }
    val darkTheme = darkOverride ?: systemDark
    val darkThemeSetting = remember(darkTheme) { DarkThemeSetting(darkTheme) { darkOverride = it } }
    CompositionLocalProvider(LocalSelectedHome provides selectedHome, LocalDarkThemeSetting provides darkThemeSetting) {
        VestaTheme(darkTheme = darkTheme) {
            Box(modifier = Modifier.fillMaxSize()) {
                homeId?.let { id -> homeContent(id, homeList.firstOrNull { it.id == id }?.name.orEmpty()) } ?: run {
                    val target = editTarget
                    val pairing = pairingSession
                    if (pairing != null && deviceSync != null) {
                        val pairingViewModel = viewModel(key = "device-pairing-start-$pairing") { deviceSync.newPairingViewModel() }
                        DevicePairingFlow(pairingViewModel, onExit = { pairingSession = null })
                    } else if (target != null && homeRepository != null) {
                        val editViewModel = viewModel(key = "home-edit-${target.session}") {
                            HomeEditViewModel(homeRepository, selectedHome, target.homeId)
                        }
                        HomeEditScreen(editViewModel, onFinished = { editTarget = null })
                    } else {
                        val viewModel = viewModel { HomePickerViewModel(homes, selectedHome) }
                        HomePickerScreen(
                            viewModel,
                            onCreateHome = { openEditor(null) },
                            onPairDevice = { if (deviceSync != null) pairingSession = pairingSessions++ },
                            onEditHome = { openEditor(it.id) },
                            onDeleteHome = { home -> homeRepository?.let { scope.launch { it.delete(home.id) } } },
                        )
                    }
                }
                agentAccess?.let { AgentApprovalHost(it, modifier = Modifier.align(Alignment.BottomCenter)) }
            }
        }
    }
}


/**
 * Default content for a selected Home: the [HomeDashboardScreen], whose Pantry tile opens the
 * [PantryScreen] (its FAB opens [PantryItemEditScreen], whose saves go to [pantrySource] and show up
 * in the Pantry). Without a [pantrySource] the Pantry is empty and saves are dropped.
 */
@Composable
private fun HomeContent(
    homeId: String,
    homeName: String,
    pantrySource: PantrySource? = null,
    deviceSync: DeviceSyncHost? = null,
) {
    val selectedHome = LocalSelectedHome.current
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(HomeDestination.Dashboard) }
    var pairingSessions by remember { mutableIntStateOf(0) }
    var editingItem by remember { mutableStateOf<PantryItemDetails?>(null) }
    val pantryViewModel = pantrySource?.let { source ->
        viewModel(key = "pantry-$homeId") { PantryViewModel(source) }
    }
    val categoriesViewModel = pantrySource?.categoriesSource?.let { source ->
        viewModel(key = "pantry-categories-$homeId") { PantryCategoriesViewModel(source) }
    }
    val categoriesState = categoriesViewModel?.uiState?.collectAsStateWithLifecycle()
    // Where the categories screen's back arrow returns to: the Pantry, or the item form it was opened from.
    var categoriesBackTo by remember { mutableStateOf(HomeDestination.Pantry) }
    val syncedDeviceList by (deviceSync?.devices?.devices ?: flowOf(emptyList())).collectAsStateWithLifecycle(emptyList())
    when (destination) {
        HomeDestination.Dashboard -> HomeDashboardScreen(
            homeName = homeName,
            onBack = { selectedHome.clear() },
            x0xConnectivity = rememberX0xConnectivity(),
            onPantryClick = { destination = HomeDestination.Pantry },
            onSettingsClick = { destination = HomeDestination.Settings },
        )
        HomeDestination.Settings -> SettingsScreen(
            x0xConnectivity = rememberX0xConnectivity(),
            darkTheme = LocalDarkThemeSetting.current.enabled,
            onDarkThemeChange = LocalDarkThemeSetting.current.onChange,
            onSyncedDevicesClick = { destination = HomeDestination.SettingsSyncedDevices },
            onBack = { destination = HomeDestination.Dashboard },
        )
        HomeDestination.SettingsSyncedDevices -> SettingsSyncedDevicesScreen(
            devices = syncedDeviceList,
            onAddDevice = { if (deviceSync != null) destination = HomeDestination.DevicePairing },
            onRemoveDevice = { device -> deviceSync?.let { scope.launch { it.service.removeDevice(device.agentId) } } },
            onBack = { destination = HomeDestination.Settings },
        )
        HomeDestination.DevicePairing -> deviceSync?.let { sync ->
            // A fresh view model per visit, so a finished pairing isn't left in the store.
            val pairing = viewModel(key = "device-pairing-$pairingSessions") { sync.newPairingViewModel() }
            DevicePairingFlow(pairing, onExit = {
                pairingSessions++
                destination = HomeDestination.SettingsSyncedDevices
            })
        }
        HomeDestination.Pantry -> {
            val onBack = { destination = HomeDestination.Dashboard }
            val onAddItem = {
                editingItem = null
                destination = HomeDestination.PantryItemEdit
            }
            val onItemSelected = { item: PantryListItem ->
                editingItem = item.toDetails()
                destination = HomeDestination.PantryItemEdit
            }
            val onManageCategories = {
                categoriesBackTo = HomeDestination.Pantry
                destination = HomeDestination.PantryCategories
            }
            if (pantryViewModel != null) {
                PantryScreen(
                    pantryViewModel,
                    onItemSelected = onItemSelected,
                    onBack = onBack,
                    onAddItem = onAddItem,
                    onManageCategories = onManageCategories,
                )
            } else {
                PantryScreen(
                    items = emptyList(),
                    onItemSelected = onItemSelected,
                    onBack = onBack,
                    onAddItem = onAddItem,
                    onManageCategories = onManageCategories,
                )
            }
        }
        HomeDestination.PantryCategories -> {
            val onBack = { destination = categoriesBackTo }
            if (categoriesViewModel != null) {
                PantryCategoriesScreen(categoriesViewModel, onBack = onBack)
            } else {
                PantryCategoriesScreen(categories = emptyList(), onBack = onBack)
            }
        }
        HomeDestination.PantryItemEdit -> PantryItemEditScreen(
            item = editingItem,
            categories = categoriesState?.value?.categories.orEmpty(),
            onEditCategories = { draft ->
                editingItem = draft
                categoriesBackTo = HomeDestination.PantryItemEdit
                destination = HomeDestination.PantryCategories
            },
            onEditPackage = { draft ->
                editingItem = draft
                destination = HomeDestination.PantryItemEditPackage
            },
            onDelete = { key ->
                pantryViewModel?.delete(key)
                destination = HomeDestination.Pantry
            },
            onSave = { item ->
                pantryViewModel?.save(item)
                destination = HomeDestination.Pantry
            },
            onCancel = { destination = HomeDestination.Pantry },
        )
        HomeDestination.PantryItemEditPackage -> {
            val draft = editingItem ?: PantryItemDetails()
            val backToForm = { destination = HomeDestination.PantryItemEdit }
            PantryItemEditPackageScreen(
                itemName = draft.title,
                initialValue = draft.packageSize,
                startLoose = draft.key != null,
                onDone = { size ->
                    editingItem = draft.copy(packageSize = size)
                    backToForm()
                },
                onBack = backToForm,
            )
        }
    }
}

private fun PantryListItem.toDetails() = PantryItemDetails(
    key = key,
    primaryCategory = primaryCategory,
    secondaryCategory = secondaryCategory,
    title = title,
    packageSize = packageSize,
    barcode = barcode,
    stockCount = stockCount,
)

/** What the UI needs of device sync: the stored [devices], the [service] that keeps them in step, and a pairing view model factory. */
class DeviceSyncHost(
    val devices: SyncedDeviceRepository,
    val service: DeviceSyncService,
    val newPairingViewModel: () -> DevicePairingViewModel,
)

private enum class HomeDestination { Dashboard, Settings, SettingsSyncedDevices, DevicePairing, Pantry, PantryCategories, PantryItemEdit, PantryItemEditPackage }

/**
 * [App] backed by the local database on this platform; what the platform entry points launch. With
 * [openPantry] (platforms that have the Pantry Automerge document layer), each Home's Pantry is loaded
 * from and saved to the database; without it the Pantry is not persisted. [backgroundServices], if given,
 * runs for as long as the app is shown, with the database (created here, so callers can't have it earlier).
 * [dataSync], if given, builds the sync of the user's data (Homes, Pantries) between their paired devices from the
 * database, the x0x client and the device list; it is fed Home changes and run for as long as the app is shown.
 */
@Composable
fun VestaApp(
    openPantry: (suspend (VestaDatabase, homeId: String) -> PantrySource)? = null,
    homeContent: (@Composable (homeId: String, homeName: String) -> Unit)? = null,
    backgroundServices: (suspend CoroutineScope.(VestaDatabase) -> Unit)? = null,
    dataSync: ((VestaDatabase, X0xClient, SyncedDeviceRepository) -> DeviceDataSync)? = null,
) {
    val database = remember { createVestaDatabase(DatabaseDriverFactory()) }
    LaunchedEffect(database) { backgroundServices?.invoke(this, database) }
    val client = remember { createX0xClient() }
    val devices = remember { SyncedDeviceRepository(database) }
    val deviceDataSync = remember { dataSync?.invoke(database, client, devices) }
    LaunchedEffect(deviceDataSync) { deviceDataSync?.run() }
    val homeRepository = remember { HomeRepository(database, deviceDataSync?.homeChanges) }
    val agentAccess = remember { AgentAccessRepository(database) }
    val deviceSync = remember {
        val service = DeviceSyncService(client, database, devices)
        DeviceSyncHost(devices, service) {
            DevicePairingViewModel(
                database,
                agentId = { checkNotNull(client.agent().agentId) { "x0x did not report this device's agent id" } },
                deviceName = ::getDeviceName,
                deviceSync = service,
            )
        }
    }
    LaunchedEffect(deviceSync) { deviceSync.service.run() }
    App(
        homes = homeRepository.homes,
        homeRepository = homeRepository,
        agentAccess = agentAccess,
        deviceSync = deviceSync,
        homeContent = homeContent ?: { homeId, homeName ->
            val pantrySource by produceState<PantrySource?>(null, homeId) { value = openPantry?.invoke(database, homeId) }
            HomeContent(homeId, homeName, pantrySource, deviceSync)
        },
    )
}
