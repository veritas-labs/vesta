package uk.veritaslabs.vesta.location

/** How a nearby locality relates to the home locality. */
/** [Further] is a locality that touches a neighbour but not the home, offered when the home has few neighbours. */
enum class NearbyRelation { Neighbour, InsideOf, Contains, NeighbourAndNested, Further }

data class NearbyRow(val id: String, val name: String, val relation: NearbyRelation, val distanceMetres: Double)

data class NearbyPage(val rows: List<NearbyRow>, val hasMore: Boolean)

/** The "also search nearby places" list (ADR-039 decision 3, step 5), kept free of UI so it can be unit-tested. */
object NearbyPlaces {
    const val DEFAULT_SHOWN = 10

    /** A home with fewer candidates than this is widened to the neighbours of its neighbours. */
    const val MIN_CANDIDATES = 10

    private const val MAX_RINGS = 3

    /**
     * Candidates are the home locality's neighbours, the localities nested in it and the locality it is nested in, each
     * once and never the home itself. When that gives fewer than [MIN_CANDIDATES], localities touching them are added ring by
     * ring (at most [MAX_RINGS]) until there are enough, marked [NearbyRelation.Further]. Nearest first (ties by name); only the first [shown] rows are returned.
     */
    fun rows(home: String, directory: LocalityDirectory, shown: Int = DEFAULT_SHOWN): NearbyPage {
        val homeId = directory.row(home)?.id ?: return NearbyPage(emptyList(), false)
        val neighbours = directory.neighbours(homeId).map { it.id }.toSet()
        val contained = directory.contains(homeId).map { it.id }.toSet()
        val inside = directory.insideOf(homeId)?.id

        val candidates = LinkedHashMap<String, LocalityRow>()
        directory.neighbours(homeId).forEach { candidates[it.id] = it }
        directory.contains(homeId).forEach { candidates[it.id] = it }
        directory.insideOf(homeId)?.let { candidates[it.id] = it }
        candidates.remove(homeId)

        val further = HashSet<String>()
        var ring = candidates.values.toList()
        var rings = 0
        while (candidates.size < MIN_CANDIDATES && ring.isNotEmpty() && rings < MAX_RINGS) {
            val next = ArrayList<LocalityRow>()
            ring.forEach { from ->
                directory.neighbours(from.id).forEach { row ->
                    if (row.id != homeId && row.id !in candidates) {
                        candidates[row.id] = row
                        further += row.id
                        next += row
                    }
                }
            }
            ring = next
            rings++
        }

        val all = candidates.values.map { row ->
            val nested = row.id in contained || row.id == inside
            val relation = when {
                row.id in further -> NearbyRelation.Further
                row.id in neighbours && nested -> NearbyRelation.NeighbourAndNested
                row.id in neighbours -> NearbyRelation.Neighbour
                row.id == inside -> NearbyRelation.InsideOf
                else -> NearbyRelation.Contains
            }
            NearbyRow(row.id, row.name, relation, directory.distanceMetres(homeId, row.id) ?: Double.MAX_VALUE)
        }.sortedWith(compareBy<NearbyRow> { it.distanceMetres }.thenBy { it.name })

        val count = shown.coerceAtLeast(0)
        return NearbyPage(all.take(count), all.size > count)
    }
}
