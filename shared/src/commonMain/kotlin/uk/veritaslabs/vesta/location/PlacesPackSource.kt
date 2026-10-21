package uk.veritaslabs.vesta.location

/** What loading the places pack's root, directory and category table came to. */
sealed interface PlacesPackState {
    /** Neither a pointer nor a root address is configured. */
    data object Unconfigured : PlacesPackState

    /** This platform has no way to fetch chunks, and nothing is cached. */
    data object NotAvailableOnDevice : PlacesPackState

    data class Unreachable(val reason: String) : PlacesPackState

    data class Ready(val root: PlacesRoot, val directory: PlacesDirectory, val categories: CategoryTable) : PlacesPackState
}

/**
 * What [PlacesPackSource.fetchUnits] managed. [places] has an entry for every requested row (empty when none of its chunks
 * could be read); [failedChunks] counts the chunks that could not be fetched or did not decode, so the caller can say the
 * list is partial.
 */
data class PlacesFetchResult(val places: Map<Int, List<PlaceRecord>>, val failedChunks: Int)

/** Where the places pack (ADR-039) comes from. */
interface PlacesPackSource {
    /** True when [load] can finish without touching the network. */
    suspend fun isCachedLocally(): Boolean

    /** A check that never touches the network: Unconfigured or NotAvailableOnDevice when no load could succeed, null otherwise. */
    suspend fun preflight(): PlacesPackState?

    /** Never throws (apart from cancellation); a failure is a state. */
    suspend fun load(): PlacesPackState

    /**
     * The places of the names-table [rows], per row (inside places first, then nearby ones when [includeNearby]). Fetches only
     * the data chunks the rows need, nearest-first in the order of [rows], a few at a time. [onProgress] is told after each
     * chunk finishes (fetched or not): chunks done so far, chunks needed in all, and bytes downloaded so far (cached chunks
     * add none). A chunk that fails does not discard the others; see [PlacesFetchResult.failedChunks].
     */
    suspend fun fetchUnits(
        ready: PlacesPackState.Ready,
        rows: Collection<Int>,
        includeNearby: Boolean,
        onProgress: (fetchedChunks: Int, totalChunks: Int, fetchedBytes: Long) -> Unit = { _, _, _ -> },
    ): PlacesFetchResult
}

/** For platforms that cannot fetch the places pack yet: the Businesses step says it is not available on this device. */
object UnavailablePlacesPackSource : PlacesPackSource {
    override suspend fun isCachedLocally() = false

    override suspend fun preflight(): PlacesPackState? = PlacesPackState.NotAvailableOnDevice

    override suspend fun load(): PlacesPackState = PlacesPackState.NotAvailableOnDevice

    override suspend fun fetchUnits(
        ready: PlacesPackState.Ready,
        rows: Collection<Int>,
        includeNearby: Boolean,
        onProgress: (fetchedChunks: Int, totalChunks: Int, fetchedBytes: Long) -> Unit,
    ) = PlacesFetchResult(rows.associateWith { emptyList() }, 0)
}
