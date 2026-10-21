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
            assertTrue(page.items.zipWithNext().all { (a, b) -> a.distanceMetres <= b.distanceMetres })
            assertEquals(page.total, bigPlaces.values.flatten().map { it.id }.distinct().size)
        }
        assertTrue(timings.isEmpty() || timings.last() < 5_000)
    }
}
