package uk.veritaslabs.vesta.ui.flows

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.screens.AreaCountryScreen
import uk.veritaslabs.vesta.ui.screens.AreaDisclosureScreen
import uk.veritaslabs.vesta.ui.screens.AreaHomeLocalityScreen
import uk.veritaslabs.vesta.ui.screens.AreaNearbyScreen
import uk.veritaslabs.vesta.ui.screens.AreaNeedsRechooseScreen
import uk.veritaslabs.vesta.ui.screens.AreaSavedScreen
import uk.veritaslabs.vesta.ui.screens.AreaScaffold
import uk.veritaslabs.vesta.ui.screens.AreaStep
import uk.veritaslabs.vesta.ui.screens.AreaUnavailableScreen
import uk.veritaslabs.vesta.ui.screens.MarketplaceAreaTestTags
import uk.veritaslabs.vesta.ui.screens.MarketplaceAreaViewModel
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.area_loading
import vesta.shared.generated.resources.area_title

/**
 * The Marketplace area picker (ADR-039 decision 3): maps each [AreaStep] of [viewModel] to its screen and starts the view
 * model once. Every way back, the top-bar arrow and the system back gesture, goes through [MarketplaceAreaViewModel.back] and
 * calls [onBack] when there is no earlier step. "Not now" at the disclosure also calls [onBack]. The caller owns the view
 * model (one for each Home) and decides where [onBack] goes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MarketplaceAreaFlow(viewModel: MarketplaceAreaViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val leave by rememberUpdatedState(onBack)
    val goBack = { if (!viewModel.back()) leave() }
    LaunchedEffect(viewModel) { viewModel.start() }
    BackHandler(onBack = goBack)
    when (val step = state.step) {
        AreaStep.Loading -> AreaScaffold(stringResource(Res.string.area_title), goBack, modifier) {
            Box(Modifier.fillMaxSize().testTag(MarketplaceAreaTestTags.Loading), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AreaStep.Unavailable -> AreaUnavailableScreen(step.why, modifier, onTryAgain = viewModel::start, onBack = goBack)
        AreaStep.Disclosure -> AreaDisclosureScreen(
            modifier,
            onContinue = viewModel::agreeToDownload,
            onNotNow = viewModel::declineDownload,
            onBack = goBack,
        )
        AreaStep.Declined -> LaunchedEffect(Unit) { leave() }
        AreaStep.Country -> AreaCountryScreen(
            state.countries,
            state.selectedCountry,
            modifier,
            onSelect = viewModel::selectCountry,
            onContinue = viewModel::continueFromCountry,
            onBack = goBack,
        )
        AreaStep.HomeLocality -> AreaHomeLocalityScreen(
            state.query,
            state.results,
            state.noMatch,
            state.home,
            modifier,
            onQueryChange = viewModel::setQuery,
            onChoose = viewModel::chooseHome,
            onBack = goBack,
        )
        AreaStep.Nearby -> state.home?.let { home ->
            AreaNearbyScreen(
                home,
                state.nearby,
                state.nearbyQuery,
                state.hasMoreNearby,
                state.tickedCount,
                state.isSaving,
                modifier,
                onToggle = viewModel::toggle,
                onQueryChange = viewModel::setNearbyQuery,
                onShowMore = viewModel::showMore,
                onSave = viewModel::confirm,
                onBack = goBack,
            )
        }
        AreaStep.Saved -> state.saved?.let { summary ->
            AreaSavedScreen(summary, modifier, onChange = viewModel::edit, onForget = viewModel::forget, onBack = goBack)
        }
        AreaStep.NeedsRechoose -> AreaNeedsRechooseScreen(modifier, onChoose = viewModel::rechoose, onBack = goBack)
    }
}
