package uk.veritaslabs.vesta

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import uk.veritaslabs.vesta.x0x.X0xNetworkPolicy
import uk.veritaslabs.vesta.x0x.createX0xClient
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.LeaveResult
import uk.veritaslabs.vesta.homejoin.HomeJoining
import uk.veritaslabs.vesta.homegroup.HomeHub
import uk.veritaslabs.vesta.homegroup.HubViewModel
import uk.veritaslabs.vesta.homejoin.InviteLinksViewModel
import uk.veritaslabs.vesta.homejoin.RedeemInviteViewModel
import uk.veritaslabs.vesta.ui.screens.HubManagerScreen
import uk.veritaslabs.vesta.ui.screens.HubScreen
import uk.veritaslabs.vesta.ui.screens.HubTutorialScreen
import uk.veritaslabs.vesta.ui.screens.NetworkDiagnosticsScreen
import uk.veritaslabs.vesta.ui.screens.NetworkPeersScreen
import uk.veritaslabs.vesta.ui.screens.InviteLinksScreen
import uk.veritaslabs.vesta.ui.screens.RedeemInviteScreen
import uk.veritaslabs.vesta.home.MemberRepository
import uk.veritaslabs.vesta.profile.ProfileRepository
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
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingItemFormLabels
import uk.veritaslabs.vesta.ui.screens.ShoppingListItem
import uk.veritaslabs.vesta.ui.screens.ShoppingListScreen
import uk.veritaslabs.vesta.ui.screens.ShoppingListSource
import uk.veritaslabs.vesta.ui.screens.ShoppingListViewModel
import uk.veritaslabs.vesta.ui.screens.PantryScreen
import uk.veritaslabs.vesta.ui.screens.MemberListItem
import uk.veritaslabs.vesta.ui.screens.MemberRole
import uk.veritaslabs.vesta.ui.screens.ProfileNameMode
import uk.veritaslabs.vesta.ui.screens.ProfileNameScreen
import uk.veritaslabs.vesta.ui.screens.MemberDetailScreen
import uk.veritaslabs.vesta.ui.screens.MembersScreen
import uk.veritaslabs.vesta.ui.screens.SettingsScreen
import uk.veritaslabs.vesta.ui.screens.SettingsSyncedDevicesScreen
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xConnectivity
import uk.veritaslabs.vesta.x0x.isReachingNetwork
import uk.veritaslabs.vesta.x0x.rememberX0xConnectivity
import uk.veritaslabs.vesta.x0x.rememberX0xNetworkDiagnostics
import uk.veritaslabs.vesta.x0x.rememberX0xPeers

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

/** The app-wide settings screens, reached from the Home picker. */
private enum class SettingsDestination { Settings, SyncedDevices, ProfileName, Peers, NetworkDiagnostics }

