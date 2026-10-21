package uk.veritaslabs.vesta.location

/**
 * Where a Home is (ADR-039): the country and locality it is in, the locality pack [release] the locality id belongs to,
 * and the localities the household also shops and goes out in ([area]). [cell] and [cellSource] are the optional
 * finer position, [cellSource] saying how [cell] was obtained.
 */
data class HomeLocation(
    val country: String,
    val homeLocality: String,
    val release: String,
    val cell: String? = null,
    val cellSource: String? = null,
    val area: Set<String> = emptySet(),
)

/**
 * Reads and writes a Home's [HomeLocation]. It lives on the Home's entry in the user's own registry, so it syncs between
 * the user's own devices only. Every call reads through, because an edit made on another device is not announced here.
 */
interface HomeLocationRepository {
    /** The location of the Home with [homeId]; null when it has none, or the Home is absent or deleted. */
    suspend fun read(homeId: String): HomeLocation?

    /** Sets where the Home is. A new home invalidates the cell and the search area, so both are cleared. */
    suspend fun setHome(homeId: String, country: String, localityId: String, release: String)

    /** Makes [localityIds] the search area: the ids not already ticked are added, those no longer in the set are unticked. */
    suspend fun setArea(homeId: String, localityIds: Set<String>)

    /** Forgets the Home's whole location; its name and kind are untouched. */
    suspend fun clear(homeId: String)
}

/** For platforms without the registry (iOS): nothing is known and nothing is stored. */
object UnavailableHomeLocationRepository : HomeLocationRepository {
    override suspend fun read(homeId: String): HomeLocation? = null
    override suspend fun setHome(homeId: String, country: String, localityId: String, release: String) = Unit
    override suspend fun setArea(homeId: String, localityIds: Set<String>) = Unit
    override suspend fun clear(homeId: String) = Unit
}
