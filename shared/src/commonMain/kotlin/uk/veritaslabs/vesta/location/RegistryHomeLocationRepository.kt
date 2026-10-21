package uk.veritaslabs.vesta.location

import uk.veritaslabs.kits.spaces.SpaceAttributes

private const val Country = "locality.country"
private const val Home = "locality.home"
private const val Release = "locality.release"
private const val Cell = "locality.cell"
private const val CellSource = "locality.cellSource"
private const val LocalityPrefix = "locality."
private const val AreaPrefix = "area."
private const val Included = "1"
private const val Excluded = "0"

/**
 * [HomeLocationRepository] stored as string attributes on the Home's entry in the user's Space registry, through [SpaceAttributes]. The area is one key per
 * locality, `area.<localityId>`, "1" when included and "0" when it was included and then removed: a removal is a value, not a
 * deleted key, so an edit on one device and a tick on another merge instead of one undoing the other.
 */
class RegistryHomeLocationRepository(private val registry: SpaceAttributes) : HomeLocationRepository {

    override suspend fun read(homeId: String): HomeLocation? {
        val attributes = registry.attributes(homeId)
        val country = attributes[Country] ?: return null
        val home = attributes[Home] ?: return null
        val release = attributes[Release] ?: return null
        return HomeLocation(
            country = country,
            homeLocality = home,
            release = release,
            cell = attributes[Cell],
            cellSource = attributes[CellSource],
            area = attributes.filter { (key, value) -> key.startsWith(AreaPrefix) && value == Included }.keys.map { it.removePrefix(AreaPrefix) }.toSet(),
        )
    }

    override suspend fun setHome(homeId: String, country: String, localityId: String, release: String) {
        val changes = mutableMapOf<String, String?>(Country to country, Home to localityId, Release to release, Cell to null, CellSource to null)
        registry.attributes(homeId).keys.filter { it.startsWith(AreaPrefix) }.forEach { changes[it] = null }
        registry.setAttributes(homeId, changes)
    }

    override suspend fun setArea(homeId: String, localityIds: Set<String>) {
        val current = registry.attributes(homeId)
        val changes = mutableMapOf<String, String?>()
        localityIds.forEach { id -> if (current[AreaPrefix + id] != Included) changes[AreaPrefix + id] = Included }
        current.forEach { (key, value) ->
            if (key.startsWith(AreaPrefix) && value == Included && key.removePrefix(AreaPrefix) !in localityIds) changes[key] = Excluded
        }
        if (changes.isNotEmpty()) registry.setAttributes(homeId, changes)
    }

    override suspend fun clear(homeId: String) {
        val keys = registry.attributes(homeId).keys.filter { it.startsWith(LocalityPrefix) || it.startsWith(AreaPrefix) }
        if (keys.isNotEmpty()) registry.setAttributes(homeId, keys.associateWith { null })
    }
}