/**
 * App root. Shows the Home picker until a Home is selected, then [homeContent] for that Home's id and name, and an
 * `onEditHome` that opens the Home's editor over it.
 * [homes] is the list the picker offers. With a [homeRepository], the picker's create/edit/delete
 * actions are live; without one (previews) they do nothing. With [agentAccess], an agent waiting for the user's
 * approval is offered over whatever screen is showing. [x0xConnectivity] says whether the device is reaching the x0x network
 * (the Home picker tells waiting join requests so); it polls the local daemon by default. With [deviceSync], the Synced devices screen (under Settings, from the Home picker) lists and
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
    homeJoining: HomeJoining? = null,
    profiles: ProfileRepository? = null,
    x0xConnectivity: @Composable () -> X0xConnectivity = { rememberX0xConnectivity() },
    homeContent: @Composable (homeId: String, homeName: String, onEditHome: () -> Unit) -> Unit = { id, name, onEditHome ->
        HomeContent(id, name, onEditHome = onEditHome)
    },
) {
    val homeId by selectedHome.homeId.collectAsStateWithLifecycle()
    val homeList by homes.collectAsStateWithLifecycle(emptyList())
    var editTarget by remember { mutableStateOf<HomeEditTarget?>(null) }
    var pairingSession by remember { mutableStateOf<Int?>(null) }
    var settingsDestination by remember { mutableStateOf<SettingsDestination?>(null) }
    // Which opening of the screen to paste an invite link into is showing; unique so each gets a fresh view model.
    var joinSession by remember { mutableStateOf<Int?>(null) }
    var joinSessions by remember { mutableIntStateOf(0) }
    // What to do once the user has chosen a name, when they tried to create or join a Home before having one.
    var afterNaming by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pairingSessions by remember { mutableIntStateOf(0) }
    var editSessions by remember { mutableIntStateOf(0) }
    fun openEditor(homeId: String?) {
        editTarget = HomeEditTarget(homeId, session = editSessions++)
    }
    val scope = rememberCoroutineScope()
    val profile by remember(profiles) { profiles?.observe() ?: flowOf(null) }.collectAsStateWithLifecycle(null)
    // Runs [action] now if the user has a chosen name, otherwise after they have chosen one.
    fun needingName(action: () -> Unit) {
        if (profiles != null && profile?.named == false) afterNaming = action else action()
    }
    val pendingJoins by remember(homeJoining) { homeJoining?.observePendingJoins() ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    val syncedDeviceList by (deviceSync?.devices?.devices ?: flowOf(emptyList())).collectAsStateWithLifecycle(emptyList())
    // Drop the selection if its Home goes away (deleted, or left).
    LaunchedEffect(homes, selectedHome) {
        homes.collect { list -> selectedHome.homeId.value?.let { id -> if (list.none { it.id == id }) selectedHome.clear() } }
    }
    // Follows the system until the user flips the switch in Settings; not persisted yet.
    val systemDark = isSystemInDarkTheme()
    var darkOverride by remember { mutableStateOf<Boolean?>(null) }
    val darkTheme = darkOverride ?: systemDark
    // On until the user opts out; not persisted yet. Platforms that run x0x in-process stop it on mobile data unless this is on.
    var useMobileData by remember { mutableStateOf(X0xNetworkPolicy.useMobileData.value) }
    LaunchedEffect(useMobileData) { X0xNetworkPolicy.useMobileData.value = useMobileData }
    val darkThemeSetting = remember(darkTheme) { DarkThemeSetting(darkTheme) { darkOverride = it } }
    CompositionLocalProvider(LocalSelectedHome provides selectedHome, LocalDarkThemeSetting provides darkThemeSetting) {
        VestaTheme(darkTheme = darkTheme) {
            Box(modifier = Modifier.fillMaxSize()) {
                val currentProfile = profile
                if (profiles != null && currentProfile == null) {
                    // The Profile is still loading: nothing to show yet, rather than a flash of the Home picker.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else if (profiles != null && currentProfile != null && !currentProfile.named && afterNaming != null) {
                    // Asked for when the user first tries to create or join a Home (CORE-EXPERIENCE: a Profile is required for that).
                    ProfileNameScreen(
                        initialName = currentProfile.displayName,
                        mode = ProfileNameMode.Setup,
                        onSave = { name ->
                            scope.launch {
                                profiles.rename(name)
                                afterNaming?.invoke()
                                afterNaming = null
                            }
                        },
                        onBack = { afterNaming = null },
                    )
                } else homeId?.takeIf { editTarget == null }?.let { id ->
                    homeContent(id, homeList.firstOrNull { it.id == id }?.name.orEmpty()) { openEditor(id) }
                } ?: run {
                    val target = editTarget
                    val pairing = pairingSession
                    if (joinSession != null && homeJoining != null) {
                        val redeemViewModel = viewModel(key = "redeem-invite-$joinSession") { RedeemInviteViewModel(homeJoining) }
                        RedeemInviteScreen(redeemViewModel, onBack = { joinSession = null }, onStarted = { joinSession = null })
                    } else if (pairing != null && deviceSync != null) {
                        val pairingViewModel = viewModel(key = "device-pairing-start-$pairing") { deviceSync.newPairingViewModel() }
                        DevicePairingFlow(pairingViewModel, onExit = { pairingSession = null })
                    } else if (settingsDestination == SettingsDestination.ProfileName && profiles != null) {
                        ProfileNameScreen(
                            initialName = profile?.displayName.orEmpty(),
                            mode = ProfileNameMode.Rename,
                            onSave = { name ->
                                scope.launch {
                                    profiles.rename(name)
                                    settingsDestination = SettingsDestination.Settings
                                }
                            },
                            onBack = { settingsDestination = SettingsDestination.Settings },
                        )
                    } else if (settingsDestination == SettingsDestination.Settings) {
                        SettingsScreen(
                            x0xConnectivity = rememberX0xConnectivity(),
                            darkTheme = darkThemeSetting.enabled,
                            onDarkThemeChange = darkThemeSetting.onChange,
                            useMobileData = useMobileData,
                            onUseMobileDataChange = { useMobileData = it },
                            onSyncedDevicesClick = { settingsDestination = SettingsDestination.SyncedDevices },
                            onNetworkStatusClick = { settingsDestination = SettingsDestination.NetworkDiagnostics },
                            onPeersClick = { settingsDestination = SettingsDestination.Peers },
                            profileName = profile?.displayName,
                            onProfileNameClick = { settingsDestination = SettingsDestination.ProfileName },
                            onBack = { settingsDestination = null },
                        )
                    } else if (settingsDestination == SettingsDestination.NetworkDiagnostics) {
                        NetworkDiagnosticsScreen(
                            diagnostics = rememberX0xNetworkDiagnostics(),
                            onBack = { settingsDestination = SettingsDestination.Settings },
                        )
                    } else if (settingsDestination == SettingsDestination.Peers) {
                        val probeClient = remember { createX0xClient() }
                        NetworkPeersScreen(
                            peers = rememberX0xPeers(),
                            probe = { peerId -> probeClient.probePeer(peerId) },
                            onBack = { settingsDestination = SettingsDestination.Settings },
                        )
                    } else if (settingsDestination == SettingsDestination.SyncedDevices) {
                        SettingsSyncedDevicesScreen(
                            devices = syncedDeviceList,
                            onAddDevice = { if (deviceSync != null) pairingSession = pairingSessions++ },
                            onRemoveDevice = { device -> deviceSync?.let { scope.launch { it.service.removeDevice(device.agentId) } } },
                            onBack = { settingsDestination = SettingsDestination.Settings },
                        )
                    } else if (target != null && homeRepository != null) {
                        val editViewModel = viewModel(key = "home-edit-${target.session}") {
                            HomeEditViewModel(homeRepository, selectedHome, target.homeId)
                        }
                        HomeEditScreen(editViewModel, onFinished = { editTarget = null })
                    } else {
                        val viewModel = viewModel { HomePickerViewModel(homes, selectedHome) }
                        val connectivity = x0xConnectivity()
                        HomePickerScreen(
                            viewModel,
                            onCreateHome = { needingName { openEditor(null) } },
                            onJoinHome = { if (homeJoining != null) needingName { joinSession = joinSessions++ } },
                            onPairDevice = { if (deviceSync != null) pairingSession = pairingSessions++ },
                            onSettingsClick = { settingsDestination = SettingsDestination.Settings },
                            pendingJoins = pendingJoins,
                            networkConnected = connectivity.isReachingNetwork,
                            onCancelPendingJoin = { join -> scope.launch { homeJoining?.cancelPendingJoin(join.homeId) } },
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
    shoppingListSource: ShoppingListSource? = null,
    members: Flow<List<MemberListItem>> = flowOf(emptyList()),
    onLeave: suspend () -> LeaveResult = { LeaveResult.Left },
    onSetRole: suspend (memberId: String, role: MemberRole) -> Unit = { _, _ -> },
    onRemoveMember: suspend (memberId: String) -> Unit = {},
    memberAgents: suspend (memberId: String) -> List<String> = { emptyList() },
    onEditHome: () -> Unit = {},
    onDeleteHome: suspend () -> Unit = {},
    joining: HomeJoining? = null,
    hubs: HomeHub? = null,
    ownDeviceCanBeHub: Boolean = false,
    onOpened: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var leaveBlocked by remember { mutableStateOf(false) }
    val selectedHome = LocalSelectedHome.current
    val memberList by members.collectAsStateWithLifecycle(emptyList())
    val joinRequests by remember(homeId, joining) { joining?.observeJoinRequests(homeId) ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    val isAdmin = memberList.any { it.isYou && it.role == MemberRole.Admin }
    var destination by remember { mutableStateOf(HomeDestination.Dashboard) }
    var viewingMemberId by remember { mutableStateOf<String?>(null) }
    // Opening a Home or its Pantry asks for a sync now (ADR-020), rather than showing what the next timer round brings.
    LaunchedEffect(homeId, destination == HomeDestination.Pantry) { onOpened() }
    var editingItem by remember { mutableStateOf<PantryItemDetails?>(null) }
    val pantryViewModel = pantrySource?.let { source ->
        viewModel(key = "pantry-$homeId") { PantryViewModel(source) }
    }
    val shoppingViewModel = shoppingListSource?.let { source ->
        viewModel(key = "shopping-$homeId") { ShoppingListViewModel(source) }
    }
    var editingShoppingItem by remember { mutableStateOf<ShoppingItemDetails?>(null) }
    val categoriesViewModel = pantrySource?.categoriesSource?.let { source ->
        viewModel(key = "pantry-categories-$homeId") { PantryCategoriesViewModel(source) }
    }
    val pantryState = pantryViewModel?.uiState?.collectAsStateWithLifecycle()
    val categoriesState = categoriesViewModel?.uiState?.collectAsStateWithLifecycle()
    // Where the categories screen's back arrow returns to: the Pantry, or the item form it was opened from.
    var categoriesBackTo by remember { mutableStateOf(HomeDestination.Pantry) }
    when (destination) {
        HomeDestination.Dashboard -> HomeDashboardScreen(
            homeName = homeName,
            onBack = { selectedHome.clear() },
            x0xConnectivity = rememberX0xConnectivity(),
            onPantryClick = { destination = HomeDestination.Pantry },
            onShoppingClick = { destination = HomeDestination.ShoppingList },
            onMembersClick = { destination = HomeDestination.Members },
            isAdmin = isAdmin,
            membersBadge = if (isAdmin) joinRequests.count { !it.approved } else 0,
            onEditHome = onEditHome,
            onHubClick = { destination = HomeDestination.HubManager },
            onDeleteHome = { scope.launch { onDeleteHome() } },
            onLeaveHome = { scope.launch { if (onLeave() == LeaveResult.OnlyAdmin) leaveBlocked = true } },
            leaveBlocked = leaveBlocked,
            onLeaveBlockedDismiss = { leaveBlocked = false },
        )
        HomeDestination.Members -> {
            MembersScreen(
                members = memberList,
                onHomeClick = { destination = HomeDestination.Dashboard },
                onInviteClick = { destination = HomeDestination.InviteLinks },
                onMemberClick = { member ->
                    viewingMemberId = member.memberId
                    destination = HomeDestination.MemberDetail
                },
                joinRequests = joinRequests,
                onApprove = { request -> scope.launch { joining?.approve(request.homeId, request.memberId) } },
                onDecline = { request -> scope.launch { joining?.decline(request.homeId, request.memberId) } },
            )
        }
        HomeDestination.MemberDetail -> {
            val member = memberList.firstOrNull { it.memberId == viewingMemberId }
            if (member == null) {
                // Gone (removed, or never there): back to the list.
                LaunchedEffect(Unit) { destination = HomeDestination.Members }
            } else {
                // Reads the roster each time the member's list changes, so a device they just added shows up.
                val agents by produceState(emptyList<String>(), member.memberId, memberList) { value = memberAgents(member.memberId) }
                MemberDetailScreen(
                    member = member,
                    agents = agents,
                    canManage = isAdmin,
                    onBack = { destination = HomeDestination.Members },
                    onSetRole = { m, role -> scope.launch { onSetRole(m.memberId, role) } },
                    onRemoveMember = { m ->
                        destination = HomeDestination.Members
                        scope.launch { onRemoveMember(m.memberId) }
                    },
                )
            }
        }
        HomeDestination.InviteLinks -> {
            val linksViewModel = viewModel(key = "invite-links-$homeId") { InviteLinksViewModel(homeId, joining) }
            InviteLinksScreen(linksViewModel, onBack = { destination = HomeDestination.Members })
        }
        HomeDestination.HubManager -> {
            val hubViewModel = viewModel(key = "hub-$homeId") { HubViewModel(homeId, hubs, offerOwnDevice = ownDeviceCanBeHub) }
            HubManagerScreen(
                viewModel = hubViewModel,
                onHowHubsWork = { destination = HomeDestination.HubTutorial },
                onSetUpHub = { destination = HomeDestination.Hub },
                onBack = { destination = HomeDestination.Dashboard },
            )
        }
        HomeDestination.HubTutorial -> HubTutorialScreen(
            onFinish = { destination = HomeDestination.Hub },
            onBack = { destination = HomeDestination.HubManager },
        )
        HomeDestination.Hub -> {
            val hubViewModel = viewModel(key = "hub-$homeId") { HubViewModel(homeId, hubs, offerOwnDevice = ownDeviceCanBeHub) }
            HubScreen(hubViewModel, onBack = { destination = HomeDestination.HubManager })
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
        HomeDestination.ShoppingList -> {
            val onItemSelected = { item: ShoppingListItem ->
                editingShoppingItem = item.toDetails()
                destination = HomeDestination.ShoppingItemEdit
            }
            val onAddItem = {
                editingShoppingItem = null
                destination = HomeDestination.ShoppingItemEdit
            }
            val onBack = { destination = HomeDestination.Dashboard }
            if (shoppingViewModel != null) {
                ShoppingListScreen(shoppingViewModel, onItemSelected = onItemSelected, onBack = onBack, onAddItem = onAddItem)
            } else {
                ShoppingListScreen(items = emptyList(), onItemSelected = onItemSelected, onBack = onBack, onAddItem = onAddItem)
            }
        }
        HomeDestination.ShoppingItemEdit -> {
            val item = editingShoppingItem
            PantryItemEditScreen(
                item = item?.toPantryDetails(),
                categories = categoriesState?.value?.categories.orEmpty(),
                labels = ShoppingItemFormLabels,
                suggestions = pantryState?.value?.items.orEmpty(),
                onEditCategories = { draft ->
                    editingShoppingItem = draft.toShoppingDetails()
                    categoriesBackTo = HomeDestination.ShoppingItemEdit
                    destination = HomeDestination.PantryCategories
                },
                onEditPackage = { draft ->
                    editingShoppingItem = draft.toShoppingDetails()
                    destination = HomeDestination.ShoppingItemEditPackage
                },
                onDelete = { key ->
                    shoppingViewModel?.remove(key)
                    destination = HomeDestination.ShoppingList
                },
                onSave = { draft ->
                    shoppingViewModel?.save(draft.toShoppingDetails())
                    destination = HomeDestination.ShoppingList
                },
                onCancel = { destination = HomeDestination.ShoppingList },
            )
        }
        HomeDestination.ShoppingItemEditPackage -> {
            val draft = editingShoppingItem ?: ShoppingItemDetails()
            val backToForm = { destination = HomeDestination.ShoppingItemEdit }
            PantryItemEditPackageScreen(
                itemName = draft.title,
                initialValue = draft.packageSize,
                startLoose = draft.key != null,
                onDone = { size ->
                    editingShoppingItem = draft.copy(packageSize = size)
                    backToForm()
                },
                onBack = backToForm,
            )
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

private enum class HomeDestination {
    Dashboard, Members, MemberDetail, InviteLinks, HubManager, HubTutorial, Hub, Pantry, PantryCategories, PantryItemEdit, PantryItemEditPackage,
    ShoppingList, ShoppingItemEdit, ShoppingItemEditPackage,
}

private fun ShoppingListItem.toDetails() = ShoppingItemDetails(
    key = key, primaryCategory = primaryCategory, secondaryCategory = secondaryCategory, title = title,
    quantity = quantity, packageSize = packageSize, barcode = barcode,
)

// The shopping-list form is the Pantry's item form, with the quantity carried in the stock-count field.
private fun ShoppingItemDetails.toPantryDetails() = PantryItemDetails(
    key = key, primaryCategory = primaryCategory, secondaryCategory = secondaryCategory, title = title,
    packageSize = packageSize, barcode = barcode, stockCount = quantity,
)

private fun PantryItemDetails.toShoppingDetails() = ShoppingItemDetails(
    key = key, primaryCategory = primaryCategory, secondaryCategory = secondaryCategory, title = title,
    quantity = stockCount, packageSize = packageSize, barcode = barcode,
)

/**
 * [App] backed by the local database on this platform; what the platform entry points launch. With
 * [openPantry] (platforms that have the Pantry Automerge document layer), each Home's Pantry is loaded
 * from and saved to the database; without it the Pantry is not persisted. [backgroundServices], if given,
 * runs for as long as the app is shown, with the database (created here, so callers can't have it earlier).
 * [dataSync], if given, builds the sync of the user's data (Homes, Pantries) between their paired devices from the
 * database, the x0x client and the device list; it is fed Home changes and run for as long as the app is shown.
 * [ownDeviceCanBeHub] lets a user make this device an always-on hub of a Home (docs/adr/ADR-020, several-hubs amendment): true where
 * the x0x daemon keeps running when the app is closed, as on the desktop, not where it is embedded and stops with the app.
 */
