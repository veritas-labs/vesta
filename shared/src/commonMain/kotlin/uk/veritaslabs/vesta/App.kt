package uk.veritaslabs.vesta

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.veritaslabs.vesta.db.DatabaseDriverFactory
import uk.veritaslabs.vesta.db.createVestaDatabase
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.SelectedHome
import uk.veritaslabs.vesta.ui.screens.HomeListItem
import uk.veritaslabs.vesta.ui.screens.HomePickerScreen
import uk.veritaslabs.vesta.ui.screens.HomePickerViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/** The app-wide currently selected Home; read `LocalSelectedHome.current.homeId` from any composable under [App]. */
val LocalSelectedHome = compositionLocalOf<SelectedHome> { error("No SelectedHome provided; call within App()") }

/**
 * App root. Shows the Home picker until a Home is selected, then [homeContent] for that Home's id.
 * [homes] is the list the picker offers.
 */
@Composable
@Preview
fun App(
    homes: Flow<List<HomeListItem>> = flowOf(emptyList()),
    selectedHome: SelectedHome = remember { SelectedHome() },
    homeContent: @Composable (homeId: String) -> Unit = {},
) {
    val homeId by selectedHome.homeId.collectAsStateWithLifecycle()
    // Drop the selection if its Home goes away (deleted, or left).
    LaunchedEffect(homes, selectedHome) {
        homes.collect { list -> selectedHome.homeId.value?.let { id -> if (list.none { it.id == id }) selectedHome.clear() } }
    }
    CompositionLocalProvider(LocalSelectedHome provides selectedHome) {
        VestaTheme {
            homeId?.let { homeContent(it) } ?: run {
                val viewModel = viewModel { HomePickerViewModel(homes, selectedHome) }
                HomePickerScreen(viewModel)
            }
        }
    }
}


/** [App] backed by the local database on this platform; what the platform entry points launch. */
@Composable
fun VestaApp(homeContent: @Composable (homeId: String) -> Unit = {}) {
    val homeRepository = remember { HomeRepository(createVestaDatabase(DatabaseDriverFactory())) }
    App(homes = homeRepository.homes, homeContent = homeContent)
}
