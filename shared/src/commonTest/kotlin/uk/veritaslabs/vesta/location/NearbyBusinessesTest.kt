package uk.veritaslabs.vesta.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NearbyBusinessesTest {

    private val directory = TinyPack.directory()
    private val categories = CategoryTable.parse(TinyPlaces.categoryTable(), TinyPlaces.identity)

    // Newport (row 3) is the home; Shide (13) is about 1 km away, Carisbrooke (7) about 2 km, Cowes (5) about 7 km.
    private val homeId = TinyPack.id(3)
    private val plan = AreaPlan.of(homeId, listOf(TinyPack.id(5), TinyPack.id(7)), directory)!!

    private fun rec(n: Int, name: String = "Place $n", dLat: Double = 0.0, category: Int = 0, nearby: Boolean = false) =
        PlaceRecord(TinyPlaces.uuid(n), name, 50.70 + dLat, -1.29, 1.0, category, nearby)

    private fun list(
        places: Map<Int, List<PlaceRecord>>,
        query: String = "",
        group: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        categoryFilter: Int? = null,
    ) = NearbyBusinesses.list(places, plan, query, group, categories, limit, offset, categoryFilter = categoryFilter)

    /** Row 3: chunk 0 inside + nearby; row 5: chunk 1 inside; row 7: chunk 1 and chunk 0 nearby. */
    private fun placesDirectory(): PlacesDirectory {
        val built = TinyPlaces.build()
        val ranges = List(14) { row ->
            when (row) {
                3 -> built.ranges[0]
                5 -> built.ranges[1]
                7 -> built.ranges[2]
                else -> emptyList()
            }
        }
        return PlacesDirectory.parse(TinyPlaces.directory(ranges = ranges), TinyPlaces.identity)
    }

    @Test
    fun planPutsHomeFirstThenNeighboursNearestFirst() {
        // Arrange
        val ids = listOf(TinyPack.id(5), TinyPack.id(7), TinyPack.id(13), "unknown-id", TinyPack.id(3))

        // Act
        val result = AreaPlan.of(homeId, ids, directory)

        // Assert
        assertEquals(listOf(3, 13, 7, 5), result?.rows)
        assertEquals(3, result?.homeRow)
        assertEquals(listOf(13, 7, 5), result?.selectedRows)
    }

    @Test
    fun planIsNullWhenTheHomeIsNotInThePack() {
        // Arrange / Act
        val result = AreaPlan.of("00000000-0000-0000-0000-ffffffffffff", emptyList(), directory)

        // Assert
        assertNull(result)
    }

    @Test
    fun planFromHomeLocationUsesItsArea() {
        // Arrange
        val location = HomeLocation("GB", homeId, "r", area = setOf(TinyPack.id(5)))

        // Act
        val result = AreaPlan.of(location, directory)

        // Assert
        assertEquals(listOf(3, 5), result?.rows)
    }

    @Test
    fun emptyAreaHasZeroCountsAndNoBytes() {
        // Arrange
        val alone = AreaPlan.of(TinyPack.id(6), emptyList(), directory)!!

        // Act
        val counts = NearbyBusinesses.summarise(alone, placesDirectory(), directory)

        // Assert
        assertEquals(0, counts.total)
        assertEquals(0L, counts.bytesWithNearby)
        assertEquals(0L, counts.bytesInsideOnly)
        assertEquals(listOf(6), counts.rows.map { it.row })
    }

    @Test
    fun countsAndBytesComeFromTheDirectoryAndShareChunksOnce() {
        // Arrange
        val dir = placesDirectory()

        // Act
        val counts = NearbyBusinesses.summarise(plan, dir, directory)

        // Assert
        assertEquals(6, counts.inside)
        assertEquals(3, counts.nearby)
        assertEquals(listOf(3, 7, 5), counts.rows.map { it.row })
        assertEquals(350L, counts.bytesWithNearby)
        assertEquals(350L, counts.bytesInsideOnly)
        assertEquals(0.0, counts.rows.first().distanceMetres)
        assertTrue(counts.rows[1].distanceMetres < counts.rows[2].distanceMetres)
    }

    @Test
    fun insideOnlyBytesIgnoreChunksOnlyNearbyPlacesNeed() {
        // Arrange
        val withCarisbrooke = AreaPlan.of(homeId, listOf(TinyPack.id(7)), directory)!!

        // Act
        val counts = NearbyBusinesses.summarise(withCarisbrooke, placesDirectory(), directory)

        // Assert: Carisbrooke only has nearby places, in chunk 1 as well as chunk 0
        assertEquals(350L, counts.bytesWithNearby)
        assertEquals(100L, counts.bytesInsideOnly)
        assertEquals(3, counts.inside)
        assertEquals(3, counts.nearby)
    }

    @Test
    fun sortsAToZByFoldedNameThenNameThenIdIgnoringDistance() {
        // Arrange
        val tied = listOf(rec(2, "Bravo"), rec(1, "bravo"), rec(3, "Alpha"), rec(5, "Bravo"))
        val far = rec(4, "Aardvark", dLat = 0.05)
        val near = rec(6, "Zed", dLat = 0.001)

        // Act
        val page = list(mapOf(3 to tied + far + near))

        // Assert
        assertEquals(listOf(4, 3, 2, 5, 1, 6).map { TinyPlaces.uuid(it) }, page.items.map { it.id })
    }

    @Test
    fun accentedNamesSortWithTheirPlainLetters() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, "Cafe Zed"), rec(2, "Café Alpha"), rec(3, "Cabin"), rec(4, "Cafe Beta")))

        // Act
        val page = list(places)

        // Assert
        assertEquals(listOf("Cabin", "Café Alpha", "Cafe Beta", "Cafe Zed"), page.items.map { it.name })
    }

    @Test
    fun sectionsFollowAreaOrderWithHomeFirstThenNearestAndSortAToZInside() {
        // Arrange: area rows are 3 (home), 7 (Carisbrooke), 5 (Cowes)
        val places = mapOf(
            5 to listOf(rec(1, "Cowes Baker")),
            7 to listOf(rec(2, "Zeta"), rec(3, "Alpha")),
            3 to listOf(rec(4, "Newport Pub")),
        )

        // Act
        val page = NearbyBusinesses.list(places, plan, "", null, categories, localityName = { "Row $it" })

        // Assert
        assertEquals(listOf(3, 7, 5), page.sections.map { it.localityRow })
        assertEquals(listOf("Row 3", "Row 7", "Row 5"), page.sections.map { it.localityName })
        assertEquals(listOf("Alpha", "Zeta"), page.sections[1].items.map { it.name })
        assertEquals(page.items, page.sections.flatMap { it.items })
    }

    @Test
    fun insideAndNearbyPlacesMixInOneSectionAToZ() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, "Charlie"), rec(2, "Alpha", nearby = true), rec(3, "Bravo"), rec(4, "Delta", nearby = true)))

        // Act
        val page = list(places)

        // Assert
        assertEquals(1, page.sections.size)
        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), page.sections.single().items.map { it.name })
        assertEquals(listOf(true, false, false, true), page.sections.single().items.map { it.nearby })
    }

    @Test
    fun aSectionCutByAPageBoundaryRepeatsItsHeaderOnTheNextPage() {
        // Arrange: 3 in the home section, 3 in the next; pages of 4
        val places = mapOf(
            3 to listOf(rec(1, "A1"), rec(2, "A2"), rec(3, "A3")),
            7 to listOf(rec(4, "B1"), rec(5, "B2"), rec(6, "B3")),
        )

        // Act
        val first = NearbyBusinesses.list(places, plan, "", null, categories, limit = 4)
        val second = NearbyBusinesses.list(places, plan, "", null, categories, limit = 4, offset = 4)

        // Assert
        assertEquals(listOf(3 to listOf("A1", "A2", "A3"), 7 to listOf("B1")), first.sections.map { it.localityRow to it.items.map { i -> i.name } })
        assertEquals(listOf(7 to listOf("B2", "B3")), second.sections.map { it.localityRow to it.items.map { i -> i.name } })
        assertTrue(first.hasMore)
        assertFalse(second.hasMore)
        assertEquals(6, second.total)
        assertEquals(6, (first.items + second.items).map { it.id }.distinct().size)
    }

    @Test
    fun categoriesAreCountedOverDedupedAreaPlacesOrderedByCountThenName() {
        // Arrange: cafe (0) x1, hair salon (1) x2, pub (2) x2; place 1 listed twice; row 12 is outside the area
        val places = mapOf(
            3 to listOf(rec(1, category = 0), rec(2, category = 1), rec(3, category = 1), rec(4, category = 2)),
            5 to listOf(rec(1, category = 0), rec(5, category = 2)),
            12 to listOf(rec(6, category = 0)),
        )

        // Act
        val result = NearbyBusinesses.categoriesWithCounts(places, categories, plan)

        // Assert
        assertEquals(listOf(CategoryCount(1, "Hair Salon", 2), CategoryCount(2, "Pub", 2), CategoryCount(0, "Cafe", 1)), result)
    }

    @Test
    fun categoriesCanBeLimitedToOneGroup() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, category = 0), rec(2, category = 1), rec(3, category = 2), rec(4, category = 2)))

        // Act
        val food = NearbyBusinesses.categoriesWithCounts(places, categories, plan, "food_and_drink")
        val services = NearbyBusinesses.categoriesWithCounts(places, categories, plan, "services")

        // Assert
        assertEquals(listOf("Pub" to 2, "Cafe" to 1), food.map { it.name to it.count })
        assertEquals(listOf("Hair Salon" to 1), services.map { it.name to it.count })
    }

    @Test
    fun theUnknownGroupAndUncategorisedPlacesHaveNoCategoryEntries() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, category = 99), rec(2, category = -1), rec(3, category = 0)))

        // Act
        val unknown = NearbyBusinesses.categoriesWithCounts(places, categories, plan, NearbyBusinesses.UNKNOWN_GROUP)
        val all = NearbyBusinesses.categoriesWithCounts(places, categories, plan)

        // Assert
        assertTrue(unknown.isEmpty())
        assertEquals(listOf("Cafe" to 1), all.map { it.name to it.count })
    }

    @Test
    fun emptyAreaHasNoCategories() {
        // Arrange / Act
        val result = NearbyBusinesses.categoriesWithCounts(emptyMap(), categories, plan)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun categoryFilterKeepsOnePlaceKindAndKeepsSections() {
        // Arrange
        val places = mapOf(
            3 to listOf(rec(1, "Ann", category = 0), rec(2, "Bea", category = 2)),
            7 to listOf(rec(3, "Cat", category = 0)),
        )

        // Act
        val page = list(places, categoryFilter = 0)

        // Assert
        assertEquals(listOf("Ann", "Cat"), page.items.map { it.name })
        assertEquals(listOf(3, 7), page.sections.map { it.localityRow })
        assertEquals(2, page.total)
    }

    @Test
    fun groupCategoryAndSearchFiltersCombine() {
        // Arrange: pub (2) and cafe (0) are both food and drink; hair salon (1) is services
        val places = mapOf(3 to listOf(rec(1, "Alpha Cafe", category = 0), rec(2, "Alpha Pub", category = 2), rec(3, "Alpha Hair", category = 1)))

        // Act
        val matching = list(places, "alp", "food_and_drink", categoryFilter = 2)
        val mismatched = list(places, "alp", "services", categoryFilter = 2)

        // Assert
        assertEquals(listOf("Alpha Pub"), matching.items.map { it.name })
        assertEquals(0, mismatched.total)
        assertTrue(mismatched.sections.isEmpty())
    }

    @Test
    fun aPlaceWithoutACategoryIsListedAndNamedUnknown() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, "Mystery", category = 99)))

        // Act
        val page = list(places)

        // Assert
        assertEquals("Unknown", page.sections.single().items.single().categoryName)
    }

    @Test
    fun dedupeKeepsTheInsideRowSectionOnly() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, "Shared", nearby = true)), 5 to listOf(rec(1, "Shared")))

        // Act
        val page = list(places)

        // Assert
        assertEquals(listOf(5), page.sections.map { it.localityRow })
        assertEquals(1, page.total)
    }

    @Test
    fun distanceIsFromTheHomeCentroid() {
        // Arrange
        val place = rec(1, dLat = 0.01)

        // Act
        val item = list(mapOf(3 to listOf(place))).items.single()

        // Assert: 0.01 degrees of latitude is about 1112 m
        assertTrue(item.distanceMetres in 1100.0..1125.0, "was ${item.distanceMetres}")
    }

    @Test
    fun aPlaceListedByTwoRowsAppearsOnceUnderTheInsideRow() {
        // Arrange
        val inside = rec(1, nearby = false)
        val asNearby = rec(1, nearby = true)

        // Act
        val page = list(mapOf(3 to listOf(asNearby), 5 to listOf(inside)))

        // Assert
        assertEquals(1, page.total)
        assertEquals(5, page.items.single().localityRow)
        assertFalse(page.items.single().nearby)
    }

    @Test
    fun aPlaceNearbyOfTwoRowsKeepsTheRowNearestHome() {
        // Arrange
        val shared = rec(1, nearby = true)

        // Act
        val page = list(mapOf(5 to listOf(shared), 7 to listOf(shared)))

        // Assert
        assertEquals(1, page.total)
        assertEquals(7, page.items.single().localityRow)
        assertTrue(page.items.single().nearby)
    }

    @Test
    fun nearbyFlagIsPreserved() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1), rec(2, nearby = true)))

        // Act
        val page = list(places)

        // Assert
        assertEquals(mapOf(TinyPlaces.uuid(1) to false, TinyPlaces.uuid(2) to true), page.items.associate { it.id to it.nearby })
    }

    @Test
    fun rowsOutsideTheAreaAreIgnored() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1)), 12 to listOf(rec(2)))

        // Act
        val page = list(places)

        // Assert
        assertEquals(listOf(TinyPlaces.uuid(1)), page.items.map { it.id })
    }

    @Test
    fun searchFoldsCaseAndAccentsAndMatchesPrefixAndWordPrefix() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, "Café Nero"), rec(2, "The Red Cafe"), rec(3, "Scafell Shop"), rec(4, "Pub")))

        // Act
        val byAccent = list(places, "CAFE")
        val byAccentedQuery = list(places, "café")
        val multiWord = list(places, "cafe n")
        val blank = list(places, "   ")

        // Assert
        assertEquals(setOf(TinyPlaces.uuid(1), TinyPlaces.uuid(2)), byAccent.items.map { it.id }.toSet())
        assertEquals(byAccent.items, byAccentedQuery.items)
        assertEquals(listOf(TinyPlaces.uuid(1)), multiWord.items.map { it.id })
        assertEquals(4, blank.total)
    }

    @Test
    fun groupFilterKeepsOneGroupIncludingUnknown() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, category = 0), rec(2, category = 1), rec(3, category = 2), rec(4, category = 99)))

        // Act
        val food = list(places, group = "food_and_drink")
        val services = list(places, group = "services")
        val unknown = list(places, group = NearbyBusinesses.UNKNOWN_GROUP)

        // Assert
        assertEquals(setOf(TinyPlaces.uuid(1), TinyPlaces.uuid(3)), food.items.map { it.id }.toSet())
        assertEquals(listOf("Hair Salon"), services.items.map { it.categoryName })
        assertEquals(listOf("Services"), services.items.map { it.groupName })
        assertEquals(listOf(TinyPlaces.uuid(4)), unknown.items.map { it.id })
        assertEquals("Unknown", unknown.items.single().categoryName)
    }

    @Test
    fun groupFilterAndSearchCombine() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, "Alpha", category = 0), rec(2, "Alpine", category = 1)))

        // Act
        val page = list(places, "alp", "services")

        // Assert
        assertEquals(listOf(TinyPlaces.uuid(2)), page.items.map { it.id })
        assertEquals(1, page.total)
    }

    @Test
    fun pagingBoundaries() {
        // Arrange
        val places = mapOf(3 to (1..120).map { rec(it, "Place ${it.toString().padStart(3, '0')}") })

        // Act
        val first = list(places)
        val second = list(places, offset = 50)
        val last = list(places, offset = 100)
        val past = list(places, offset = 500)
        val exact = list(mapOf(3 to (1..50).map { rec(it) }))

        // Assert
        assertEquals(50, first.items.size)
        assertTrue(first.hasMore)
        assertEquals(120, first.total)
        assertEquals(TinyPlaces.uuid(51), second.items.first().id)
        assertEquals(20, last.items.size)
        assertFalse(last.hasMore)
        assertTrue(past.items.isEmpty())
        assertFalse(past.hasMore)
        assertEquals(120, past.total)
        assertFalse(exact.hasMore)
    }

    @Test
    fun emptyAreaYieldsAnEmptyPage() {
        // Arrange / Act
        val page = list(emptyMap())

        // Assert
        assertEquals(BusinessPage(emptyList(), 0, false, emptyList()), page)
    }

    @Test
    fun groupChipsAreOrderedByCountWithUnknownLast() {
        // Arrange
        val places = mapOf(
            3 to listOf(rec(1, category = 1), rec(2, category = 0), rec(3, category = 2), rec(4, category = 99), rec(5, category = 99), rec(6, category = 99)),
            5 to listOf(rec(1, category = 1)),
        )

        // Act
        val chips = NearbyBusinesses.groupsWithCounts(places, categories)

        // Assert
        assertEquals(listOf("food_and_drink", "services", "unknown"), chips.map { it.key })
        assertEquals(listOf(2, 1, 3), chips.map { it.count })
        assertEquals("Food and drink", chips.first().name)
        assertEquals("Unknown", chips.last().name)
    }

    @Test
    fun groupChipsCanBeLimitedToTheArea() {
        // Arrange
        val places = mapOf(3 to listOf(rec(1, category = 0)), 12 to listOf(rec(2, category = 1)))

        // Act
        val chips = NearbyBusinesses.groupsWithCounts(places, categories, plan)

        // Assert
        assertEquals(listOf("food_and_drink"), chips.map { it.key })
    }

    @Test
    fun decodedTinyPlacesFlowThroughTheList() {
        // Arrange
        val built = TinyPlaces.build()
        val decoded = mapOf(3 to built.ranges[0].flatMap { PlaceUnit.decodeRange(PlaceUnit.decompress(built.chunks[it.chunk], TinyPlaces.identity), it) })

        // Act
        val page = list(decoded)

        // Assert
        assertEquals(4, page.total)
        assertEquals(1, page.items.count { it.nearby })
        assertTrue(page.items.any { it.name == "Café Nero" })
    }
}
