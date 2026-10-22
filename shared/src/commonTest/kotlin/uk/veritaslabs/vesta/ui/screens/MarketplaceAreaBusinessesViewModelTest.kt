package uk.veritaslabs.vesta.ui.screens

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.PlaceRange
import uk.veritaslabs.vesta.location.PlaceRecord
import uk.veritaslabs.vesta.location.PlacesPackState
import uk.veritaslabs.vesta.location.TinyPlaces
import uk.veritaslabs.vesta.location.TinyPack.id

@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceAreaBusinessesViewModelTest {

    private val newport = id(3)
    private val cowes = id(5)
    private val carisbrooke = id(7)
    private val release = "2026-09-23.1"

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun location(vararg area: String) = HomeLocation("GB", newport, release, area = area.toSet())

    private fun TestScope.viewModel(
        places: AreaFakePlaces = AreaFakePlaces(),
        source: AreaFakeSource = AreaFakeSource(cached = true),
        repository: AreaFakeRepository = AreaFakeRepository(location(cowes)),
    ): MarketplaceAreaViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return MarketplaceAreaViewModel("home", source, repository, places) { "GB" }.also { it.start() }
    }

    private val MarketplaceAreaViewModel.step get() = uiState.value.step
    private val MarketplaceAreaViewModel.businesses get() = uiState.value.businesses

    /** Newport: 250 inside (chunk 0, 0.25 MB), 74,050 just outside (chunk 1, 2.25 MB): 2.5 MB together. */
    private fun bigPlaces() = AreaFakePlaces(
        chunkSizes = listOf(262_144, 2_359_296),
        ranges = areaPlacesRanges().mapIndexed { row, ranges ->
            if (row == 3) listOf(PlaceRange(0, 4, 10, 250, false), PlaceRange(1, 4, 10, 74_050, true)) else ranges
        },
    )

    private fun bigRepository() = AreaFakeRepository(location())

    @Test
    fun theSummaryReportsInsideAndNearbyCountsAndSizesWithoutFetching() = runTest {
        // Arrange
        val places = bigPlaces()

        // Act
        val viewModel = viewModel(places, repository = bigRepository())
        viewModel.seeBusinesses()

        // Assert
        assertEquals(AreaStep.BusinessesSummary, viewModel.step)
        assertEquals(250, viewModel.businesses.inside)
        assertEquals(74_050, viewModel.businesses.nearby)
        assertEquals(262_144L, viewModel.businesses.bytesInsideOnly)
        assertEquals(2_621_440L, viewModel.businesses.bytesWithNearby)
        assertEquals(0, places.fetchCalls)
    }

    @Test
    fun theToggleDefaultsOffWhenTheNearbyDownloadIsOverOneMiB() = runTest {
        // Arrange
        val viewModel = viewModel(bigPlaces(), repository = bigRepository())

        // Act
        viewModel.seeBusinesses()

        // Assert
        assertFalse(viewModel.businesses.includeNearby)
        assertEquals(262_144L, viewModel.businesses.chosenBytes)
        assertEquals(250, viewModel.businesses.chosenCount)
    }

    @Test
    fun theToggleUpdatesTheChosenSizeAndCount() = runTest {
        // Arrange
        val viewModel = viewModel(bigPlaces(), repository = bigRepository())
        viewModel.seeBusinesses()

        // Act
        viewModel.setIncludeNearby(true)

        // Assert
        assertEquals(2_621_440L, viewModel.businesses.chosenBytes)
        assertEquals(74_300, viewModel.businesses.chosenCount)
    }

    @Test
    fun aSmallAreaFetchesAtOnceWithNearbyIncluded() = runTest {
        // Arrange
        val places = AreaFakePlaces()
        val viewModel = viewModel(places)

        // Act
        viewModel.seeBusinesses()

        // Assert
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
        assertEquals(true, places.lastIncludeNearby)
        assertEquals(listOf(3, 5), places.lastRows)
        assertEquals(7, viewModel.businesses.loaded)
    }

    @Test
    fun overOneMiBAsksBeforeDownloadingAndDownloadsOnConfirm() = runTest {
        // Arrange
        val places = bigPlaces()
        val viewModel = viewModel(places, repository = bigRepository())
        viewModel.seeBusinesses()
        viewModel.setIncludeNearby(true)

        // Act
        viewModel.downloadBusinesses()
        val askedFirst = viewModel.businesses.confirming
        val fetchedBeforeConfirm = places.fetchCalls
        viewModel.confirmBusinessesDownload()

        // Assert
        assertTrue(askedFirst)
        assertEquals(0, fetchedBeforeConfirm)
        assertEquals(1, places.fetchCalls)
        assertEquals(true, places.lastIncludeNearby)
    }

    @Test
    fun underOneMiBDownloadsWithoutAsking() = runTest {
        // Arrange
        val places = bigPlaces()
        val viewModel = viewModel(places, repository = bigRepository())
        viewModel.seeBusinesses()

        // Act
        viewModel.downloadBusinesses()

        // Assert
        assertEquals(1, places.fetchCalls)
        assertEquals(false, places.lastIncludeNearby)
        assertFalse(viewModel.businesses.confirming)
    }

    @Test
    fun decliningTheSizeConfirmationFetchesNothing() = runTest {
        // Arrange
        val places = bigPlaces()
        val viewModel = viewModel(places, repository = bigRepository())
        viewModel.seeBusinesses()
        viewModel.setIncludeNearby(true)
        viewModel.downloadBusinesses()

        // Act
        viewModel.declineBusinessesDownload()

        // Assert
        assertEquals(0, places.fetchCalls)
        assertFalse(viewModel.businesses.confirming)
        assertEquals(AreaStep.BusinessesSummary, viewModel.step)
    }

    @Test
    fun progressIsReportedWhileLoadingThenTheListAppears() = runTest {
        // Arrange
        val places = AreaFakePlaces().apply { gate = CompletableDeferred() }
        val viewModel = viewModel(places)

        // Act
        viewModel.seeBusinesses()
        val during = viewModel.uiState.value
        places.gate?.complete(Unit)

        // Assert
        assertEquals(AreaStep.BusinessesLoading, during.step)
        assertEquals(1, during.businesses.chunksDone)
        assertEquals(2, during.businesses.chunksTotal)
        assertEquals(100L, during.businesses.bytesFetched)
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
    }

    @Test
    fun cancelStopsTheFetchAndReturnsToTheSummary() = runTest {
        // Arrange
        val places = AreaFakePlaces().apply { gate = CompletableDeferred() }
        val viewModel = viewModel(places)
        viewModel.seeBusinesses()

        // Act
        viewModel.cancelBusinessesFetch()

        // Assert
        assertTrue(places.cancelled)
        assertEquals(AreaStep.BusinessesSummary, viewModel.step)
    }

    @Test
    fun aPartialResultShowsTheListWithABanner() = runTest {
        // Arrange
        val places = AreaFakePlaces().apply { failedChunks = 1 }
        val viewModel = viewModel(places)

        // Act
        viewModel.seeBusinesses()

        // Assert
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
        assertTrue(viewModel.businesses.partial)
        assertEquals(7, viewModel.businesses.loaded)
    }

    @Test
    fun nothingLoadedWithFailuresIsUnreachableAndTryAgainRecovers() = runTest {
        // Arrange
        val places = AreaFakePlaces().apply {
            failAll = true
            failedChunks = 2
        }
        val viewModel = viewModel(places)
        viewModel.seeBusinesses()
        val failed = viewModel.step
        places.failAll = false
        places.failedChunks = 0

        // Act
        viewModel.retryBusinesses()

        // Assert
        assertEquals(AreaStep.BusinessesUnreachable, failed)
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
        assertFalse(viewModel.businesses.partial)
    }

    @Test
    fun aPlacesLoadFailureIsUnreachable() = runTest {
        // Arrange
        val places = AreaFakePlaces().apply { loadResult = PlacesPackState.Unreachable("offline") }
        val viewModel = viewModel(places)

        // Act
        viewModel.seeBusinesses()

        // Assert
        assertEquals(AreaStep.BusinessesUnreachable, viewModel.step)
    }

    @Test
    fun searchNarrowsTheListAndNearbyRowsAreFlagged() = runTest {
        // Arrange
        val viewModel = viewModel(repository = AreaFakeRepository(location(cowes, carisbrooke)))
        viewModel.seeBusinesses()
        viewModel.openAll()

        // Act
        viewModel.setBusinessQuery("pub")

        // Assert
        assertEquals(listOf("Nearby Pub"), viewModel.businesses.items.map { it.name })
        assertTrue(viewModel.businesses.items.single().nearby)
        assertEquals(1, viewModel.businesses.matching)
    }

    @Test
    fun theFetchLandsOnTheGroupsLargestFirstWithTheTotal() = runTest {
        // Arrange
        val viewModel = viewModel()

        // Act
        viewModel.seeBusinesses()

        // Assert
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
        assertNull(viewModel.businesses.group)
        assertEquals(listOf("food_and_drink" to 6, "services" to 1), viewModel.businesses.groups.map { it.key to it.count })
        assertEquals(7, viewModel.businesses.loaded)
    }

    @Test
    fun openingAGroupShowsItsCategoriesWithCounts() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()

        // Act
        viewModel.openGroup("food_and_drink")

        // Assert
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
        assertEquals("food_and_drink", viewModel.businesses.group)
        assertEquals("Food and drink", viewModel.businesses.groupName)
        assertEquals(6, viewModel.businesses.groupCount)
        assertEquals(listOf("Cafe" to 5, "Pub" to 1), viewModel.businesses.categories.map { it.name to it.count })
    }

    @Test
    fun openingACategoryListsOnlyThatCategory() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()
        viewModel.openGroup("food_and_drink")

        // Act
        viewModel.openCategory(2)

        // Assert
        assertEquals(AreaStep.BusinessesList, viewModel.step)
        assertEquals("Pub", viewModel.businesses.categoryName)
        assertEquals(listOf("Place 4"), viewModel.businesses.items.map { it.name })
        assertEquals(1, viewModel.businesses.matching)
    }

    @Test
    fun allInTheGroupListsTheWholeGroup() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()
        viewModel.openGroup("food_and_drink")

        // Act
        viewModel.openGroupList()

        // Assert
        assertEquals(AreaStep.BusinessesList, viewModel.step)
        assertNull(viewModel.businesses.category)
        assertEquals(6, viewModel.businesses.matching)
    }

    @Test
    fun allBusinessesListsEverythingAndTheGroupsComeBackOnBack() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()

        // Act
        viewModel.openAll()
        val listed = viewModel.businesses.matching
        viewModel.back()

        // Assert
        assertEquals(7, listed)
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
        assertNull(viewModel.businesses.group)
    }

    @Test
    fun theCategorySearchNarrowsTheCategoryNamesOnly() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()
        viewModel.openGroup("food_and_drink")

        // Act
        viewModel.setCategoryQuery("pu")
        val narrowed = viewModel.businesses.categories.map { it.name }
        viewModel.setCategoryQuery("")

        // Assert
        assertEquals(listOf("Pub"), narrowed)
        assertEquals(2, viewModel.businesses.categories.size)
        assertEquals(6, viewModel.businesses.groupCount)
    }

    @Test
    fun theTopLevelSearchOpensTheListAcrossAllBusinesses() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()
        viewModel.setBrowseQuery("caf")

        // Act
        viewModel.searchAll(viewModel.businesses.browseQuery)

        // Assert
        assertEquals(AreaStep.BusinessesList, viewModel.step)
        assertNull(viewModel.businesses.group)
        assertEquals("caf", viewModel.businesses.query)
        assertEquals(listOf("Café Nero"), viewModel.businesses.items.map { it.name })
    }

    @Test
    fun backStepsUpOneLevelAtATime() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()
        viewModel.openGroup("food_and_drink")
        viewModel.openCategory(0)

        // Act
        val steps = buildList {
            viewModel.back()
            add(viewModel.step to viewModel.businesses.group)
            viewModel.back()
            add(viewModel.step to viewModel.businesses.group)
            viewModel.back()
            add(viewModel.step to viewModel.businesses.group)
        }

        // Assert
        assertEquals(
            listOf(
                AreaStep.BusinessesCategories to "food_and_drink",
                AreaStep.BusinessesCategories to null,
                AreaStep.Saved to null,
            ),
            steps,
        )
    }

    @Test
    fun backFromAListOpenedFromTheGroupsReturnsToTheGroups() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()
        viewModel.openGroup("food_and_drink")
        viewModel.openGroupList()
        viewModel.back()
        viewModel.openAll()

        // Act
        viewModel.back()

        // Assert
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
        assertNull(viewModel.businesses.group)
    }

    @Test
    fun theUnknownGroupHasNoCategoriesAndOpensItsListDirectly() = runTest {
        // Arrange
        val mystery = PlaceRecord(TinyPlaces.uuid(200), "Mystery Shop", 50.7, -1.29, 1.0, 99, false)
        val cafe = PlaceRecord(TinyPlaces.uuid(201), "Cafe One", 50.7, -1.29, 1.0, 0, false)
        val places = AreaFakePlaces().apply { generated = mapOf(3 to listOf(mystery, cafe)) }
        val viewModel = viewModel(places)
        viewModel.seeBusinesses()

        // Act
        val order = viewModel.businesses.groups.map { it.key }
        viewModel.openGroup("unknown")
        val listed = viewModel.businesses.items.map { it.name }
        viewModel.back()

        // Assert
        assertEquals(listOf("food_and_drink", "unknown"), order)
        assertEquals(listOf("Mystery Shop"), listed)
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
        assertNull(viewModel.businesses.group)
    }

    @Test
    fun categoryCountsFollowTheJustOutsideToggle() = runTest {
        // Arrange
        val places = bigPlaces()
        val viewModel = viewModel(places, repository = bigRepository())
        viewModel.seeBusinesses()
        viewModel.downloadBusinesses()
        val without = viewModel.businesses.groups.first { it.key == "food_and_drink" }.count
        viewModel.back()
        viewModel.seeBusinesses()

        // Act
        viewModel.setIncludeNearby(true)
        viewModel.downloadBusinesses()
        viewModel.confirmBusinessesDownload()

        // Assert
        assertEquals(2, without)
        assertEquals(3, viewModel.businesses.groups.first { it.key == "food_and_drink" }.count)
    }

    @Test
    fun sectionsRunHomeLocalityFirstAndAToZWithinEach() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()

        // Act
        viewModel.openAll()

        // Assert
        val sections = viewModel.businesses.sections
        assertEquals(listOf(3, 5), sections.map { it.localityRow })
        assertTrue(sections.all { it.localityName.isNotEmpty() })
        sections.forEach { section -> assertEquals(section.items.map { it.name }.sortedBy { it.lowercase() }, section.items.map { it.name }) }
        assertEquals(viewModel.businesses.items, sections.flatMap { it.items })
    }

    @Test
    fun forgettingWhileInACategoryDropsTheBrowseState() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()
        viewModel.openGroup("food_and_drink")
        viewModel.openCategory(0)

        // Act
        viewModel.forget()

        // Assert
        assertEquals(AreaBusinessesState(), viewModel.businesses)
    }

    @Test
    fun theListPagesByFifty() = runTest {
        // Arrange
        val many = (1..120).map { PlaceRecord(TinyPlaces.uuid(100 + it), "Shop $it", 50.70 + it / 10_000.0, -1.29, 1.0, 0, false) }
        val places = AreaFakePlaces().apply { generated = mapOf(3 to many) }
        val viewModel = viewModel(places, repository = bigRepository())
        viewModel.seeBusinesses()
        viewModel.openAll()

        // Act
        val first = viewModel.businesses.items.size
        viewModel.showMoreBusinesses()
        val second = viewModel.businesses.items.size
        viewModel.showMoreBusinesses()

        // Assert
        assertEquals(50, first)
        assertEquals(100, second)
        assertEquals(120, viewModel.businesses.items.size)
        assertFalse(viewModel.businesses.hasMore)
    }

    @Test
    fun forgettingTheAreaLeavesTheStepAndDropsTheResults() = runTest {
        // Arrange
        val repository = AreaFakeRepository(location(cowes))
        val viewModel = viewModel(repository = repository)
        viewModel.seeBusinesses()

        // Act
        viewModel.forget()

        // Assert
        assertEquals(AreaStep.Country, viewModel.step)
        assertEquals(AreaBusinessesState(), viewModel.businesses)
        assertNull(repository.location)
    }

    @Test
    fun backFromTheGroupsReturnsToTheSavedCardAndDropsTheResults() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()

        // Act
        val handled = viewModel.back()

        // Assert
        assertTrue(handled)
        assertEquals(AreaStep.Saved, viewModel.step)
        assertEquals(AreaBusinessesState(), viewModel.businesses)
    }

    @Test
    fun changingTheAreaRecomputesTheCounts() = runTest {
        // Arrange
        val viewModel = viewModel()
        viewModel.seeBusinesses()
        val before = viewModel.businesses.nearby
        viewModel.back()
        viewModel.edit()
        viewModel.chooseHome(newport)
        viewModel.toggle(carisbrooke)
        viewModel.confirm()

        // Act
        viewModel.seeBusinesses()

        // Assert
        assertEquals(1, before)
        assertEquals(3, viewModel.businesses.nearby)
    }

    @Test
    fun theSavedCardReportsWhyBusinessesAreMissing() = runTest {
        // Arrange
        val cases = listOf(
            null to AreaBusinessesAvailability.Available,
            PlacesPackState.Unconfigured to AreaBusinessesAvailability.Unconfigured,
            PlacesPackState.NotAvailableOnDevice to AreaBusinessesAvailability.NotAvailableOnDevice,
            PlacesPackState.Unreachable("x") to AreaBusinessesAvailability.Unreachable,
        )

        // Act
        val results = cases.map { (preflight, _) ->
            val places = AreaFakePlaces(preflightResult = preflight)
            val viewModel = viewModel(places)
            viewModel.seeBusinesses()
            Triple(viewModel.uiState.value.saved?.businesses, viewModel.step, places.loadCalls)
        }

        // Assert
        assertEquals(cases.map { it.second }, results.map { it.first })
        assertEquals(listOf(AreaStep.BusinessesCategories), results.map { it.second }.take(1))
        assertTrue(results.drop(1).all { it.second == AreaStep.Saved && it.third == 0 })
    }

    @Test
    fun theDefaultPlacesSourceSaysNotAvailableOnThisDevice() = runTest {
        // Arrange
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = MarketplaceAreaViewModel("home", AreaFakeSource(cached = true), AreaFakeRepository(location(cowes))) { "GB" }

        // Act
        viewModel.start()

        // Assert
        assertEquals(AreaBusinessesAvailability.NotAvailableOnDevice, viewModel.uiState.value.saved?.businesses)
    }

    @Test
    fun nothingIsRequestedBeforeTheDisclosureIsAccepted() = runTest {
        // Arrange
        val source = AreaFakeSource(cached = false)
        val places = AreaFakePlaces(cached = false)
        val viewModel = viewModel(places, source)

        // Act
        viewModel.seeBusinesses()
        val asked = viewModel.step
        val requestsBefore = source.loadCalls + places.loadCalls + places.fetchCalls
        viewModel.agreeToDownload()

        // Assert
        assertEquals(AreaStep.Disclosure, asked)
        assertEquals(0, requestsBefore)
        assertEquals(AreaStep.BusinessesCategories, viewModel.step)
    }

    @Test
    fun decliningTheDisclosureRequestsNothing() = runTest {
        // Arrange
        val source = AreaFakeSource(cached = false)
        val places = AreaFakePlaces(cached = false)
        val viewModel = viewModel(places, source)
        viewModel.seeBusinesses()

        // Act
        viewModel.declineDownload()

        // Assert
        assertEquals(AreaStep.Declined, viewModel.step)
        assertEquals(0, source.loadCalls + places.loadCalls + places.fetchCalls)
    }

    @Test
    fun sizesAndNumbersAreFormattedForDisplay() {
        // Arrange / Act
        val quarter = megabytesText(262_144)
        val big = megabytesText(2_621_440)
        val tiny = megabytesText(1)
        val grouped = groupedNumber(74_050)

        // Assert
        assertEquals("0.25", quarter)
        assertEquals("2.5", big)
        assertEquals("0.01", tiny)
        assertEquals("74,050", grouped)
        assertEquals("12", groupedNumber(12))
    }
}
