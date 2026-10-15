package uk.veritaslabs.vesta

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import uk.veritaslabs.vesta.ui.components.LocalUndo
import uk.veritaslabs.vesta.ui.components.UndoController
import vesta.shared.generated.resources.common_undo
import androidx.compose.runtime.Composable
import uk.veritaslabs.vesta.blobs.LocalBlobs
import uk.veritaslabs.vesta.files.FileExporter
import uk.veritaslabs.vesta.files.FileImporter
import uk.veritaslabs.vesta.files.LocalFileImporter
import uk.veritaslabs.vesta.files.LocalFileExporter
import uk.veritaslabs.vesta.recipes.LocalRecipeImporter
import uk.veritaslabs.vesta.recipes.RecipeImporter
import uk.veritaslabs.vesta.blobs.BlobRuntime
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
import androidx.compose.foundation.layout.safeDrawingPadding
import org.jetbrains.compose.resources.stringResource
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.pantry_item_deleted
import vesta.shared.generated.resources.shopping_item_deleted
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.agent.AssistantRepository
import uk.veritaslabs.vesta.agent.AgentApprovalHost
import uk.veritaslabs.vesta.agent.AssistantEditHost
import uk.veritaslabs.vesta.agent.AssistantsListHost
import uk.veritaslabs.vesta.db.DatabaseDriverFactory
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.createVestaDatabase
import uk.veritaslabs.vesta.licenses.OpenSourceLicense
import uk.veritaslabs.vesta.licenses.loadOpenSourceLicenses
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
import uk.veritaslabs.vesta.ui.flows.CookbookFlow
import uk.veritaslabs.vesta.ui.flows.MerchantsFlow
import uk.veritaslabs.vesta.calendar.CalendarSettings
import uk.veritaslabs.vesta.calendar.CalendarSettingsController
import uk.veritaslabs.vesta.calendar.LocalCalendarSettings
import uk.veritaslabs.vesta.ui.screens.AddedToPantryScreen
import uk.veritaslabs.vesta.ui.flows.CalendarFlow
import uk.veritaslabs.vesta.ui.flows.TasksFlow
import uk.veritaslabs.vesta.ui.screens.TasksScreen
import uk.veritaslabs.vesta.ui.screens.TasksViewModel
import uk.veritaslabs.vesta.ui.screens.TasksSummary
import uk.veritaslabs.vesta.ui.screens.tasksSummary
import uk.veritaslabs.vesta.tasks.TaskSource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.today
import uk.veritaslabs.vesta.ui.screens.CalendarScreen
import uk.veritaslabs.vesta.ui.screens.CalendarSource
import uk.veritaslabs.vesta.ui.screens.CalendarViewModel
import uk.veritaslabs.vesta.ui.screens.CalendarSettingsScreen
import uk.veritaslabs.vesta.ui.screens.CookbookScreen
import uk.veritaslabs.vesta.ui.screens.CookbookSource
import uk.veritaslabs.vesta.ui.screens.CookbookViewModel
import uk.veritaslabs.vesta.ui.screens.MealPlanSource
import uk.veritaslabs.vesta.ui.screens.MealPlannerScreen
import uk.veritaslabs.vesta.ui.screens.MealPlannerViewModel
import uk.veritaslabs.vesta.ui.flows.MealPlannerFlow
import uk.veritaslabs.vesta.ui.screens.MarketplaceScreen
import uk.veritaslabs.vesta.ui.screens.MerchantsScreen
import uk.veritaslabs.vesta.ui.screens.MerchantsSource
import uk.veritaslabs.vesta.ui.screens.MerchantsViewModel
import uk.veritaslabs.vesta.syncactivity.SyncActivityRepository
import uk.veritaslabs.vesta.syncactivity.SyncActivityViewModel
import uk.veritaslabs.vesta.syncactivity.UnavailableSyncActivityRepository
import uk.veritaslabs.vesta.ui.screens.HubManagerScreen
import uk.veritaslabs.vesta.ui.screens.SyncActivityScreen
import uk.veritaslabs.vesta.ui.screens.HubScreen
import uk.veritaslabs.vesta.ui.screens.HubTutorialScreen
import uk.veritaslabs.vesta.ui.screens.MemberAgent
import uk.veritaslabs.vesta.ui.screens.NetworkDiagnosticsScreen
import uk.veritaslabs.vesta.ui.screens.NetworkPeersScreen
import uk.veritaslabs.vesta.ui.screens.OnboardingMode
import uk.veritaslabs.vesta.ui.screens.OnboardingScreen
import uk.veritaslabs.vesta.ui.screens.OpenSourceLicensesScreen
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
import uk.veritaslabs.vesta.ui.screens.PantryAccent
import uk.veritaslabs.vesta.ui.screens.ShoppingAccent
import uk.veritaslabs.vesta.ui.screens.ShoppingItemFormLabels
import uk.veritaslabs.vesta.ui.screens.ShoppingListItem
import uk.veritaslabs.vesta.ui.screens.shoppingTags
import uk.veritaslabs.vesta.ui.screens.ShoppingListFilterScreen
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
import uk.veritaslabs.vesta.settings.AppSettingsRepository
import uk.veritaslabs.vesta.ui.theme.AppTheme
import uk.veritaslabs.vesta.ui.theme.ThemeMode
import uk.veritaslabs.vesta.x0x.X0xConnectivity
import uk.veritaslabs.vesta.x0x.isReachingNetwork
import uk.veritaslabs.vesta.x0x.rememberX0xConnectivity
import uk.veritaslabs.vesta.x0x.rememberX0xNetworkDiagnostics
import uk.veritaslabs.vesta.x0x.rememberX0xPeers

