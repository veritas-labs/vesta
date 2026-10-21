package uk.veritaslabs.vesta.location

/**
 * The saved area as names-table rows: the home locality first, then the ticked ones nearest-first by distance between
 * centroids. [homeCentroid] is the point every business distance is measured from (never a device position).
 */
class AreaPlan(val homeRow: Int, val selectedRows: List<Int>, val homeCentroid: Microdegrees) {
    /** Home first, then [selectedRows]; each row once. */
    val rows: List<Int> = listOf(homeRow) + selectedRows.filter { it != homeRow }.distinct()

    companion object {
        /** Resolves ids against [directory]. Null when the home id is not in the pack; unknown neighbour ids are dropped. */
        fun of(homeId: String, areaIds: Collection<String>, directory: LocalityDirectory): AreaPlan? {
            val home = directory.rowOf(homeId) ?: return null
            val centroid = directory.centroid(homeId) ?: return null
            val selected = areaIds.mapNotNull { id -> directory.rowOf(id)?.takeIf { it != home } }.distinct()
                .map { it to (directory.distanceMetres(homeId, directory.idOf(it)) ?: Double.MAX_VALUE) }
                .sortedWith(compareBy({ it.second }, { it.first })).map { it.first }
            return AreaPlan(home, selected, centroid)
        }

        fun of(location: HomeLocation, directory: LocalityDirectory): AreaPlan? =
            of(location.homeLocality, location.area, directory)
    }
}

/** One locality of the area as the summary shows it. */
data class BusinessAreaRow(val row: Int, val id: String, val name: String, val inside: Int, val nearby: Int, val distanceMetres: Double)

/** What the area holds, from the places directory alone: nothing has been downloaded. */
data class BusinessAreaCounts(
    val inside: Int,
    val nearby: Int,
    /** Compressed bytes of the distinct chunks needed for inside and nearby places. */
    val bytesWithNearby: Long,
    /** Compressed bytes of the distinct chunks needed for inside places only. */
    val bytesInsideOnly: Long,
    /** Home first, then nearest first. */
    val rows: List<BusinessAreaRow>,
) {
    val total: Int get() = inside + nearby
}

data class BusinessRow(
    val id: String,
    val name: String,
    val categoryName: String,
    val groupName: String,
    /** From the home locality's centroid. */
    val distanceMetres: Double,
    /** Names-table row of the locality that listed it. */
    val localityRow: Int,
    /** True when outside every polygon: the UI must not say "in <locality>". */
    val nearby: Boolean,
)

data class BusinessPage(val items: List<BusinessRow>, val total: Int, val hasMore: Boolean)

data class BusinessGroupCount(val key: String, val name: String, val count: Int)

/** The nearby business list (ADR-039), UI-free. Sums and sorts what was fetched; reads no data itself. */
object NearbyBusinesses {
    const val PAGE_SIZE = 50
    const val UNKNOWN_GROUP = "unknown"

    fun summarise(plan: AreaPlan, places: PlacesDirectory, localities: LocalityDirectory): BusinessAreaCounts {
        val home = localities.idOf(plan.homeRow)
        val rows = plan.rows.filter { it in 0 until places.size }.map {
            val id = localities.idOf(it)
            BusinessAreaRow(
                it, id, localities.row(id)?.name.orEmpty(), places.insideCount(it), places.nearbyCount(it),
                if (it == plan.homeRow) 0.0 else localities.distanceMetres(home, id) ?: Double.MAX_VALUE,
            )
        }
        val numbers = rows.map { it.row }
        return BusinessAreaCounts(
            inside = rows.sumOf { it.inside },
            nearby = rows.sumOf { it.nearby },
            bytesWithNearby = places.bytesFor(numbers, true),
            bytesInsideOnly = places.bytesFor(numbers, false),
            rows = rows,
        )
    }

