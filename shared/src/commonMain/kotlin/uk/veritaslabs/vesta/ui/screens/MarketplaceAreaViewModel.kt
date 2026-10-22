package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import uk.veritaslabs.vesta.location.AreaPlan
import uk.veritaslabs.vesta.location.BusinessGroupCount
import uk.veritaslabs.vesta.location.BusinessRow
import uk.veritaslabs.vesta.location.BusinessSection
import uk.veritaslabs.vesta.location.CategoryCount
import uk.veritaslabs.vesta.location.foldForSearch
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.HomeLocationRepository
import uk.veritaslabs.vesta.location.LocalityDirectory
import uk.veritaslabs.vesta.location.LocalityPackSource
import uk.veritaslabs.vesta.location.LocalityPackState
import uk.veritaslabs.vesta.location.LocalityRoot
import uk.veritaslabs.vesta.location.NearbyBusinesses
import uk.veritaslabs.vesta.location.NearbyPlaces
import uk.veritaslabs.vesta.location.PlaceRecord
import uk.veritaslabs.vesta.location.PlacesPackSource
import uk.veritaslabs.vesta.location.PlacesPackState
import uk.veritaslabs.vesta.location.UnavailablePlacesPackSource
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

    /** What the area holds and what fetching it costs, with the choice to include places just outside; see [MarketplaceAreaUiState.businesses]. */
    data object BusinessesSummary : AreaStep

    /** Fetching the business data, with progress and Cancel. */
    data object BusinessesLoading : AreaStep

    /**
     * Browsing the fetched businesses by trade: level one (groups) while [AreaBusinessesState.group] is null, level two
     * (that group's categories) otherwise. [AreaBusinessesState.partial] says some data is missing.
     */
    data object BusinessesCategories : AreaStep

    /** The businesses of one category, group or all, A to Z under locality headers; see [AreaBusinessesState.sections]. */
    data object BusinessesList : AreaStep

    /** Nothing could be loaded; offers Try again. */
    data object BusinessesUnreachable : AreaStep

    /** My Shops: the favourites, A to Z, read from the merchants document so it works offline; back returns to the categories. */
    data object MyShops : AreaStep
}

/** Whether the Saved card can offer "See businesses": from [PlacesPackSource.preflight], so it never touches the network. */
enum class AreaBusinessesAvailability { Available, Unconfigured, NotAvailableOnDevice, Unreachable }

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
data class AreaSummary(
    val homeName: String?,
    val chainText: String?,
    val nearbyCount: Int,
    val businesses: AreaBusinessesAvailability = AreaBusinessesAvailability.NotAvailableOnDevice,
)

/** The Businesses step (ADR-039 decisions 6 to 9). Counts and sizes come from the places directory alone. */
data class AreaBusinessesState(
    val inside: Int = 0,
    val nearby: Int = 0,
    val bytesInsideOnly: Long = 0,
    val bytesWithNearby: Long = 0,
    /** Whether places just outside the chosen localities are fetched too. */
    val includeNearby: Boolean = false,
    /** True while "This will download about X MB. Download?" is showing. */
    val confirming: Boolean = false,
    val chunksDone: Int = 0,
    val chunksTotal: Int = 0,
    val bytesFetched: Long = 0,
    /** The visible page of the list, flat. */
    val items: List<BusinessRow> = emptyList(),
    /** The same page under locality headers, home locality first; a split locality's header repeats on the next page. */
    val sections: List<BusinessSection> = emptyList(),
    /** Businesses matching the list's query, group and category, before paging. */
    val matching: Int = 0,
    val hasMore: Boolean = false,
    /** Level one: groups, most places first, the unknown group last. */
    val groups: List<BusinessGroupCount> = emptyList(),
    /** Level two: the open group's categories, most places first, narrowed by [categoryQuery]. */
    val categories: List<CategoryCount> = emptyList(),
    /** Everything fetched, before any query or group. */
    val loaded: Int = 0,
    /** The list's own search. */
    val query: String = "",
    /** The search field on level one. */
    val browseQuery: String = "",
    /** The category search on level two. */
    val categoryQuery: String = "",
    /** The open group's key (level two, or the list narrowed to a group), or null for none / all. */
    val group: String? = null,
    /** Display name of [group], for headers. */
    val groupName: String = "",
    /** Places in [group]. */
    val groupCount: Int = 0,
    /** The category the list is narrowed to (a category-table index), or null. */
    val category: Int? = null,
    val categoryName: String = "",
    /** Some chunks could not be fetched: the list is what loaded. */
    val partial: Boolean = false,
) {
    val chosenBytes: Long get() = if (includeNearby) bytesWithNearby else bytesInsideOnly
    val chosenCount: Int get() = if (includeNearby) inside + nearby else inside
}