/** The app-wide currently selected Home; read `LocalSelectedHome.current.homeId` from any composable under [App]. */
val LocalSelectedHome = compositionLocalOf<SelectedHome> { error("No SelectedHome provided; call within App()") }

/**
 * Which Home the edit screen is open on: [homeId] is null when creating a new Home. [session] is unique
 * per opening, so each visit gets a fresh view model rather than a finished one left in the store.
 */
private data class HomeEditTarget(val homeId: String?, val session: Int)

/** The app-wide settings screens, reached from the Home picker. */
/** An assistant open in the editor; a null [agentId] is a new one. */
private data class AssistantEditTarget(val agentId: String?)

private enum class SettingsDestination { Settings, Assistants, SyncedDevices, ProfileName, Peers, NetworkDiagnostics, OpenSourceLicenses }

/**
 * App root. Shows the Home picker until a Home is selected, then [homeContent] for that Home's id and name, and an
 * `onEditHome` that opens the Home's editor over it.
 * [homes] is the list the picker offers. With a [homeRepository], the picker's create/edit/delete
 * actions are live; without one (previews) they do nothing. With [assistants], an agent waiting for the user's
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
    assistants: AssistantRepository? = null,
    deviceSync: DeviceSyncHost? = null,
    homeJoining: HomeJoining? = null,
    profiles: ProfileRepository? = null,
    settings: AppSettingsRepository? = null,
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
    // The assistant being added (null id) or edited, shown over everything so a card's Review… works from inside a Home.
    var assistantEdit by remember { mutableStateOf<AssistantEditTarget?>(null) }
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
    // Follows the system until the user picks light or dark in Settings; saved in [settings] when there is one.
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    var appTheme by remember { mutableStateOf(AppTheme.Vesta) }
    var useMobileData by remember { mutableStateOf(X0xNetworkPolicy.useMobileData.value) }
    // Until the saved settings have been read there is nothing to draw, rather than a flash of the default theme.
    var settingsLoaded by remember(settings) { mutableStateOf(settings == null) }
    // The welcome tour, when it is showing: FirstLaunch until it has been finished once, Revisit when opened again from Settings.
    var onboarding by remember { mutableStateOf<OnboardingMode?>(null) }
    LaunchedEffect(settings) {
        settings?.appearance()?.let { saved ->
            themeMode = saved.themeMode
            appTheme = saved.appTheme
            useMobileData = settings.useMobileData()
            if (settings.onboardingSeen() == null) {
                // Only new installs get the tour: anyone who already has a Home or a chosen name was here before it existed.
                val existingInstall = homes.first().isNotEmpty() || profiles?.observe()?.filterNotNull()?.first()?.named == true
                if (existingInstall) settings.setOnboardingSeen(true) else onboarding = OnboardingMode.FirstLaunch
            }
            settingsLoaded = true
        }
    }
    val darkTheme = themeMode.isDark(isSystemInDarkTheme())
    // On until the user opts out; saved in [settings]. Platforms that run x0x in-process stop it on mobile data unless this is on.
    // The policy is only set once the saved choice is known, so a saved "off" is never briefly overridden by the default.
    LaunchedEffect(useMobileData, settingsLoaded) { if (settingsLoaded) X0xNetworkPolicy.useMobileData.value = useMobileData }
    val snackbarHost = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.common_undo)
    val undo = remember(snackbarHost, scope, undoLabel) { UndoController(snackbarHost, scope, undoLabel) }
    CompositionLocalProvider(LocalSelectedHome provides selectedHome, LocalUndo provides undo) {
        VestaTheme(darkTheme = darkTheme, appTheme = appTheme) {
            if (!settingsLoaded) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                return@VestaTheme
            }
            Box(modifier = Modifier.fillMaxSize()) {
                val currentProfile = profile
                val tourMode = onboarding
                if (tourMode != null) {
                    OnboardingScreen(
                        mode = tourMode,
                        onFinish = {
                            onboarding = null
                            scope.launch { settings?.setOnboardingSeen(true) }
                        },
                    )
                } else if (profiles != null && currentProfile == null) {
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
                } else if (assistantEdit != null && assistants != null) {
                    AssistantEditHost(assistants, assistantEdit?.agentId, homeList, onDone = { assistantEdit = null })
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
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                scope.launch { settings?.setThemeMode(it) }
                            },
                            appTheme = appTheme,
                            onAppThemeChange = {
                                appTheme = it
                                scope.launch { settings?.setAppTheme(it) }
                            },
                            useMobileData = useMobileData,
                            onUseMobileDataChange = {
                                useMobileData = it
                                scope.launch { settings?.setUseMobileData(it) }
                            },
                            onSyncedDevicesClick = { settingsDestination = SettingsDestination.SyncedDevices },
                            onAssistantsClick = assistants?.let { { settingsDestination = SettingsDestination.Assistants } },
                            onNetworkStatusClick = { settingsDestination = SettingsDestination.NetworkDiagnostics },
                            onPeersClick = { settingsDestination = SettingsDestination.Peers },
                            onOpenSourceLicensesClick = { settingsDestination = SettingsDestination.OpenSourceLicenses },
                            onWelcomeTourClick = { onboarding = OnboardingMode.Revisit },
                            profileName = profile?.displayName,
                            onProfileNameClick = { settingsDestination = SettingsDestination.ProfileName },
                            onBack = { settingsDestination = null },
                        )
                    } else if (settingsDestination == SettingsDestination.Assistants && assistants != null) {
                        AssistantsListHost(
                            assistants,
                            onAdd = { assistantEdit = AssistantEditTarget(null) },
                            onOpen = { assistantEdit = AssistantEditTarget(it) },
                            onBack = { settingsDestination = SettingsDestination.Settings },
                        )
                    } else if (settingsDestination == SettingsDestination.NetworkDiagnostics) {
                        NetworkDiagnosticsScreen(
                            diagnostics = rememberX0xNetworkDiagnostics(),
                            onBack = { settingsDestination = SettingsDestination.Settings },
                        )
                    } else if (settingsDestination == SettingsDestination.OpenSourceLicenses) {
                        val licenses by produceState(emptyList<OpenSourceLicense>()) { value = loadOpenSourceLicenses() }
                        OpenSourceLicensesScreen(
                            licenses = licenses,
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
                // Not over the assistant editor: the card would sit on top of its buttons.
                assistants?.takeIf { assistantEdit == null }?.let {
                    AgentApprovalHost(
                        it,
                        onReview = { agentId -> assistantEdit = AssistantEditTarget(agentId) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                        homes = homes,
                    )
                }
                SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
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
    cookbookSource: CookbookSource? = null,
    merchantsSource: MerchantsSource? = null,
    mealPlanSource: MealPlanSource? = null,
    calendarSource: CalendarSource? = null,
    privateCalendarSource: CalendarSource? = null,
    tasksSource: TaskSource? = null,
    privateTasksSource: TaskSource? = null,
    members: Flow<List<MemberListItem>> = flowOf(emptyList()),
    onLeave: suspend () -> LeaveResult = { LeaveResult.Left },
    onSetRole: suspend (memberId: String, role: MemberRole) -> Unit = { _, _ -> },
    onRemoveMember: suspend (memberId: String) -> Unit = {},
    memberAgents: suspend (memberId: String) -> List<MemberAgent> = { emptyList() },
    onEditHome: () -> Unit = {},
    onDeleteHome: suspend () -> Unit = {},
    joining: HomeJoining? = null,
    hubs: HomeHub? = null,
    syncActivity: SyncActivityRepository = UnavailableSyncActivityRepository,
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
    val undo = LocalUndo.current
    val itemDeletedMessage = stringResource(Res.string.pantry_item_deleted)
    val shoppingDeletedMessage = stringResource(Res.string.shopping_item_deleted)
    // The recipe a cookbook visit opens on, when it was opened from somewhere with a recipe in hand (the meal planner), and where back goes.
    var cookbookRecipeKey by remember { mutableStateOf<String?>(null) }
    val pantryViewModel = pantrySource?.let { source ->
        viewModel(key = "pantry-$homeId") { PantryViewModel(source) }
    }
    val shoppingViewModel = shoppingListSource?.let { source ->
        viewModel(key = "shopping-$homeId") { ShoppingListViewModel(source) }
    }
    var editingShoppingItem by remember { mutableStateOf<ShoppingItemDetails?>(null) }
    var addedToPantryCount by remember { mutableStateOf(0) }
    val merchantList by remember(merchantsSource) { merchantsSource?.merchants ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    val cookbookViewModel = cookbookSource?.let { source ->
        val pantryItems = pantrySource?.contents?.map { it.items } ?: flowOf(emptyList())
        viewModel(key = "cookbook-$homeId") { CookbookViewModel(source, pantryItems) }
    }
    val mealPlannerViewModel = mealPlanSource?.let { source ->
        val recipes = cookbookSource?.recipes ?: flowOf(emptyList())
        viewModel(key = "meal-planner-$homeId") { MealPlannerViewModel(source, recipes, members) }
    }
    val calendarViewModel = if (calendarSource != null && privateCalendarSource != null) {
        viewModel(key = "calendar-$homeId") { CalendarViewModel(calendarSource, privateCalendarSource, members) }
    } else {
        null
    }
    val tasksViewModel = if (tasksSource != null && privateTasksSource != null) {
        viewModel(key = "tasks-$homeId") { TasksViewModel(tasksSource, privateTasksSource, members) }
    } else {
        null
    }
    val merchantsViewModel = merchantsSource?.let { source ->
        viewModel(key = "merchants-$homeId") { MerchantsViewModel(source) }
    }
    val categoriesViewModel = pantrySource?.categoriesSource?.let { source ->
        viewModel(key = "pantry-categories-$homeId") { PantryCategoriesViewModel(source) }
    }
    val pantryState = pantryViewModel?.uiState?.collectAsStateWithLifecycle()
    val categoriesState = categoriesViewModel?.uiState?.collectAsStateWithLifecycle()
    val shoppingState = shoppingViewModel?.uiState?.collectAsStateWithLifecycle()
    val calendarState = calendarViewModel?.uiState?.collectAsStateWithLifecycle()
    val tasksState = tasksViewModel?.uiState?.collectAsStateWithLifecycle()
    // Where the categories screen's back arrow returns to: the Pantry, or the item form it was opened from.
    var categoriesBackTo by remember { mutableStateOf(HomeDestination.Pantry) }
    // Where the Sync activity screen's back arrow returns to: the dashboard or the hub manager it was opened from.
    var syncActivityBackTo by remember { mutableStateOf(HomeDestination.Dashboard) }
    when (destination) {
        HomeDestination.Dashboard -> HomeDashboardScreen(
            homeName = homeName,
            onBack = { selectedHome.clear() },
            x0xConnectivity = rememberX0xConnectivity(),
            onPantryClick = { destination = HomeDestination.Pantry },
            onShoppingClick = { destination = HomeDestination.ShoppingList },
            onCookbookClick = { destination = HomeDestination.Cookbook },
            onMealPlannerClick = { destination = HomeDestination.MealPlanner },
            onCalendarClick = { destination = HomeDestination.Calendar },
            onTasksClick = { destination = HomeDestination.Tasks },
            tasksSummary = tasksState?.value?.let { tasksSummary(it, CalendarDate.today()) } ?: TasksSummary(),
            onMarketplaceClick = { destination = HomeDestination.Marketplace },
            onMembersClick = { destination = HomeDestination.Members },
            isAdmin = isAdmin,
            membersBadge = if (isAdmin) joinRequests.count { !it.approved } else 0,
            onEditHome = onEditHome,
            onHubClick = { destination = HomeDestination.HubManager },
            onSyncActivityClick = {
                syncActivityBackTo = HomeDestination.Dashboard
                destination = HomeDestination.SyncActivity
            },
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
                val agents by produceState(emptyList<MemberAgent>(), member.memberId, memberList) { value = memberAgents(member.memberId) }
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
                onSeeActivity = {
                    syncActivityBackTo = HomeDestination.HubManager
                    destination = HomeDestination.SyncActivity
                },
            )
        }
        HomeDestination.SyncActivity -> {
            val syncActivityViewModel = viewModel(key = "sync-activity-$homeId") { SyncActivityViewModel(homeId, syncActivity) }
            SyncActivityScreen(
                syncActivityViewModel,
                homeName = homeName,
                onBack = { destination = syncActivityBackTo },
                onInvite = { destination = HomeDestination.InviteLinks },
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
                    merchants = merchantList,
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
        HomeDestination.Cookbook -> {
            if (cookbookViewModel != null) {
                CookbookFlow(
                    cookbookViewModel,
                    initialRecipeKey = cookbookRecipeKey,
                    onBack = {
                        destination = if (cookbookRecipeKey != null) HomeDestination.MealPlanner else HomeDestination.Dashboard
                        cookbookRecipeKey = null
                    },
                )
            } else {
                CookbookScreen(recipes = emptyList(), onBack = { destination = HomeDestination.Dashboard })
            }
        }
        HomeDestination.MealPlanner -> {
            if (mealPlannerViewModel != null) {
                MealPlannerFlow(
                    mealPlannerViewModel,
                    onViewRecipe = {
                        cookbookRecipeKey = it
                        destination = HomeDestination.Cookbook
                    },
                    onBack = { destination = HomeDestination.Dashboard },
                )
            } else {
                MealPlannerScreen(recipes = emptyList(), onBack = { destination = HomeDestination.Dashboard })
            }
        }
        HomeDestination.Marketplace -> MarketplaceScreen(
            onBack = { destination = HomeDestination.Dashboard },
            onLocalMerchantsClick = { destination = HomeDestination.Merchants },
        )
        HomeDestination.Merchants -> {
            if (merchantsViewModel != null) {
                MerchantsFlow(merchantsViewModel, onBack = { destination = HomeDestination.Marketplace })
            } else {
                MerchantsScreen(merchants = emptyList(), onBack = { destination = HomeDestination.Marketplace })
            }
        }
        HomeDestination.Calendar -> if (calendarViewModel != null) {
            CalendarFlow(
                calendarViewModel,
                homeName = homeName,
                onBack = { destination = HomeDestination.Dashboard },
                onSettingsClick = { destination = HomeDestination.CalendarSettings },
            )
        } else {
            CalendarScreen(
                onBack = { destination = HomeDestination.Dashboard },
                onSettingsClick = { destination = HomeDestination.CalendarSettings },
            )
        }
        HomeDestination.Tasks -> if (tasksViewModel != null) {
            TasksFlow(tasksViewModel, homeName = homeName, onBack = { destination = HomeDestination.Dashboard })
        } else {
            TasksScreen(tasks = emptyList(), onBack = { destination = HomeDestination.Dashboard })
        }
        HomeDestination.CalendarSettings -> {
            val calendarSettings = LocalCalendarSettings.current
            CalendarSettingsScreen(
                calendarSettings.settings,
                calendarSettings.onChange,
                events = calendarState?.value?.events.orEmpty(),
                homeName = homeName,
                onImport = { calendar, imported -> calendarViewModel?.import(calendar, imported) },
                onBack = { destination = HomeDestination.Calendar },
            )
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
                ShoppingListScreen(
                    shoppingViewModel,
                    onItemSelected = onItemSelected,
                    onBack = onBack,
                    onAddItem = onAddItem,
                    onOpenFilter = { destination = HomeDestination.ShoppingListFilter },
                    merchants = merchantList,
                    pantryItems = pantryState?.value?.items.orEmpty(),
                    onAddedToPantry = { count ->
                        addedToPantryCount = count
                        destination = HomeDestination.ShoppingAddedToPantry
                    },
                )
            } else {
                ShoppingListScreen(items = emptyList(), onItemSelected = onItemSelected, onBack = onBack, onAddItem = onAddItem)
            }
        }
        HomeDestination.ShoppingAddedToPantry -> AddedToPantryScreen(
            addedCount = addedToPantryCount,
            onClose = { destination = HomeDestination.ShoppingList },
        )
        HomeDestination.ShoppingListFilter -> {
            val onBack = { destination = HomeDestination.ShoppingList }
            if (shoppingViewModel != null) {
                ShoppingListFilterScreen(shoppingViewModel, onBack = onBack)
            } else {
                ShoppingListFilterScreen(
                    tags = emptyList(), selectedTags = emptySet(), onToggleTag = {}, hideChecked = false,
                    onHideCheckedChange = {}, onBack = onBack,
                )
            }
        }
        HomeDestination.ShoppingItemEdit -> {
            val item = editingShoppingItem
            PantryItemEditScreen(
                item = item?.toPantryDetails(),
                categories = categoriesState?.value?.categories.orEmpty(),
                labels = ShoppingItemFormLabels,
                knownTags = shoppingTags(shoppingState?.value?.items.orEmpty()),
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
                    editingShoppingItem?.let { details ->
                        undo?.offer(shoppingDeletedMessage) { shoppingViewModel?.restore(details) }
                    }
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
            merchants = merchantsSource?.let { merchantList },
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
                editingItem?.let { item -> undo?.offer(itemDeletedMessage) { pantryViewModel?.save(item) } }
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
    merchantKeys = merchantKeys,
)

/** What the UI needs of device sync: the stored [devices], the [service] that keeps them in step, and a pairing view model factory. */
class DeviceSyncHost(
    val devices: SyncedDeviceRepository,
    val service: DeviceSyncService,
    val newPairingViewModel: () -> DevicePairingViewModel,
)

