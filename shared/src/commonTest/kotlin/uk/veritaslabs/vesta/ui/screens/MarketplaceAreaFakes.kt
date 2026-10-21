package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.HomeLocationRepository
import uk.veritaslabs.vesta.location.LocalityPackSource
import uk.veritaslabs.vesta.location.LocalityPackState
import uk.veritaslabs.vesta.location.LocalityRoot
import uk.veritaslabs.vesta.location.CategoryTable
import uk.veritaslabs.vesta.location.PlaceRange
import uk.veritaslabs.vesta.location.PlaceRecord
import uk.veritaslabs.vesta.location.PlacesDirectory
import uk.veritaslabs.vesta.location.PlacesFetchResult
import uk.veritaslabs.vesta.location.PlacesPackSource
import uk.veritaslabs.vesta.location.PlacesPackState
import uk.veritaslabs.vesta.location.PlacesRoot
import uk.veritaslabs.vesta.location.TinyPack
import uk.veritaslabs.vesta.location.TinyPlaces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

internal class AreaFakeSource(
    var cached: Boolean = false,
    var preflightResult: LocalityPackState? = null,
    var loadResult: LocalityPackState = areaReadyPack(),
) : LocalityPackSource {
    var loadCalls = 0
    override suspend fun isCachedLocally() = cached
    override suspend fun preflight() = preflightResult
    override suspend fun load(): LocalityPackState {
        loadCalls++
        return loadResult
    }
}

internal class AreaFakeRepository(var location: HomeLocation? = null) : HomeLocationRepository {
    val calls = mutableListOf<String>()
    override suspend fun read(homeId: String) = location
    override suspend fun setHome(homeId: String, country: String, localityId: String, release: String) {
        calls += "setHome $country $localityId $release"
        location = HomeLocation(country, localityId, release)
    }

    override suspend fun setArea(homeId: String, localityIds: Set<String>) {
        calls += "setArea ${localityIds.sorted()}"
        location = location?.copy(area = localityIds)
    }

    override suspend fun clear(homeId: String) {
        calls += "clear"
        location = null
    }
}

/** The tiny pack, or with [big] one where Newport has fifteen nearby places, the farthest named "Far Away". */
internal fun areaReadyPack(big: Boolean = false): LocalityPackState.Ready {
    val root = LocalityRoot.parse(TinyPack.rootChunk())
    if (!big) return LocalityPackState.Ready(root, TinyPack.pack())
    val extra = (0 until 9).map { TinyPack.Spec("Extra $it", "locality", 2, 50.60 + it * 0.001, -1.2) } +
        TinyPack.Spec("Far Away", "locality", 2, 50.0, -1.0)
    val specs = TinyPack.specs + extra
    val edges = TinyPack.edges + extra.indices.map { 3 to TinyPack.specs.size + it }
    return LocalityPackState.Ready(root, TinyPack.pack(specs, edges))
}

/**
 * A places source over [TinyPlaces]. The directory (counts and byte sizes) is built from [ranges] and [chunkSizes]; the
 * places returned by [fetchUnits] come from [TinyPlaces.units] through [rowUnits], so the two can be varied separately.
 * Defaults match the locality tiny pack: Newport is row 3, Cowes row 5, Carisbrooke row 7.
 */
internal class AreaFakePlaces(
    var cached: Boolean = true,
    var preflightResult: PlacesPackState? = null,
    var chunkSizes: List<Int> = listOf(100, 250),
    var ranges: List<List<PlaceRange>> = areaPlacesRanges(),
    var rowUnits: Map<Int, List<Int>> = mapOf(3 to listOf(0, 1), 5 to listOf(2, 3), 7 to listOf(4, 5)),
) : PlacesPackSource {
    var loadCalls = 0
    var fetchCalls = 0
    var lastIncludeNearby: Boolean? = null
    var lastRows: List<Int> = emptyList()

    /** Chunks reported as failed; when [failAll] no place comes back either. */
    var failedChunks = 0
    var failAll = false

    /** When set, returned for these rows instead of the tiny-places units (for paging tests). */
    var generated: Map<Int, List<PlaceRecord>>? = null
    var loadResult: PlacesPackState? = null

    /** When set, [fetchUnits] reports one chunk and then waits for this before finishing. */
    var gate: CompletableDeferred<Unit>? = null
    var cancelled = false

    override suspend fun isCachedLocally() = cached
    override suspend fun preflight() = preflightResult

    override suspend fun load(): PlacesPackState {
        loadCalls++
        loadResult?.let { return it }
        return PlacesPackState.Ready(
            PlacesRoot.parse(TinyPlaces.rootChunk()),
            PlacesDirectory.parse(TinyPlaces.directory(chunkSizes = chunkSizes, ranges = ranges), TinyPlaces.identity),
            CategoryTable.parse(TinyPlaces.categoryTable(), TinyPlaces.identity),
        )
    }

    override suspend fun fetchUnits(
        ready: PlacesPackState.Ready,
        rows: Collection<Int>,
        includeNearby: Boolean,
        onProgress: (fetchedChunks: Int, totalChunks: Int, fetchedBytes: Long) -> Unit,
    ): PlacesFetchResult {
        fetchCalls++
        lastIncludeNearby = includeNearby
        lastRows = rows.toList()
        try {
            onProgress(1, 2, 100)
            gate?.await()
            onProgress(2, 2, 350)
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        }
        val places = rows.associateWith { row ->
            if (failAll) {
                emptyList()
            } else if (generated != null) {
                generated?.get(row).orEmpty()
            } else {
                rowUnits[row].orEmpty().map { TinyPlaces.units[it] }.filter { includeNearby || !it.nearby }.flatMap { unit ->
                    unit.places.map { PlaceRecord(it.id, it.name, it.lat, it.lon, it.confidence, it.category, unit.nearby) }
                }
            }
        }
        return PlacesFetchResult(places, failedChunks)
    }
}

/** The directory ranges of the default [AreaFakePlaces]: 14 locality rows, places only on rows 3, 5 and 7. */
internal fun areaPlacesRanges(): List<List<PlaceRange>> {
    val built = TinyPlaces.build()
    return List(14) { row ->
        when (row) {
            3 -> built.ranges[0]
            5 -> built.ranges[1]
            7 -> built.ranges[2]
            else -> emptyList()
        }
    }
}
