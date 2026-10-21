package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.HomeLocationRepository
import uk.veritaslabs.vesta.location.LocalityDirectory
import uk.veritaslabs.vesta.location.LocalityPackSource
import uk.veritaslabs.vesta.location.LocalityPackState
import uk.veritaslabs.vesta.location.LocalityRoot
import uk.veritaslabs.vesta.location.NearbyPlaces
import uk.veritaslabs.vesta.location.NearbyRelation
import uk.veritaslabs.vesta.location.foldForSearch

/** Why the area flow cannot run. */
sealed interface AreaUnavailable {
    /** No locality data address is configured on this device. */
    data object Unconfigured : AreaUnavailable

    /** This platform cannot fetch the data and has none cached. */
    data object NotAvailableOnDevice : AreaUnavailable

    /** The data could not be fetched or decoded; [reason] is a short message with no personal data. */
    data class Unreachable(val reason: String) : AreaUnavailable
}

/** Where the Marketplace area flow (ADR-039 decision 3) is. */
sealed interface AreaStep {
    data object Loading : AreaStep
    data class Unavailable(val why: AreaUnavailable) : AreaStep

    /** Asks before the first download; nothing has been requested yet. */
    data object Disclosure : AreaStep

    /** The user chose "Not now" at [Disclosure]: a terminal step, the screen should leave the flow. Nothing was requested. */
    data object Declined : AreaStep
    data object Country : AreaStep
    data object HomeLocality : AreaStep
    data object Nearby : AreaStep

    /** A location is stored; see [MarketplaceAreaUiState.saved]. */
    data object Saved : AreaStep

    /** The stored home locality is not in the pack (a different release); the user must choose again. */
    data object NeedsRechoose : AreaStep
}

data class AreaCountry(val code: String, val name: String)

/** A locality found by search, with its parent chain ("Newport, Isle of Wight, England"). */
data class AreaPlace(val id: String, val name: String, val chainText: String)

/** One row of the "Also search nearby places" list. */
data class AreaNearbyRow(
    val id: String,
    val name: String,
    val relation: NearbyRelation,
    val distanceMetres: Double,
    val ticked: Boolean,
)

/**
 * What the Saved card shows. [homeName] and [chainText] are null while the pack is not loaded (it is not downloaded just
 * to display a card), so the screen then says "your area". [nearbyCount] is the ticked places other than the home.
 */
data class AreaSummary(val homeName: String?, val chainText: String?, val nearbyCount: Int)

data class MarketplaceAreaUiState(
    val step: AreaStep = AreaStep.Loading,
    /** Country step. */
    val countries: List<AreaCountry> = emptyList(),
    val selectedCountry: String? = null,
    /** Home locality step. */
    val query: String = "",
    val results: List<AreaPlace> = emptyList(),
    /** True only for a non-blank [query] that found nothing. */
    val noMatch: Boolean = false,
    /** The chosen home locality; ticked and locked on the Nearby step. */
    val home: AreaPlace? = null,
    /** Nearby step: the visible rows (the first ten, plus more after showMore(), while [nearbyQuery] is blank; every match otherwise). */
    val nearby: List<AreaNearbyRow> = emptyList(),
    val nearbyQuery: String = "",
    val hasMoreNearby: Boolean = false,
    /** Ticked places including the home locality. */
    val tickedCount: Int = 0,
    val saved: AreaSummary? = null,
    /** True while confirm() is writing. */
    val isSaving: Boolean = false,
)

private const val SearchLimit = 20
private const val ShowMoreStep = 10

private val CountryNames = mapOf(
    "GB" to "United Kingdom", "IE" to "Ireland", "US" to "United States", "CA" to "Canada", "AU" to "Australia",
    "NZ" to "New Zealand", "DE" to "Germany", "FR" to "France", "ES" to "Spain", "IT" to "Italy", "NL" to "Netherlands",
)

/** The display name of a country code, or the code itself when it is not known. */
fun countryName(code: String): String = CountryNames[code.uppercase()] ?: code

/**
 * Backs the Marketplace area flow (ADR-039): disclosure, country, home locality, nearby places, saved. Its only I/O is
 * [source] and [repository]. Nothing is requested from the network before [agreeToDownload]; a stored location is shown
 * from what is stored, and the pack is read only when it is already cached or the user agreed.
 */
