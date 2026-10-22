package uk.veritaslabs.vesta.location

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs [NearbyBusinesses] against the real GB places pack. Runs only when `VESTA_PLACES_FIXTURE_DIR` and
 * `VESTA_LOCALITY_FIXTURE_DIR` name the exported directories (see [RealPlacesPackTest]); otherwise it does nothing.
 */
class RealNearbyBusinessesTest {

    private val placesDir = System.getenv("VESTA_PLACES_FIXTURE_DIR")?.let(::File)?.takeIf { it.isDirectory }
    private val localityDir = System.getenv("VESTA_LOCALITY_FIXTURE_DIR")?.let(::File)?.takeIf { it.isDirectory }
    private val inflater = JvmInflater()

    private class Real(val localities: LocalityDirectory, val places: PlacesDirectory, val categories: CategoryTable, val root: PlacesRoot, val dir: File) {
        fun fetch(plan: AreaPlan): Map<Int, List<PlaceRecord>> {
            val inflater = JvmInflater()
            val cache = HashMap<Int, ByteArray>()
            return plan.rows.associateWith { row ->
                places.rangesFor(row).flatMap { range ->
                    val bytes = cache.getOrPut(range.chunk) { PlaceUnit.decompress(File(dir, root.chunkAddresses[range.chunk]).readBytes(), inflater) }
                    PlaceUnit.decodeRange(bytes, range)
                }
            }
        }
    }

    private fun load(): Real? {
        val places = placesDir ?: return null
        val localities = localityDir ?: return null
        val root = PlacesRoot.parse(File(places, "root.bin").readBytes())
        val pack = LocalityPack.parse(File(localities, "names.bin").readBytes(), File(localities, "adjacency.bin").readBytes(), inflater)
        return Real(
            LocalityDirectory(pack),
            PlacesDirectory.parse(File(places, root.directoryAddress).readBytes(), inflater),
            CategoryTable.parse(File(places, root.categoriesAddress).readBytes(), inflater),
            root,
            places,
        )
    }

    private fun plan(real: Real, homeId: String, neighbours: Int): AreaPlan =
        AreaPlan.of(homeId, real.localities.neighbours(homeId).take(neighbours).map { it.id }, real.localities)!!

    @Test
    fun summarisesAndListsTheRealAreas() {
        // Arrange
        val real = load() ?: return
        val homes = listOf("Queen's Park", "Queen’s Park", "Cowes", "Hexham").mapNotNull { q ->
            real.localities.search(q, 5).firstOrNull { it.subtype == LocalityDirectory.LOCALITY }
        }.distinctBy { it.id }
        var biggest: AreaPlan? = null
        var biggestTotal = -1

        // Act
        homes.forEach { home ->
            val plan = plan(real, home.id, 4)
            val counts = NearbyBusinesses.summarise(plan, real.places, real.localities)
            println("AREA ${home.name} (${real.localities.chainText(home.id)}): inside=${counts.inside} nearby=${counts.nearby} " +
                "bytesWithNearby=${counts.bytesWithNearby} bytesInsideOnly=${counts.bytesInsideOnly}")
            counts.rows.forEach { println("  row ${it.row} ${it.name}: inside=${it.inside} nearby=${it.nearby} ${it.distanceMetres.toInt()} m") }
            val places = real.fetch(plan)
            val page = NearbyBusinesses.list(places, plan, "", null, real.categories)
            println("  first page of ${page.total}, hasMore=${page.hasMore}:")
            page.items.take(8).forEach { println("    ${it.name} | ${it.categoryName} | ${it.groupName} | ${it.distanceMetres.toInt()} m | nearby=${it.nearby}") }
            println("  groups: " + NearbyBusinesses.groupsWithCounts(places, real.categories, plan).joinToString { "${it.name}=${it.count}" })
            if (counts.total > biggestTotal) {
                biggestTotal = counts.total
                biggest = plan
            }
        }
        val big = biggest
        val bigPlaces = big?.let { real.fetch(it) }.orEmpty()
        val timings = big?.let { plan ->
            (1..5).map {
                val t0 = System.nanoTime()
                val page = NearbyBusinesses.list(bigPlaces, plan, "", null, real.categories)
                val searched = NearbyBusinesses.list(bigPlaces, plan, "cafe", "food_and_drink", real.categories)
                val ms = (System.nanoTime() - t0) / 1e6
                println("TIMING run $it: ${ms.toLong()} ms (total ${page.total}, searched ${searched.total})")
                ms
            }
        }.orEmpty()

        // Assert
        assertTrue(homes.isNotEmpty(), "no real home locality found")
        biggest?.let { plan ->
            val page = NearbyBusinesses.list(bigPlaces, plan, "", null, real.categories)
            assertEquals(50, page.items.size)
            assertTrue(page.sections.first().localityRow == plan.homeRow)
            assertEquals(page.items, page.sections.flatMap { it.items })
            assertTrue(page.sections.first().items.zipWithNext().all { (a, b) -> foldForSearch(a.name) <= foldForSearch(b.name) })
            assertEquals(page.total, bigPlaces.values.flatten().map { it.id }.distinct().size)
        }
        assertTrue(timings.isEmpty() || timings.last() < 5_000)
    }

