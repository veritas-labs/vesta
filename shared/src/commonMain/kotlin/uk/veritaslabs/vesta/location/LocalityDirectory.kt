package uk.veritaslabs.vesta.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One row of the pack, looked up by id. */
data class LocalityRow(val id: String, val name: String, val subtype: String)

/** Lat/lon in millionths of a degree (the unit ADR-026 uses). */
data class Microdegrees(val lat: Int, val lon: Int)

/** Read-only queries over a [LocalityPack]. Ids are canonical lowercase UUID strings. */
class LocalityDirectory(private val pack: LocalityPack) {

    private val rowById: Map<String, Int> by lazy {
        HashMap<String, Int>(pack.size * 2).also { map -> for (row in 0 until pack.size) map[pack.uuid(row)] = row }
    }

    private val folded: Array<String> by lazy { Array(pack.size) { foldForSearch(pack.name(it)) } }

    private val children: Array<IntArray> by lazy {
        val counts = IntArray(pack.size)
        for (row in 0 until pack.size) pack.parentRow(row).takeIf { it >= 0 }?.let { counts[it]++ }
        val lists = Array(pack.size) { IntArray(counts[it]) }
        val filled = IntArray(pack.size)
        for (row in 0 until pack.size) pack.parentRow(row).takeIf { it >= 0 }?.let { lists[it][filled[it]++] = row }
        lists
    }

    val size: Int get() = pack.size

    fun row(id: String): LocalityRow? = rowIndex(id)?.let { rowAt(it) }

    /** Localities whose name matches [query], best first; at most [limit]. A blank query matches nothing. */
    fun search(query: String, limit: Int = 20): List<LocalityRow> {
        val q = foldForSearch(query).trim()
        if (q.isEmpty() || limit <= 0) return emptyList()
        val hits = ArrayList<Hit>()
        for (row in 0 until pack.size) {
            if (pack.subtype(row) != LOCALITY) continue
            val rank = rank(folded[row], q) ?: continue
            hits += Hit(row, rank)
        }
        return hits
            .sortedWith(compareBy<Hit> { it.rank }.thenBy { pack.name(it.row).length }.thenBy { pack.name(it.row) })
            .take(limit)
            .map { rowAt(it.row) }
    }

    /** From the locality up to the country, e.g. Newport, Isle of Wight, England, United Kingdom. Empty for an unknown id. */
    fun chain(id: String): List<LocalityRow> {
        var row = rowIndex(id) ?: return emptyList()
        val result = ArrayList<LocalityRow>()
        while (row >= 0 && result.size <= pack.size) {
            result += rowAt(row)
            row = pack.parentRow(row)
        }
        return result
    }

    /** [chain] as "Newport, Isle of Wight, England": names joined with ", ", the country row dropped. */
    fun chainText(id: String): String = chain(id).filter { it.subtype != COUNTRY }.joinToString(", ") { it.name }

    fun neighbours(id: String): List<LocalityRow> {
        val row = rowIndex(id) ?: return emptyList()
        return (pack.adjacencyStart[row] until pack.adjacencyStart[row + 1]).map { rowAt(pack.adjacency[it]) }
    }

    /** The parent, when the parent is itself a locality. */
    fun insideOf(id: String): LocalityRow? {
        val parent = rowIndex(id)?.let { pack.parentRow(it) }?.takeIf { it >= 0 } ?: return null
        return rowAt(parent).takeIf { it.subtype == LOCALITY }
    }

    /** Localities whose parent is this one. */
    fun contains(id: String): List<LocalityRow> {
        val row = rowIndex(id) ?: return emptyList()
        return children[row].filter { pack.subtype(it) == LOCALITY }.map { rowAt(it) }
    }

    fun centroid(id: String): Microdegrees? = rowIndex(id)?.let { Microdegrees(pack.latE6[it], pack.lonE6[it]) }

    /** Great-circle distance between two centroids, or null when either id is unknown. */
    fun distanceMetres(a: String, b: String): Double? {
        val from = centroid(a) ?: return null
        val to = centroid(b) ?: return null
        return distanceMetres(from, to.lat / MICRO, to.lon / MICRO)
    }

    /** The names-table row of [id], the index the places directory uses; null when the id is not in the pack. */
    fun rowOf(id: String): Int? = rowIndex(id)

    /** The id at names-table [row]. */
    fun idOf(row: Int): String = pack.uuid(row)

    /** The name of a names-table row. */
    fun nameAt(row: Int): String = pack.name(row)

    private fun rowIndex(id: String): Int? = rowById[id.lowercase()]

    private fun rowAt(row: Int) = LocalityRow(pack.uuid(row), pack.name(row), pack.subtype(row))

    private class Hit(val row: Int, val rank: Int)

    private fun rank(name: String, query: String): Int? {
        if (name.startsWith(query)) return 0
        var i = name.indexOf(query)
        if (i < 0) return null
        while (i >= 0) {
            if (i > 0 && !name[i - 1].isLetterOrDigit()) return 1
            i = name.indexOf(query, i + 1)
        }
        return 2
    }

    companion object {
        const val LOCALITY = "locality"
        const val COUNTRY = "country"
        private const val MICRO = 1_000_000.0
        private const val RADIANS = 3.141592653589793 / 180.0
        private const val EARTH_RADIUS_METRES = 6_371_000.0

        /** Great-circle distance in metres from [from] to a point given in degrees. */
        internal fun distanceMetres(from: Microdegrees, latitude: Double, longitude: Double): Double {
            val lat1 = from.lat / MICRO * RADIANS
            val lat2 = latitude * RADIANS
            val dLat = lat2 - lat1
            val dLon = (longitude - from.lon / MICRO) * RADIANS
            val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_METRES * atan2(sqrt(h), sqrt(1 - h))
        }
    }
}