class MarketplaceAreaViewModel(
    private val homeId: String,
    private val source: LocalityPackSource,
    private val repository: HomeLocationRepository,
    private val regionCode: () -> String?,
) : ViewModel() {

    private val state = MutableStateFlow(MarketplaceAreaUiState())
    val uiState: StateFlow<MarketplaceAreaUiState> = state.asStateFlow()

    private var directory: LocalityDirectory? = null
    private var root: LocalityRoot? = null
    private var stored: HomeLocation? = null

    /** Ticked neighbours, never the home. */
    private var ticked: Set<String> = emptySet()
    private var shown = NearbyPlaces.DEFAULT_SHOWN

    /** The user tapped Edit with the pack not cached: after the disclosure, resume editing instead of the country step. */
    private var resumeEdit = false

    /** Reads the stored location and decides the first step. */
    fun start() {
        viewModelScope.launch { begin() }
    }

    private suspend fun begin() {
        state.value = MarketplaceAreaUiState()
        val location = repository.read(homeId)
        stored = location
        if (location == null) return beginFresh()
        // Only an already-cached pack is read here, so a saved card never causes a request.
        if (source.isCachedLocally() && load() && directory?.row(location.homeLocality) == null) {
            state.update { it.copy(step = AreaStep.NeedsRechoose) }
            return
        }
        showSaved(location)
    }

    /** The entry rules with nothing stored: unavailable, straight to the country, or the disclosure. */
    private suspend fun beginFresh() {
        source.preflight()?.let { return unavailable(it) }
        if (source.isCachedLocally()) {
            if (load()) enterCountry()
        } else {
            state.update { it.copy(step = AreaStep.Disclosure) }
        }
    }

    /** Downloads the pack after the user agreed at the disclosure. */
    fun agreeToDownload() {
        viewModelScope.launch {
            if (!load()) return@launch
            if (resumeEdit) {
                resumeEdit = false
                enterEdit()
            } else {
                enterCountry()
            }
        }
    }

    /** "Not now": nothing is loaded or requested, and the step becomes [AreaStep.Declined]. */
    fun declineDownload() {
        resumeEdit = false
        state.update { it.copy(step = AreaStep.Declined) }
    }

    /** Loads the pack (step Loading first); false after moving to Unavailable. */
    private suspend fun load(): Boolean {
        if (directory != null) return true
        state.update { it.copy(step = AreaStep.Loading) }
        return when (val result = source.load()) {
            is LocalityPackState.Ready -> {
                directory = LocalityDirectory(result.pack)
                root = result.root
                true
            }
            else -> {
                unavailable(result)
                false
            }
        }
    }

    private fun unavailable(result: LocalityPackState) {
        val why = when (result) {
            LocalityPackState.Unconfigured -> AreaUnavailable.Unconfigured
            LocalityPackState.NotAvailableOnDevice -> AreaUnavailable.NotAvailableOnDevice
            is LocalityPackState.Unreachable -> AreaUnavailable.Unreachable(result.reason)
            is LocalityPackState.Ready -> return
        }
        state.update { it.copy(step = AreaStep.Unavailable(why)) }
    }

    private fun showSaved(location: HomeLocation) {
        val dir = directory
        val row = dir?.row(location.homeLocality)
        val summary = AreaSummary(
            homeName = row?.name,
            chainText = row?.let { dir.chainText(it.id) },
            nearbyCount = (location.area - location.homeLocality).size,
        )
        state.update { it.copy(step = AreaStep.Saved, saved = summary) }
    }

    private fun enterCountry() {
        val offered = offeredCountries()
        val preselected = regionCode()?.uppercase()?.takeIf { code -> offered.any { it.code == code } }
        state.update { it.copy(step = AreaStep.Country, countries = offered, selectedCountry = preselected) }
    }

    private fun offeredCountries() = root?.country?.let { listOf(AreaCountry(it, countryName(it))) }.orEmpty()

    /** Selects [code] if the root offers it. */
    fun selectCountry(code: String) {
        state.update { current ->
            if (current.countries.any { it.code == code }) current.copy(selectedCountry = code) else current
        }
    }

    /** Continue on the country step; ignored until a country is selected. */
    fun continueFromCountry() {
        if (state.value.selectedCountry == null) return
        state.update { it.copy(step = AreaStep.HomeLocality) }
    }

    fun setQuery(query: String) {
        val dir = directory ?: return
        val results = if (query.isBlank()) emptyList() else dir.search(query, SearchLimit).map { place(dir, it.id, it.name) }
        state.update { it.copy(query = query, results = results, noMatch = query.isNotBlank() && results.isEmpty()) }
    }

    /** Chooses [id] as the home locality and moves to Nearby. Choosing the current home again keeps its ticks. */
    fun chooseHome(id: String) {
        val dir = directory ?: return
        val row = dir.row(id) ?: return
        if (state.value.home?.id != row.id) ticked = emptySet()
        shown = NearbyPlaces.DEFAULT_SHOWN
        state.update { it.copy(home = place(dir, row.id, row.name), nearbyQuery = "", step = AreaStep.Nearby) }
        renderNearby()
    }

    fun setNearbyQuery(query: String) {
        state.update { it.copy(nearbyQuery = query) }
        renderNearby()
    }

    fun showMore() {
        shown += ShowMoreStep
        renderNearby()
    }

    /** Ticks or unticks [id]; the home locality and ids not offered are ignored. */
    fun toggle(id: String) {
        val home = state.value.home?.id ?: return
        val dir = directory ?: return
        if (id == home) return
        if (NearbyPlaces.rows(home, dir, Int.MAX_VALUE).rows.none { it.id == id }) return
        ticked = if (id in ticked) ticked - id else ticked + id
        renderNearby()
    }

    private fun renderNearby() {
        val dir = directory ?: return
        val home = state.value.home?.id ?: return
        val query = foldForSearch(state.value.nearbyQuery).trim()
        val all = NearbyPlaces.rows(home, dir, Int.MAX_VALUE).rows
        val visible = if (query.isEmpty()) all.take(shown) else all.filter { query in foldForSearch(it.name) }
        state.update {
            it.copy(
                nearby = visible.map { row -> AreaNearbyRow(row.id, row.name, row.relation, row.distanceMetres, row.id in ticked) },
                hasMoreNearby = query.isEmpty() && all.size > shown,
                tickedCount = ticked.size + 1,
            )
        }
    }

    /** Saves the home and the ticked places, then shows Saved. */
    fun confirm() {
        val home = state.value.home?.id ?: return
        val release = root?.release ?: return
        val country = state.value.selectedCountry ?: root?.country ?: return
        val area = ticked
        viewModelScope.launch {
            state.update { it.copy(isSaving = true) }
            repository.setHome(homeId, country, home, release)
            repository.setArea(homeId, area)
            val location = HomeLocation(country, home, release, area = area)
            stored = location
            showSaved(location)
            state.update { it.copy(isSaving = false) }
        }
    }

    /** One step back, keeping what was typed and ticked. False at a step with nothing before it: the screen leaves. */
    fun back(): Boolean = when (state.value.step) {
        AreaStep.Nearby -> {
            state.update { it.copy(step = AreaStep.HomeLocality) }
            true
        }
        AreaStep.HomeLocality -> {
            stored?.let { showSaved(it) } ?: state.update { it.copy(step = AreaStep.Country) }
            true
        }
        else -> false
    }

    /** Clears the stored location and starts again by the entry rules, with the typed and ticked state reset. */
    fun forget() {
        viewModelScope.launch {
            repository.clear(homeId)
            stored = null
            ticked = emptySet()
            shown = NearbyPlaces.DEFAULT_SHOWN
            resumeEdit = false
            state.value = MarketplaceAreaUiState()
            beginFresh()
        }
    }

    /**
     * Edit on the Saved card: opens the home locality step with the current choice kept. When the pack is not cached the
     * disclosure comes first, because loading would touch the network; [agreeToDownload] then resumes the edit.
     */
    fun edit() {
        viewModelScope.launch {
            if (directory == null) {
                source.preflight()?.let { return@launch unavailable(it) }
                if (!source.isCachedLocally()) {
                    resumeEdit = true
                    state.update { it.copy(step = AreaStep.Disclosure) }
                    return@launch
                }
                if (!load()) return@launch
            }
            enterEdit()
        }
    }

    private fun enterEdit() {
        val dir = directory ?: return
        val location = stored ?: return enterCountry()
        val row = dir.row(location.homeLocality)
        if (row == null) {
            state.update { it.copy(step = AreaStep.NeedsRechoose) }
            return
        }
        ticked = location.area - location.homeLocality
        shown = NearbyPlaces.DEFAULT_SHOWN
        state.update {
            it.copy(
                step = AreaStep.HomeLocality,
                countries = offeredCountries(),
                selectedCountry = location.country,
                query = "",
                results = emptyList(),
                noMatch = false,
                home = place(dir, row.id, row.name),
                nearbyQuery = "",
            )
        }
    }

    /** From NeedsRechoose: forgets the stale choice locally (the stored value is replaced on confirm) and starts at the country step. */
    fun rechoose() {
        stored = null
        ticked = emptySet()
        state.update { MarketplaceAreaUiState(step = it.step) }
        enterCountry()
    }

    private fun place(dir: LocalityDirectory, id: String, name: String) = AreaPlace(id, name, dir.chainText(id))
}
