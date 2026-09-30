package uk.veritaslabs.vesta

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
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.SelectedHome
import uk.veritaslabs.vesta.ui.screens.HomeDashboardScreen
import uk.veritaslabs.vesta.ui.screens.HomeEditScreen
import uk.veritaslabs.vesta.ui.screens.HomeEditViewModel
import uk.veritaslabs.vesta.ui.screens.HomeListItem
import uk.veritaslabs.vesta.ui.screens.HomePickerScreen
import uk.veritaslabs.vesta.ui.screens.HomePickerViewModel
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails
import uk.veritaslabs.vesta.ui.screens.PantryItemEditScreen
import uk.veritaslabs.vesta.ui.screens.PantryListItem
import uk.veritaslabs.vesta.ui.screens.PantrySource
import uk.veritaslabs.vesta.ui.screens.PantryViewModel
import uk.veritaslabs.vesta.ui.screens.PantryScreen
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.rememberX0xConnectivity

/** The app-wide currently selected Home; read `LocalSelectedHome.current.homeId` from any composable under [App]. */
val LocalSelectedHome = compositionLocalOf<SelectedHome> { error("No SelectedHome provided; call within App()") }

/**
 * Which Home the edit screen is open on: [homeId] is null when creating a new Home. [session] is unique
 * per opening, so each visit gets a fresh view model rather than a finished one left in the store.
 */
private data class HomeEditTarget(val homeId: String?, val session: Int)

/**
 * App root. Shows the Home picker until a Home is selected, then [homeContent] for that Home's id and name.
 * [homes] is the list the picker offers. With a [homeRepository], the picker's create/edit/delete
 * actions are live; without one (previews) they do nothing. With [agentAccess], an agent waiting for the user's
 * approval is offered over whatever screen is showing.
 */
@Composable
@Preview
fun App(
    homes: Flow<List<HomeListItem>> = flowOf(emptyList()),
    selectedHome: SelectedHome = remember { SelectedHome() },
    homeRepository: HomeRepository? = null,
    agentAccess: AgentAccessRepository? = null,
    homeContent: @Composable (homeId: String, homeName: String) -> Unit = { id, name -> HomeContent(id, name) },
) {
    val homeId by selectedHome.homeId.collectAsStateWithLifecycle()
    val homeList by homes.collectAsStateWithLifecycle(emptyList())
    var editTarget by remember { mutableStateOf<HomeEditTarget?>(null) }
    var editSessions by remember { mutableIntStateOf(0) }
    fun openEditor(homeId: String?) {
        editTarget = HomeEditTarget(homeId, session = editSessions++)
    }
    val scope = rememberCoroutineScope()
    // Drop the selection if its Home goes away (deleted, or left).
    LaunchedEffect(homes, selectedHome) {
        homes.collect { list -> selectedHome.homeId.value?.let { id -> if (list.none { it.id == id }) selectedHome.clear() } }
    }
    CompositionLocalProvider(LocalSelectedHome provides selectedHome) {
        VestaTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                homeId?.let { id -> homeContent(id, homeList.firstOrNull { it.id == id }?.name.orEmpty()) } ?: run {
                    val target = editTarget
                    if (target != null && homeRepository != null) {
                        val editViewModel = viewModel(key = "home-edit-${target.session}") {
                            HomeEditViewModel(homeRepository, selectedHome, target.homeId)
                        }
                        HomeEditScreen(editViewModel, onFinished = { editTarget = null })
                    } else {
                        val viewModel = viewModel { HomePickerViewModel(homes, selectedHome) }
                        HomePickerScreen(
                            viewModel,
                            onCreateHome = { openEditor(null) },
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
private fun HomeContent(homeId: String, homeName: String, pantrySource: PantrySource? = null) {
    val selectedHome = LocalSelectedHome.current
    var destination by remember { mutableStateOf(HomeDestination.Dashboard) }
    var editingItem by remember { mutableStateOf<PantryItemDetails?>(null) }
    val pantryViewModel = pantrySource?.let { source ->
        viewModel(key = "pantry-$homeId") { PantryViewModel(source) }
    }
    when (destination) {
        HomeDestination.Dashboard -> HomeDashboardScreen(
            homeName = homeName,
            onBack = { selectedHome.clear() },
            x0xConnectivity = rememberX0xConnectivity(),
            onPantryClick = { destination = HomeDestination.Pantry },
        )
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
            if (pantryViewModel != null) {
                PantryScreen(pantryViewModel, onItemSelected = onItemSelected, onBack = onBack, onAddItem = onAddItem)
            } else {
                PantryScreen(items = emptyList(), onItemSelected = onItemSelected, onBack = onBack, onAddItem = onAddItem)
            }
        }
        HomeDestination.PantryItemEdit -> PantryItemEditScreen(
            item = editingItem,
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

private enum class HomeDestination { Dashboard, Pantry, PantryItemEdit }

/**
 * [App] backed by the local database on this platform; what the platform entry points launch. With
 * [openPantry] (platforms that have the Pantry Automerge document layer), each Home's Pantry is loaded
 * from and saved to the database; without it the Pantry is not persisted. [backgroundServices], if given,
 * runs for as long as the app is shown, with the database (created here, so callers can't have it earlier).
 */
@Composable
fun VestaApp(
    openPantry: (suspend (VestaDatabase, homeId: String) -> PantrySource)? = null,
    homeContent: (@Composable (homeId: String, homeName: String) -> Unit)? = null,
    backgroundServices: (suspend CoroutineScope.(VestaDatabase) -> Unit)? = null,
) {
    val database = remember { createVestaDatabase(DatabaseDriverFactory()) }
    LaunchedEffect(database) { backgroundServices?.invoke(this, database) }
    val homeRepository = remember { HomeRepository(database) }
    val agentAccess = remember { AgentAccessRepository(database) }
    App(
        homes = homeRepository.homes,
        homeRepository = homeRepository,
        agentAccess = agentAccess,
        homeContent = homeContent ?: { homeId, homeName ->
            val pantrySource by produceState<PantrySource?>(null, homeId) { value = openPantry?.invoke(database, homeId) }
            HomeContent(homeId, homeName, pantrySource)
        },
    )
}