private enum class HomeDestination {
    Dashboard, Members, MemberDetail, InviteLinks, HubManager, SyncActivity, HubTutorial, Hub, Pantry, PantryCategories, PantryItemEdit, PantryItemEditPackage,
    ShoppingList, ShoppingListFilter, ShoppingAddedToPantry, ShoppingItemEdit, ShoppingItemEditPackage, Cookbook, MealPlanner, Marketplace, Merchants, Calendar, CalendarSettings, Tasks,
}

internal fun ShoppingListItem.toDetails() = ShoppingItemDetails(
    key = key, primaryCategory = primaryCategory, secondaryCategory = secondaryCategory, title = title,
    quantity = quantity, packageSize = packageSize, barcode = barcode, tags = tags,
)

// The shopping-list form is the Pantry's item form, with the quantity carried in the stock-count field.
internal fun ShoppingItemDetails.toPantryDetails() = PantryItemDetails(
    key = key, primaryCategory = primaryCategory, secondaryCategory = secondaryCategory, title = title,
    packageSize = packageSize, barcode = barcode, stockCount = quantity, tags = tags,
)

internal fun PantryItemDetails.toShoppingDetails() = ShoppingItemDetails(
    key = key, primaryCategory = primaryCategory, secondaryCategory = secondaryCategory, title = title,
    quantity = stockCount, packageSize = packageSize, barcode = barcode, tags = tags,
)

