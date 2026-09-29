package uk.veritaslabs.vesta

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.db.DatabaseDriverFactory
import uk.veritaslabs.vesta.db.createVestaDatabase
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.SelectedHome
import uk.veritaslabs.vesta.ui.screens.HomeEditScreen
import uk.veritaslabs.vesta.ui.screens.HomeEditViewModel
import uk.veritaslabs.vesta.ui.screens.HomeListItem
import uk.veritaslabs.vesta.ui.screens.HomePickerScreen
import uk.veritaslabs.vesta.ui.screens.HomePickerViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/** The app-wide currently selected Home; read `LocalSelectedHome.current.homeId` from any composable under [App]. */
val LocalSelectedHome = compositionLocalOf<SelectedHome> { error("No SelectedHome provided; call within App()") }

/**
 * Which Home the edit screen is open on: [homeId] is null when creating a new Home. [session] is unique
 * per opening, so each visit gets a fresh view model rather than a finished one left in the store.
 */
private data class HomeEditTarget(val homeId: String?, val session: Int)

/**
 * App root. Shows the Home picker until a Home is selected, then [homeContent] for that Home's id.
 * [homes] is the list the picker offers. With a [homeRepository], the picker's create/edit/delete
 * actions are live; without one (previews) they do nothing.
 */
@Composable
@Preview
fun App(
    homes: Flow<List<HomeListItem>> = flowOf(emptyList()),
    selectedHome: SelectedHome = remember { SelectedHome() },
    homeRepository: HomeRepository? = null,
    homeContent: @Composable (homeId: String) -> Unit = {},
) {
    val homeId by selectedHome.homeId.collectAsStateWithLifecycle()
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
            homeId?.let { homeContent(it) } ?: run {
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
        }
    }
}


/** [App] backed by the local database on this platform; what the platform entry points launch. */
@Composable
fun VestaApp(homeContent: @Composable (homeId: String) -> Unit = {}) {
    val homeRepository = remember { HomeRepository(createVestaDatabase(DatabaseDriverFactory())) }
    App(homes = homeRepository.homes, homeRepository = homeRepository, homeContent = homeContent)
}
