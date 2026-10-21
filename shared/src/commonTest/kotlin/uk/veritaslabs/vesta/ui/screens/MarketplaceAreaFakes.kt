package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.HomeLocationRepository
import uk.veritaslabs.vesta.location.LocalityPackSource
import uk.veritaslabs.vesta.location.LocalityPackState
import uk.veritaslabs.vesta.location.LocalityRoot
import uk.veritaslabs.vesta.location.TinyPack

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
