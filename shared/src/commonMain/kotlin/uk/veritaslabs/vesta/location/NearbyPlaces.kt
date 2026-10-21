package uk.veritaslabs.vesta.location

/** How a nearby locality relates to the home locality. */
enum class NearbyRelation { Neighbour, InsideOf, Contains, NeighbourAndNested }

data class NearbyRow(val id: String, val name: String, val relation: NearbyRelation, val distanceMetres: Double)

data class NearbyPage(val rows: List<NearbyRow>, val hasMore: Boolean)

/** The "also search nearby places" list (ADR-039 decision 3, step 5), kept free of UI so it can be unit-tested. */
object NearbyPlaces {
    const val DEFAULT_SHOWN = 10

    /**
     * Candidates are the home locality's neighbours, the localities nested in it and the locality it is nested in, each
     * once and never the home itself. Nearest first (ties by name); only the first [shown] rows are returned.
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

        val all = candidates.values.map { row ->
            val nested = row.id in contained || row.id == inside
            val relation = when {
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