/**
 * [App] backed by the local database on this platform; what the platform entry points launch. With
 * [openPantry] (platforms that have the Pantry Automerge document layer), each Home's Pantry is loaded
 * from and saved to the database; without it the Pantry is not persisted. [backgroundServices], if given,
 * runs for as long as the app is shown, with the database (created here, so callers can't have it earlier).
 * [dataSync], if given, builds the sync of the user's data (Homes, Pantries) between their paired devices from the
 * database, the x0x client and the device list; it is fed Home changes and run for as long as the app is shown.
 * [blobRuntime], if given, builds the storage and transfer of photos from the same three; screens get it through [LocalBlobs].
 * [fileExporter], if given, lets screens save a file the user chooses a place for (an event as iCal); screens get it through [LocalFileExporter].
 * [fileImporter], if given, lets screens read a file the user chooses (iCal events into the calendar); through [LocalFileImporter].
 * [recipeImporter], if given, lets the add-recipe form fill itself from a web link; screens get it through [LocalRecipeImporter].
 * [ownDeviceCanBeHub] lets a user make this device an always-on hub of a Home (docs/adr/ADR-020, several-hubs amendment): true where
 * the x0x daemon keeps running when the app is closed, as on the desktop, not where it is embedded and stops with the app.
 */
@Composable
fun VestaApp(
    openPantry: (suspend (VestaDatabase, homeId: String) -> PantrySource)? = null,
    openShoppingList: (suspend (VestaDatabase, homeId: String) -> ShoppingListSource)? = null,
    openCookbook: (suspend (VestaDatabase, homeId: String) -> CookbookSource)? = null,
    openMerchants: (suspend (VestaDatabase, homeId: String) -> MerchantsSource)? = null,
    openMealPlan: (suspend (VestaDatabase, homeId: String) -> MealPlanSource)? = null,
    openCalendar: (suspend (VestaDatabase, homeId: String) -> CalendarSource)? = null,
    openPrivateCalendar: (suspend (VestaDatabase, homeId: String) -> CalendarSource)? = null,
    openTasks: (suspend (VestaDatabase, homeId: String) -> TaskSource)? = null,
    openPrivateTasks: (suspend (VestaDatabase, homeId: String) -> TaskSource)? = null,
    homeContent: (@Composable (homeId: String, homeName: String, onEditHome: () -> Unit) -> Unit)? = null,
    backgroundServices: (suspend CoroutineScope.(VestaDatabase) -> Unit)? = null,
    assistantsFor: ((VestaDatabase) -> AssistantRepository)? = null,
    dataSync: ((VestaDatabase, X0xClient, SyncedDeviceRepository) -> DeviceDataSync)? = null,
    blobRuntime: ((VestaDatabase, X0xClient, DeviceDataSync?) -> BlobRuntime)? = null,
    recipeImporter: RecipeImporter? = null,
    fileExporter: FileExporter? = null,
    fileImporter: FileImporter? = null,
    ownDeviceCanBeHub: Boolean = false,
) {
    val database = remember { createVestaDatabase(DatabaseDriverFactory()) }
    LaunchedEffect(database) { backgroundServices?.invoke(this, database) }
    val client = remember { createX0xClient() }
    val devices = remember { SyncedDeviceRepository(database) }
    val deviceDataSync = remember { dataSync?.invoke(database, client, devices) }
    LaunchedEffect(deviceDataSync) { deviceDataSync?.run() }
    // Photos (ADR-023): the runtime moves them between devices while the app is shown, and the screens reach it through LocalBlobs.
    val blobs = remember { blobRuntime?.invoke(database, client, deviceDataSync) }
    LaunchedEffect(blobs) { blobs?.run() }
    // Coming back to the app is a moment other devices may be up (ADR-020), so sync then, not at the next timer round.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { deviceDataSync?.syncNow() }
    val profiles = remember { ProfileRepository(database, changes = deviceDataSync?.profileChanges) }
    val homeRepository = remember { HomeRepository(database, deviceDataSync?.homeChanges, profiles, deviceDataSync?.rosterChanges) }
    val memberRepository = remember { MemberRepository(database, profiles, deviceDataSync?.rosterChanges) }
    val assistants = remember { assistantsFor?.invoke(database) ?: AssistantRepository(database) }
    val settings = remember { AppSettingsRepository(database) }
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
    val calendarScope = rememberCoroutineScope()
    var calendarSettings by remember { mutableStateOf(CalendarSettings()) }
    LaunchedEffect(settings) { calendarSettings = settings.calendarSettings() }
    val calendarController = remember(calendarSettings) {
        CalendarSettingsController(calendarSettings) { changed ->
            calendarSettings = changed
            calendarScope.launch { settings.setCalendarSettings(changed) }
        }
    }
    CompositionLocalProvider(
        LocalBlobs provides blobs?.blobs,
        LocalRecipeImporter provides recipeImporter,
        LocalFileExporter provides fileExporter,
        LocalFileImporter provides fileImporter,
        LocalCalendarSettings provides calendarController,
    ) {
        App(
            homes = homeRepository.homes,
            homeRepository = homeRepository,
            assistants = assistants,
            deviceSync = deviceSync,
            homeJoining = deviceDataSync?.joining,
            profiles = profiles,
            settings = settings,
            homeContent = homeContent ?: { homeId, homeName, onEditHome ->
                val pantrySource by produceState<PantrySource?>(null, homeId) { value = openPantry?.invoke(database, homeId) }
                val shoppingListSource by produceState<ShoppingListSource?>(null, homeId) { value = openShoppingList?.invoke(database, homeId) }
                val cookbookSource by produceState<CookbookSource?>(null, homeId) { value = openCookbook?.invoke(database, homeId) }
                val merchantsSource by produceState<MerchantsSource?>(null, homeId) { value = openMerchants?.invoke(database, homeId) }
                val mealPlanSource by produceState<MealPlanSource?>(null, homeId) { value = openMealPlan?.invoke(database, homeId) }
                val calendarSource by produceState<CalendarSource?>(null, homeId) { value = openCalendar?.invoke(database, homeId) }
                val privateCalendarSource by produceState<CalendarSource?>(null, homeId) { value = openPrivateCalendar?.invoke(database, homeId) }
                val tasksSource by produceState<TaskSource?>(null, homeId) { value = openTasks?.invoke(database, homeId) }
                val privateTasksSource by produceState<TaskSource?>(null, homeId) { value = openPrivateTasks?.invoke(database, homeId) }
                val members = remember(homeId) { memberRepository.members(homeId) }
                HomeContent(
                    homeId, homeName, pantrySource, shoppingListSource, cookbookSource, merchantsSource, mealPlanSource,
                    calendarSource, privateCalendarSource, tasksSource, privateTasksSource, members,
                    onLeave = { homeRepository.leave(homeId) },
                    onSetRole = { memberId, role -> memberRepository.setRole(homeId, memberId, role) },
                    onRemoveMember = { memberId -> memberRepository.removeMember(homeId, memberId) },
                    memberAgents = { memberId ->
                        deviceDataSync?.let { sync ->
                            sync.memberAgents(homeId, memberId).map { agentId ->
                                val details = sync.agentDetails(homeId, agentId)
                                MemberAgent(agentId, details.name, details.machineId, details.lastSeenMsAgo, details.lastSyncedMsAgo)
                            }
                        }.orEmpty()
                    },
                    onEditHome = onEditHome,
                    onDeleteHome = { homeRepository.delete(homeId) },
                    joining = deviceDataSync?.joining,
                    hubs = deviceDataSync?.hubs,
                    syncActivity = deviceDataSync?.syncActivity ?: UnavailableSyncActivityRepository,
                    ownDeviceCanBeHub = ownDeviceCanBeHub,
                    onOpened = { deviceDataSync?.syncNow() },
                )
            },
        )
    }
}