/** Largest download that starts without asking. */
const val BusinessesAutoDownloadBytes = 1L * 1024 * 1024

/** "0.25" under a megabyte, one decimal place from there up, never below "0.01". */
fun megabytesText(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1.0) {
        val hundredths = maxOf(1, kotlin.math.round(mb * 100).toInt())
        "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    } else {
        val tenths = kotlin.math.round(mb * 10).toInt()
        "${tenths / 10}.${tenths % 10}"
    }
}

/** 74050 -> "74,050". */
fun groupedNumber(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

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
    val businesses: AreaBusinessesState = AreaBusinessesState(),
    /** True while confirm() is writing. */
    val isSaving: Boolean = false,
    /** False when the Home has no merchants document yet: hearts and the My Shops row are hidden. */
    val favouritesAvailable: Boolean = false,
    /** Place ids of the favourites (the filled hearts). */
    val favouriteIds: Set<String> = emptySet(),
    /** My Shops, A to Z, hand-made ones included. */
    val myShops: List<Merchant> = emptyList(),
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
    private val placesSource: PlacesPackSource = UnavailablePlacesPackSource,
    private val favourites: FavouriteBusinesses? = null,
    private val regionCode: () -> String?,
) : ViewModel() {

    private val state = MutableStateFlow(MarketplaceAreaUiState(favouritesAvailable = favourites != null))
    val uiState: StateFlow<MarketplaceAreaUiState> = state.asStateFlow()

    init {
        favourites?.let { f ->
            viewModelScope.launch { f.favouriteIds.collect { ids -> state.update { it.copy(favouriteIds = ids) } } }
            viewModelScope.launch { f.myShops.collect { shops -> state.update { it.copy(myShops = shops) } } }
        }
    }

    private var directory: LocalityDirectory? = null
    private var root: LocalityRoot? = null
    private var stored: HomeLocation? = null

    /** Ticked neighbours, never the home. */
    private var ticked: Set<String> = emptySet()
    private var shown = NearbyPlaces.DEFAULT_SHOWN

    /** The user tapped Edit with the pack not cached: after the disclosure, resume editing instead of the country step. */
    private var resumeEdit = false

    /** The user tapped See businesses before the data was cached: after the disclosure, open the Businesses step. */
    private var resumeBusinesses = false
    private var businessesAvailability = AreaBusinessesAvailability.NotAvailableOnDevice

    // Businesses step working state; dropped by forget() and when leaving the step.
    private var placesReady: PlacesPackState.Ready? = null
    private var plan: AreaPlan? = null
    private var fetched: Map<Int, List<PlaceRecord>> = emptyMap()
    private var fetchJob: Job? = null
    private var businessesShown = NearbyBusinesses.PAGE_SIZE

    /** The group whose categories screen the list was opened from (back returns there), or null when it came from level one. */
    private var listParentGroup: String? = null

    /** Every category of the open group, before [AreaBusinessesState.categoryQuery]. */
    private var groupCategories: List<CategoryCount> = emptyList()

    /** Reads the stored location and decides the first step. */
    fun start() {
        viewModelScope.launch { begin() }
    }

    private suspend fun begin() {
        state.update { it.reset() }
        val location = repository.read(homeId)
        stored = location
        businessesAvailability = availability()
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
            if (resumeBusinesses) {
                resumeBusinesses = false
                enterBusinesses()
            } else if (resumeEdit) {
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
        resumeBusinesses = false
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
            businesses = businessesAvailability,
        )
        clearBusinesses()
        state.update { it.copy(step = AreaStep.Saved, saved = summary, businesses = AreaBusinessesState()) }
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
            businessesAvailability = availability()
            val location = HomeLocation(country, home, release, area = area)
            stored = location
            showSaved(location)
            state.update { it.copy(isSaving = false) }
        }
    }

    /** One step back, keeping what was typed and ticked. False at a step with nothing before it: the screen leaves. */
    fun back(): Boolean = when (state.value.step) {
        AreaStep.MyShops -> {
            state.update { it.copy(step = AreaStep.BusinessesCategories) }
            true
        }
        AreaStep.BusinessesLoading -> {
            cancelBusinessesFetch()
            true
        }
        AreaStep.BusinessesList -> {
            backBusinesses()
            true
        }
        AreaStep.BusinessesCategories -> {
            if (state.value.businesses.group != null) backBusinesses() else stored?.let { showSaved(it) }
            true
        }
        AreaStep.BusinessesSummary, AreaStep.BusinessesUnreachable -> {
            stored?.let { showSaved(it) }
            true
        }
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
            resumeBusinesses = false
            clearBusinesses()
            state.update { it.reset() }
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

    private suspend fun availability(): AreaBusinessesAvailability = when (placesSource.preflight()) {
        null, is PlacesPackState.Ready -> AreaBusinessesAvailability.Available
        PlacesPackState.Unconfigured -> AreaBusinessesAvailability.Unconfigured
        PlacesPackState.NotAvailableOnDevice -> AreaBusinessesAvailability.NotAvailableOnDevice
        is PlacesPackState.Unreachable -> AreaBusinessesAvailability.Unreachable
    }

    private fun clearBusinesses() {
        fetchJob?.cancel()
        fetchJob = null
        placesReady = null
        plan = null
        fetched = emptyMap()
        businessesShown = NearbyBusinesses.PAGE_SIZE
        listParentGroup = null
        groupCategories = emptyList()
    }

    /**
     * "See businesses" on the Saved card. Loading the locality data and the places directory touches the network, so when
     * either is not cached the disclosure comes first; [agreeToDownload] then resumes here. Nothing is requested before that.
     */
    fun seeBusinesses() {
        viewModelScope.launch {
            if (placesSource.preflight() != null) return@launch
            if (!source.isCachedLocally() || !placesSource.isCachedLocally()) {
                resumeBusinesses = true
                state.update { it.copy(step = AreaStep.Disclosure) }
                return@launch
            }
            if (!load()) return@launch
            enterBusinesses()
        }
    }

    /** Reads the places directory, works out the counts and either fetches at once (small) or shows the summary. */
    private suspend fun enterBusinesses() {
        val dir = directory ?: return
        val location = stored ?: return
        val area = AreaPlan.of(location, dir) ?: run {
            state.update { it.copy(step = AreaStep.NeedsRechoose) }
            return
        }
        state.update { it.copy(step = AreaStep.Loading) }
        val ready = when (val result = placesSource.load()) {
            is PlacesPackState.Ready -> result
            else -> {
                state.update { it.copy(step = AreaStep.BusinessesUnreachable) }
                return
            }
        }
        plan = area
        placesReady = ready
        fetched = emptyMap()
        businessesShown = NearbyBusinesses.PAGE_SIZE
        val counts = NearbyBusinesses.summarise(area, ready.directory, dir)
        val businesses = AreaBusinessesState(
            inside = counts.inside,
            nearby = counts.nearby,
            bytesInsideOnly = counts.bytesInsideOnly,
            bytesWithNearby = counts.bytesWithNearby,
            includeNearby = counts.bytesWithNearby <= BusinessesAutoDownloadBytes,
        )
        state.update { it.copy(step = AreaStep.BusinessesSummary, businesses = businesses) }
        // Small enough with nearby places included: fetch at once. Otherwise the user chooses first.
        if (businesses.chosenCount > 0 && counts.bytesWithNearby <= BusinessesAutoDownloadBytes) startBusinessesFetch()
    }

    /** The toggle "Include businesses just outside these places". */
    fun setIncludeNearby(include: Boolean) {
        state.update { it.copy(businesses = it.businesses.copy(includeNearby = include)) }
    }

    /** Download on the summary: asks first when the chosen download is over 1 MiB. */
    fun downloadBusinesses() {
        val chosen = state.value.businesses
        if (chosen.chosenCount == 0) return
        if (chosen.chosenBytes > BusinessesAutoDownloadBytes) {
            state.update { it.copy(businesses = chosen.copy(confirming = true)) }
        } else {
            startBusinessesFetch()
        }
    }

    /** "Not now" at the size confirmation: nothing is fetched. */
    fun declineBusinessesDownload() {
        state.update { it.copy(businesses = it.businesses.copy(confirming = false)) }
    }

    fun confirmBusinessesDownload() {
        if (!state.value.businesses.confirming) return
        startBusinessesFetch()
    }

    /** Try again after a failure or on the partial-data banner. */
    fun retryBusinesses() {
        if (placesReady == null || plan == null) {
            viewModelScope.launch { enterBusinesses() }
        } else {
            startBusinessesFetch()
        }
    }

    private fun startBusinessesFetch() {
        val ready = placesReady ?: return
        val area = plan ?: return
        val include = state.value.businesses.includeNearby
        val rows = area.rows.filter { it in 0 until ready.directory.size }
        fetchJob?.cancel()
        state.update {
            it.copy(
                step = AreaStep.BusinessesLoading,
                businesses = it.businesses.copy(confirming = false, chunksDone = 0, chunksTotal = 0, bytesFetched = 0),
            )
        }
        fetchJob = viewModelScope.launch {
            val result = placesSource.fetchUnits(ready, rows, include) { done, total, bytes ->
                state.update { it.copy(businesses = it.businesses.copy(chunksDone = done, chunksTotal = total, bytesFetched = bytes)) }
            }
            fetched = result.places
            val any = result.places.values.any { it.isNotEmpty() }
            if (!any && result.failedChunks > 0) {
                state.update { it.copy(step = AreaStep.BusinessesUnreachable) }
            } else {
                state.update {
                    it.copy(
                        step = AreaStep.BusinessesCategories,
                        businesses = it.businesses.copy(
                            partial = result.failedChunks > 0, query = "", browseQuery = "", categoryQuery = "",
                            group = null, groupName = "", groupCount = 0, category = null, categoryName = "",
                        ),
                    )
                }
                listParentGroup = null
                groupCategories = emptyList()
                businessesShown = NearbyBusinesses.PAGE_SIZE
                renderBusinesses()
            }
        }
    }

    /** Cancel while fetching: what was cached stays cached; back to the summary. */
    fun cancelBusinessesFetch() {
        fetchJob?.cancel()
        fetchJob = null
        state.update { it.copy(step = AreaStep.BusinessesSummary) }
    }

    /** The list's own search (the Businesses list screen). */
    fun setBusinessQuery(query: String) {
        businessesShown = NearbyBusinesses.PAGE_SIZE
        state.update { it.copy(businesses = it.businesses.copy(query = query)) }
        renderBusinesses()
    }

    /** The search field on level one; nothing opens until [searchAll]. */
    fun setBrowseQuery(query: String) {
        state.update { it.copy(businesses = it.businesses.copy(browseQuery = query)) }
    }

    /** The category search on level two: narrows the category names. */
    fun setCategoryQuery(query: String) {
        state.update { it.copy(businesses = it.businesses.copy(categoryQuery = query)) }
        renderBusinesses()
    }

    /** Level one, a group row: its categories, or for the unknown group (no categories) its list directly. */
    fun openGroup(group: String) {
        val current = state.value.businesses
        val row = current.groups.firstOrNull { it.key == group } ?: return
        val ready = placesReady ?: return
        val area = plan ?: return
        businessesShown = NearbyBusinesses.PAGE_SIZE
        listParentGroup = null
        val opened = current.copy(
            group = group, groupName = row.name, groupCount = row.count,
            category = null, categoryName = "", categoryQuery = "", query = "",
        )
        if (group == NearbyBusinesses.UNKNOWN_GROUP) {
            groupCategories = emptyList()
            state.update { it.copy(step = AreaStep.BusinessesList, businesses = opened) }
        } else {
            groupCategories = NearbyBusinesses.categoriesWithCounts(fetched, ready.categories, area, group)
            state.update { it.copy(step = AreaStep.BusinessesCategories, businesses = opened) }
        }
        renderBusinesses()
    }

    /** Level two, "All <group>": the list for the open group. */
    fun openGroupList() {
        val current = state.value.businesses
        val group = current.group ?: return
        businessesShown = NearbyBusinesses.PAGE_SIZE
        listParentGroup = group
        state.update { it.copy(step = AreaStep.BusinessesList, businesses = current.copy(category = null, categoryName = "", query = "")) }
        renderBusinesses()
    }

    /** Level two, a category row: the list narrowed to that category. */
    fun openCategory(index: Int) {
        val current = state.value.businesses
        val row = groupCategories.firstOrNull { it.index == index } ?: return
        businessesShown = NearbyBusinesses.PAGE_SIZE
        listParentGroup = current.group
        state.update { it.copy(step = AreaStep.BusinessesList, businesses = current.copy(category = index, categoryName = row.name, query = "")) }
        renderBusinesses()
    }

    /** Level one, "All businesses": the whole list. */
    fun openAll() = openAllMatching("")

    /** The top-level search submitted: the list across all businesses, matching [query] on the name. */
    fun searchAll(query: String) = openAllMatching(query.trim())

    private fun openAllMatching(query: String) {
        businessesShown = NearbyBusinesses.PAGE_SIZE
        listParentGroup = null
        groupCategories = emptyList()
        state.update {
            it.copy(
                step = AreaStep.BusinessesList,
                businesses = it.businesses.copy(
                    group = null, groupName = "", groupCount = 0, category = null, categoryName = "", query = query, browseQuery = "", categoryQuery = "",
                ),
            )
        }
        renderBusinesses()
    }

    /**
     * One level up inside the browse: a list returns to where it came from (its group's categories, or level one),
     * categories return to level one. Leaving level one for the Saved card is [back]'s job.
     */
    fun backBusinesses() {
        val current = state.value.businesses
        businessesShown = NearbyBusinesses.PAGE_SIZE
        val parent = if (state.value.step == AreaStep.BusinessesList) listParentGroup else null
        listParentGroup = null
        if (parent != null) {
            state.update { it.copy(step = AreaStep.BusinessesCategories, businesses = current.copy(group = parent, category = null, categoryName = "", query = "")) }
        } else {
            groupCategories = emptyList()
            state.update {
                it.copy(
                    step = AreaStep.BusinessesCategories,
                    businesses = current.copy(
                        group = null, groupName = "", groupCount = 0, category = null, categoryName = "",
                        query = "", browseQuery = "", categoryQuery = "",
                    ),
                )
            }
        }
        renderBusinesses()
    }

    fun showMoreBusinesses() {
        businessesShown += NearbyBusinesses.PAGE_SIZE
        renderBusinesses()
    }

    private fun renderBusinesses() {
        val ready = placesReady ?: return
        val area = plan ?: return
        val current = state.value.businesses
        val page = NearbyBusinesses.list(
            fetched, area, current.query, current.group, ready.categories, limit = businessesShown,
            categoryFilter = current.category, localityName = { row -> directory?.nameAt(row).orEmpty() },
        )
        val groups = NearbyBusinesses.groupsWithCounts(fetched, ready.categories, area)
        val folded = foldForSearch(current.categoryQuery).trim()
        val categories = if (folded.isEmpty()) groupCategories else groupCategories.filter { foldForSearch(it.name).contains(folded) }
        state.update {
            it.copy(
                businesses = it.businesses.copy(
                    items = page.items,
                    sections = page.sections,
                    matching = page.total,
                    hasMore = page.hasMore,
                    groups = groups,
                    categories = categories,
                    loaded = groups.sumOf { g -> g.count },
                ),
            )
        }
    }

    /** From NeedsRechoose: forgets the stale choice locally (the stored value is replaced on confirm) and starts at the country step. */
    fun rechoose() {
        stored = null
        ticked = emptySet()
        state.update { it.reset().copy(step = it.step) }
        enterCountry()
    }

    /** A blank state that keeps My Shops, which follow the merchants document and not the flow. */
    private fun MarketplaceAreaUiState.reset() =
        MarketplaceAreaUiState(favouritesAvailable = favouritesAvailable, favouriteIds = favouriteIds, myShops = myShops)

    /** The heart on [business]: adds it to My Shops, or removes it. */
    fun toggleFavourite(business: BusinessRow) {
        val f = favourites ?: return
        val shop = state.value.myShops.firstOrNull { it.gersId == business.id }
        viewModelScope.launch { if (shop == null) f.favourite(business, business.localityName) else f.removeByKey(shop.key) }
    }

    /** The heart on a My Shops row. */
    fun removeShop(key: String) {
        favourites?.let { f -> viewModelScope.launch { f.removeByKey(key) } }
    }

    /** Opens My Shops from the categories screen. */
    fun openMyShops() {
        if (state.value.favouritesAvailable) state.update { it.copy(step = AreaStep.MyShops) }
    }

    private fun place(dir: LocalityDirectory, id: String, name: String) = AreaPlace(id, name, dir.chainText(id))
}