    /**
     * [places] maps a names-table row to its decoded places. Only rows in [area] count. A place listed by several rows
     * appears once, under the inside row if any, else the row nearest the home. [query] matches the start of the name or
     * of a word in it, ignoring case and accents; [groupFilter] is a group key from [groupsWithCounts] (null = all).
     */
    fun list(
        places: Map<Int, List<PlaceRecord>>,
        area: AreaPlan,
        query: String,
        groupFilter: String?,
        categories: CategoryTable,
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): BusinessPage {
        val q = foldForSearch(query).trim()
        val matches = unique(places, area).filter { (place, _) ->
            (groupFilter == null || groupKey(place, categories) == groupFilter) &&
                (q.isEmpty() || matchesName(foldForSearch(place.name), q))
        }.map { (place, row) ->
            Sortable(place, row, LocalityDirectory.distanceMetres(area.homeCentroid, place.latitude, place.longitude), foldForSearch(place.name))
        }.sortedWith(compareBy<Sortable>({ it.distance }, { it.folded }, { it.place.name }, { it.place.id }))

        val from = offset.coerceIn(0, matches.size)
        val to = (from.toLong() + limit.coerceAtLeast(0)).coerceAtMost(matches.size.toLong()).toInt()
        val items = matches.subList(from, to).map { (place, row, distance) ->
            BusinessRow(
                place.id, place.name, categoryName(place, categories), groupName(place, categories),
                distance, row, place.nearby,
            )
        }
        return BusinessPage(items, matches.size, to < matches.size)
    }

    /** Group chips: most places first (ties by name), the unknown group last. With [area] only its rows count. */
    fun groupsWithCounts(places: Map<Int, List<PlaceRecord>>, categories: CategoryTable, area: AreaPlan? = null): List<BusinessGroupCount> {
        val all = area?.let { unique(places, it) } ?: places.keys.sorted().flatMap { row -> places.getValue(row).map { it to row } }
            .distinctBy { it.first.id }
        return all.groupingBy { groupKey(it.first, categories) }.eachCount()
            .map { (key, count) -> BusinessGroupCount(key, displayGroup(key, categories), count) }
            .sortedWith(compareBy<BusinessGroupCount>({ it.key == UNKNOWN_GROUP }, { -it.count }, { it.name }))
    }

    private data class Sortable(val place: PlaceRecord, val row: Int, val distance: Double, val folded: String)

    /** Each place once with the row that listed it: inside beats nearby, then the row earlier in the area order. */
    private fun unique(places: Map<Int, List<PlaceRecord>>, area: AreaPlan): List<Pair<PlaceRecord, Int>> {
        val best = LinkedHashMap<String, Pair<PlaceRecord, Int>>()
        area.rows.forEach { row ->
            places[row]?.forEach { place ->
                val current = best[place.id]
                if (current == null || (current.first.nearby && !place.nearby)) best[place.id] = place to row
            }
        }
        return best.values.toList()
    }

    private fun matchesName(folded: String, q: String): Boolean {
        if (folded.startsWith(q)) return true
        var i = folded.indexOf(q)
        while (i >= 0) {
            if (i > 0 && !folded[i - 1].isLetterOrDigit()) return true
            i = folded.indexOf(q, i + 1)
        }
        return false
    }

    private fun groupIndex(place: PlaceRecord, categories: CategoryTable): Int? =
        place.categoryIndex.takeIf { it in 0 until categories.categoryCount }?.let { categories.groupOf(it) }

    private fun groupKey(place: PlaceRecord, categories: CategoryTable): String =
        groupIndex(place, categories)?.let { categories.groupKeys[it] } ?: UNKNOWN_GROUP

    private fun groupName(place: PlaceRecord, categories: CategoryTable): String =
        groupIndex(place, categories)?.let { categories.groupName(it) } ?: "Unknown"

    private fun categoryName(place: PlaceRecord, categories: CategoryTable): String =
        place.categoryIndex.takeIf { it in 0 until categories.categoryCount }?.let { categories.categoryName(it) } ?: "Unknown"

    private fun displayGroup(key: String, categories: CategoryTable): String =
        categories.groupKeys.indexOf(key).takeIf { it >= 0 }?.let { categories.groupName(it) } ?: "Unknown"
}