    @Test
    fun printsTheDirectoryBrowseNumbersForRealAreas() {
        // Arrange
        val real = load() ?: return
        val orlestone = real.localities.search("Orlestone", 10).filter { it.subtype == LocalityDirectory.LOCALITY }
            .let { found -> found.firstOrNull { real.localities.chainText(it.id).contains("Kent") } ?: found.firstOrNull() }
        val queens = listOf("Queen's Park", "Queen\u2019s Park").firstNotNullOfOrNull { q ->
            real.localities.search(q, 5).firstOrNull { it.subtype == LocalityDirectory.LOCALITY }
        }
        val homes = listOfNotNull(orlestone, queens)

        // Act
        homes.forEach { home ->
            val plan = plan(real, home.id, 4)
            val places = real.fetch(plan)
            val t0 = System.nanoTime()
            val groups = NearbyBusinesses.groupsWithCounts(places, real.categories, plan)
            val perGroup = groups.associate { g -> g.key to NearbyBusinesses.categoriesWithCounts(places, real.categories, plan, g.key) }
            val top = NearbyBusinesses.categoriesWithCounts(places, real.categories, plan)
            val page = NearbyBusinesses.list(places, plan, "", null, real.categories) { real.localities.nameAt(it) }
            val ms = (System.nanoTime() - t0) / 1e6
            println("BROWSE ${home.name} (${real.localities.chainText(home.id)}) rows=${plan.rows.size} " +
                "businesses=${places.values.flatten().map { it.id }.distinct().size} groups=${groups.size} " +
                "distinctCategories=${top.size} time=${ms.toLong()} ms")
            println("  categories per group (largest): " + perGroup.entries.sortedByDescending { it.value.size }.take(4)
                .joinToString { "${it.key}=${it.value.size}" })
            println("  top 5 categories: " + top.take(5).joinToString { "${it.name}=${it.count}" })
            page.sections.firstOrNull()?.let { section ->
                println("  first section ${section.localityName} (${section.items.size} on page): " + section.items.take(5).joinToString { it.name })
            }

            // Assert
            assertEquals(groups.sumOf { it.count }, places.values.flatten().map { it.id }.distinct().size)
            assertEquals(top.sumOf { it.count }, groups.filter { it.key != NearbyBusinesses.UNKNOWN_GROUP }.sumOf { it.count })
            assertTrue(perGroup[NearbyBusinesses.UNKNOWN_GROUP].orEmpty().isEmpty())
        }
        assertTrue(homes.isNotEmpty())
    }
}