@Composable
fun VestaApp(
    openPantry: (suspend (VestaDatabase, homeId: String) -> PantrySource)? = null,
    openShoppingList: (suspend (VestaDatabase, homeId: String) -> ShoppingListSource)? = null,
    homeContent: (@Composable (homeId: String, homeName: String, onEditHome: () -> Unit) -> Unit)? = null,
    backgroundServices: (suspend CoroutineScope.(VestaDatabase) -> Unit)? = null,
    dataSync: ((VestaDatabase, X0xClient, SyncedDeviceRepository) -> DeviceDataSync)? = null,
    ownDeviceCanBeHub: Boolean = false,
) {
    val database = remember { createVestaDatabase(DatabaseDriverFactory()) }
    LaunchedEffect(database) { backgroundServices?.invoke(this, database) }
    val client = remember { createX0xClient() }
    val devices = remember { SyncedDeviceRepository(database) }
    val deviceDataSync = remember { dataSync?.invoke(database, client, devices) }
    LaunchedEffect(deviceDataSync) { deviceDataSync?.run() }
    // Coming back to the app is a moment other devices may be up (ADR-020), so sync then, not at the next timer round.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { deviceDataSync?.syncNow() }
    val profiles = remember { ProfileRepository(database, changes = deviceDataSync?.profileChanges) }
    val homeRepository = remember { HomeRepository(database, deviceDataSync?.homeChanges, profiles, deviceDataSync?.rosterChanges) }
    val memberRepository = remember { MemberRepository(database, profiles, deviceDataSync?.rosterChanges) }
    val agentAccess = remember { AgentAccessRepository(database) }
    val deviceSync = remember {
        val service = DeviceSyncService(
            client, database, devices,
            onDeviceUnpaired = { agentId -> deviceDataSync?.deviceUnpaired(agentId) },
            onDevicePaired = { agentId -> deviceDataSync?.devicePaired(agentId) },
        )
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
        homeJoining = deviceDataSync?.joining,
        profiles = profiles,
        homeContent = homeContent ?: { homeId, homeName, onEditHome ->
            val pantrySource by produceState<PantrySource?>(null, homeId) { value = openPantry?.invoke(database, homeId) }
            val shoppingListSource by produceState<ShoppingListSource?>(null, homeId) { value = openShoppingList?.invoke(database, homeId) }
            val members = remember(homeId) { memberRepository.members(homeId) }
            HomeContent(
                homeId, homeName, pantrySource, shoppingListSource, members,
                onLeave = { homeRepository.leave(homeId) },
                onSetRole = { memberId, role -> memberRepository.setRole(homeId, memberId, role) },
                onRemoveMember = { memberId -> memberRepository.removeMember(homeId, memberId) },
                memberAgents = { memberId -> deviceDataSync?.memberAgents(homeId, memberId).orEmpty() },
                onEditHome = onEditHome,
                onDeleteHome = { homeRepository.delete(homeId) },
                joining = deviceDataSync?.joining,
                hubs = deviceDataSync?.hubs,
                ownDeviceCanBeHub = ownDeviceCanBeHub,
                onOpened = { deviceDataSync?.syncNow() },
            )
        },
    )
}
