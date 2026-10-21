package uk.veritaslabs.vesta.ui.screens

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.HomeLocationRepository
import uk.veritaslabs.vesta.location.LocalityPackSource
import uk.veritaslabs.vesta.location.LocalityPackState
import uk.veritaslabs.vesta.location.LocalityRoot
import uk.veritaslabs.vesta.location.NearbyRelation
import uk.veritaslabs.vesta.location.TinyPack
import uk.veritaslabs.vesta.location.TinyPack.id

@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceAreaViewModelTest {

    private val newport = id(3)
    private val shide = id(13)
    private val carisbrooke = id(7)
    private val cowes = id(5)
    private val release = "2026-09-23.1"
    private val absent = "ffffffff-0000-0000-0000-000000000000"

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        source: AreaFakeSource = AreaFakeSource(),
        repository: AreaFakeRepository = AreaFakeRepository(),
        region: String? = "GB",
    ): MarketplaceAreaViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return MarketplaceAreaViewModel("home", source, repository) { region }
    }

    private val MarketplaceAreaViewModel.step get() = uiState.value.step

    /** Takes a view model on a cached source to the nearby step for Newport. */
    private fun MarketplaceAreaViewModel.toNearby() {
        start()
        selectCountry("GB")
        continueFromCountry()
        chooseHome(newport)
    }

    @Test
    fun notCachedShowsTheDisclosureAndLoadsNothingUntilTheUserAgrees() = runTest {
        // Arrange
        val source = AreaFakeSource(cached = false)
        val viewModel = viewModel(source)

        // Act
        viewModel.start()

        // Assert
        assertEquals(AreaStep.Disclosure, viewModel.step)
        assertEquals(0, source.loadCalls)
    }

    @Test
    fun agreeingLoadsOnceAndMovesToTheCountryStep() = runTest {
        // Arrange
        val source = AreaFakeSource(cached = false)
        val viewModel = viewModel(source)
        viewModel.start()

        // Act
        viewModel.agreeToDownload()

        // Assert
        assertEquals(1, source.loadCalls)
        assertEquals(AreaStep.Country, viewModel.step)
    }

    @Test
    fun anUnreachableDownloadIsUnavailableWithItsReason() = runTest {
        // Arrange
        val source = AreaFakeSource(loadResult = LocalityPackState.Unreachable("offline"))
        val viewModel = viewModel(source)
        viewModel.start()

        // Act
        viewModel.agreeToDownload()

        // Assert
        assertEquals(AreaStep.Unavailable(AreaUnavailable.Unreachable("offline")), viewModel.step)
    }

    @Test
    fun decliningNeverLoads() = runTest {
        // Arrange
        val source = AreaFakeSource(cached = false)
        val viewModel = viewModel(source)
        viewModel.start()

        // Act
        viewModel.declineDownload()

        // Assert
        assertEquals(AreaStep.Declined, viewModel.step)
        assertEquals(0, source.loadCalls)
    }

    @Test
    fun anUnconfiguredPreflightIsUnavailableWithNoLoad() = runTest {
        // Arrange
        val source = AreaFakeSource(preflightResult = LocalityPackState.Unconfigured)
        val viewModel = viewModel(source)

        // Act
        viewModel.start()

        // Assert
        assertEquals(AreaStep.Unavailable(AreaUnavailable.Unconfigured), viewModel.step)
        assertEquals(0, source.loadCalls)
    }

    @Test
    fun aNotAvailablePreflightIsUnavailableWithNoLoad() = runTest {
        // Arrange
        val source = AreaFakeSource(preflightResult = LocalityPackState.NotAvailableOnDevice)
        val viewModel = viewModel(source)

        // Act
        viewModel.start()

        // Assert
        assertEquals(AreaStep.Unavailable(AreaUnavailable.NotAvailableOnDevice), viewModel.step)
        assertEquals(0, source.loadCalls)
    }

    @Test
    fun cachedGoesStraightToCountryWithoutTheDisclosure() = runTest {
        // Arrange
        val source = AreaFakeSource(cached = true)
        val viewModel = viewModel(source)

        // Act
        viewModel.start()

        // Assert
        assertEquals(AreaStep.Country, viewModel.step)
        assertEquals(1, source.loadCalls)
    }

    @Test
    fun theRootsCountryIsOfferedAndPreselectedWhenTheRegionMatches() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true), region = "gb")

        // Act
        viewModel.start()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(listOf(AreaCountry("GB", "United Kingdom")), state.countries)
        assertEquals("GB", state.selectedCountry)
    }

    @Test
    fun aRegionTheRootDoesNotOfferPreselectsNothingAndContinueDoesNothing() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true), region = "US")
        viewModel.start()

        // Act
        viewModel.continueFromCountry()

        // Assert
        assertNull(viewModel.uiState.value.selectedCountry)
        assertEquals(AreaStep.Country, viewModel.step)
    }

    @Test
    fun aNullRegionPreselectsNothingButTheOfferedCountryCanBeSelected() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true), region = null)
        viewModel.start()

        // Act
        viewModel.selectCountry("FR")
        val afterUnoffered = viewModel.uiState.value.selectedCountry
        viewModel.selectCountry("GB")
        viewModel.continueFromCountry()

        // Assert
        assertNull(afterUnoffered)
        assertEquals("GB", viewModel.uiState.value.selectedCountry)
        assertEquals(AreaStep.HomeLocality, viewModel.step)
    }

    @Test
    fun anUnknownCountryCodeFallsBackToTheCode() {
        // Arrange
        val known = "GB"
        val unknown = "ZZ"

        // Act
        val knownName = countryName(known)
        val unknownName = countryName(unknown)

        // Assert
        assertEquals("United Kingdom", knownName)
        assertEquals("ZZ", unknownName)
    }

    @Test
    fun searchResultsCarryTheirChainText() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true))
        viewModel.start()
        viewModel.selectCountry("GB")
        viewModel.continueFromCountry()

        // Act
        viewModel.setQuery("newport")

        // Assert
        val state = viewModel.uiState.value
        assertEquals(2, state.results.size)
        assertEquals(AreaPlace(newport, "Newport", "Newport, Isle of Wight, England"), state.results.first { it.id == newport })
        assertFalse(state.noMatch)
    }

    @Test
    fun aBlankQueryHasNoResultsAndIsNotANoMatch() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true))
        viewModel.start()

        // Act
        viewModel.setQuery("   ")

        // Assert
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertFalse(viewModel.uiState.value.noMatch)
    }

    @Test
    fun aQueryWithNoResultsIsANoMatch() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true))
        viewModel.start()

        // Act
        viewModel.setQuery("zzzzqqq")

        // Assert
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertTrue(viewModel.uiState.value.noMatch)
    }

    @Test
    fun choosingAHomeShowsNearbyWithTheHomeCountedAndNeighboursUnticked() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true))

        // Act
        viewModel.toNearby()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(AreaStep.Nearby, state.step)
        assertEquals(newport, state.home?.id)
        assertEquals(listOf("Shide", "Carisbrooke", "Cowes", "Alpha Town", "Beta Town"), state.nearby.map { it.name })
        assertTrue(state.nearby.none { it.ticked })
        assertEquals(1, state.tickedCount)
        assertEquals(NearbyRelation.Contains, state.nearby.first().relation)
    }

    @Test
    fun theHomeLocalityCannotBeToggled() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true))
        viewModel.toNearby()

        // Act
        viewModel.toggle(newport)

        // Assert
        assertEquals(1, viewModel.uiState.value.tickedCount)
        assertTrue(viewModel.uiState.value.nearby.none { it.ticked })
    }

    @Test
    fun togglingTicksAndUnticksANeighbourAndCounts() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true))
        viewModel.toNearby()

        // Act
        viewModel.toggle(cowes)
        val ticked = viewModel.uiState.value
        viewModel.toggle(cowes)

        // Assert
        assertEquals(2, ticked.tickedCount)
        assertTrue(ticked.nearby.first { it.id == cowes }.ticked)
        assertEquals(1, viewModel.uiState.value.tickedCount)
    }

    @Test
    fun anIdThatIsNotOfferedCannotBeTicked() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true))
        viewModel.toNearby()

        // Act
        viewModel.toggle(id(8))

        // Assert
        assertEquals(1, viewModel.uiState.value.tickedCount)
    }

    @Test
    fun onlyTheNearestTenAreShownUntilShowMore() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true, loadResult = areaReadyPack(big = true)))

        // Act
        viewModel.toNearby()
        val first = viewModel.uiState.value
        viewModel.showMore()

        // Assert
        assertEquals(10, first.nearby.size)
        assertTrue(first.hasMoreNearby)
        assertEquals(15, viewModel.uiState.value.nearby.size)
        assertFalse(viewModel.uiState.value.hasMoreNearby)
    }

    @Test
    fun nearbyFilterSearchesEveryCandidateEvenBeyondTheShownOnes() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true, loadResult = areaReadyPack(big = true)))
        viewModel.toNearby()
        val first = viewModel.uiState.value

        // Act
        viewModel.setNearbyQuery("far away")
        val filtered = viewModel.uiState.value

        // Assert
        assertTrue(first.nearby.none { it.name == "Far Away" })
        assertEquals(listOf("Far Away"), filtered.nearby.map { it.name })
        assertFalse(filtered.hasMoreNearby)
    }

    @Test
    fun confirmSavesTheHomeThenTheAreaAndShowsSaved() = runTest {
        // Arrange
        val repository = AreaFakeRepository()
        val viewModel = viewModel(AreaFakeSource(cached = true), repository)
        viewModel.toNearby()
        viewModel.toggle(cowes)
        viewModel.toggle(shide)

        // Act
        viewModel.confirm()

        // Assert
        assertEquals(listOf("setHome GB $newport $release", "setArea ${listOf(cowes, shide).sorted()}"), repository.calls)
        val state = viewModel.uiState.value
        assertEquals(AreaStep.Saved, state.step)
        assertEquals(AreaSummary("Newport", "Newport, Isle of Wight, England", 2), state.saved)
    }

    @Test
    fun startWithAStoredLocationAndACachedPackShowsSavedWithNames() = runTest {
        // Arrange
        val stored = HomeLocation("GB", newport, release, area = setOf(cowes, newport))
        val viewModel = viewModel(AreaFakeSource(cached = true), AreaFakeRepository(stored))

        // Act
        viewModel.start()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(AreaStep.Saved, state.step)
        assertEquals("Newport", state.saved?.homeName)
        assertEquals(1, state.saved?.nearbyCount)
    }

    @Test
    fun startWithAStoredLocationAndNoCachedPackShowsSavedWithoutLoading() = runTest {
        // Arrange
        val stored = HomeLocation("GB", newport, release, area = setOf(cowes, shide))
        val source = AreaFakeSource(cached = false)
        val viewModel = viewModel(source, AreaFakeRepository(stored))

        // Act
        viewModel.start()

        // Assert
        assertEquals(AreaStep.Saved, viewModel.step)
        assertEquals(AreaSummary(null, null, 2), viewModel.uiState.value.saved)
        assertEquals(0, source.loadCalls)
    }

    @Test
    fun aStoredHomeAbsentFromThePackNeedsRechoosing() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true), AreaFakeRepository(HomeLocation("GB", absent, "old")))

        // Act
        viewModel.start()

        // Assert
        assertEquals(AreaStep.NeedsRechoose, viewModel.step)
    }

    @Test
    fun rechoosingStartsAgainAtTheCountryStep() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true), AreaFakeRepository(HomeLocation("GB", absent, "old")))
        viewModel.start()

        // Act
        viewModel.rechoose()

        // Assert
        assertEquals(AreaStep.Country, viewModel.step)
    }

    @Test
    fun editKeepsTheStoredChoicePreticked() = runTest {
        // Arrange
        val stored = HomeLocation("GB", newport, release, area = setOf(cowes))
        val viewModel = viewModel(AreaFakeSource(cached = true), AreaFakeRepository(stored))
        viewModel.start()

        // Act
        viewModel.edit()
        val editing = viewModel.uiState.value
        viewModel.chooseHome(newport)

        // Assert
        assertEquals(AreaStep.HomeLocality, editing.step)
        assertEquals(newport, editing.home?.id)
        assertEquals(AreaStep.Nearby, viewModel.step)
        assertEquals(listOf(cowes), viewModel.uiState.value.nearby.filter { it.ticked }.map { it.id })
        assertEquals(2, viewModel.uiState.value.tickedCount)
    }

    @Test
    fun choosingADifferentHomeWhileEditingStartsWithNoTicks() = runTest {
        // Arrange
        val stored = HomeLocation("GB", newport, release, area = setOf(cowes))
        val viewModel = viewModel(AreaFakeSource(cached = true), AreaFakeRepository(stored))
        viewModel.start()
        viewModel.edit()

        // Act
        viewModel.chooseHome(carisbrooke)

        // Assert
        assertTrue(viewModel.uiState.value.nearby.none { it.ticked })
    }

    @Test
    fun editingWithoutACachedPackAsksFirstAndLoadsOnlyAfterAgreeing() = runTest {
        // Arrange
        val stored = HomeLocation("GB", newport, release, area = setOf(cowes))
        val source = AreaFakeSource(cached = false)
        val viewModel = viewModel(source, AreaFakeRepository(stored))
        viewModel.start()

        // Act
        viewModel.edit()
        val asked = viewModel.step
        val loadsBeforeAgreeing = source.loadCalls
        viewModel.agreeToDownload()

        // Assert
        assertEquals(AreaStep.Disclosure, asked)
        assertEquals(0, loadsBeforeAgreeing)
        assertEquals(AreaStep.HomeLocality, viewModel.step)
        assertEquals(newport, viewModel.uiState.value.home?.id)
    }

    @Test
    fun editingWithoutACachedPackThenDecliningLoadsNothing() = runTest {
        // Arrange
        val source = AreaFakeSource(cached = false)
        val viewModel = viewModel(source, AreaFakeRepository(HomeLocation("GB", newport, release)))
        viewModel.start()
        viewModel.edit()

        // Act
        viewModel.declineDownload()

        // Assert
        assertEquals(AreaStep.Declined, viewModel.step)
        assertEquals(0, source.loadCalls)
    }

    @Test
    fun backFromNearbyKeepsWhatWasTypedAndTicked() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true))
        viewModel.start()
        viewModel.selectCountry("GB")
        viewModel.continueFromCountry()
        viewModel.setQuery("newport")
        viewModel.chooseHome(newport)
        viewModel.toggle(cowes)

        // Act
        val handled = viewModel.back()
        val afterBack = viewModel.uiState.value
        viewModel.chooseHome(newport)

        // Assert
        assertTrue(handled)
        assertEquals(AreaStep.HomeLocality, afterBack.step)
        assertEquals("newport", afterBack.query)
        assertEquals(2, afterBack.results.size)
        assertEquals(2, viewModel.uiState.value.tickedCount)
        assertTrue(viewModel.uiState.value.nearby.first { it.id == cowes }.ticked)
    }

    @Test
    fun backFromTheHomeLocalityStepWithNothingStoredGoesToCountryAndFromThereLeaves() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true))
        viewModel.start()
        viewModel.selectCountry("GB")
        viewModel.continueFromCountry()

        // Act
        val first = viewModel.back()
        val stepAfterFirst = viewModel.step
        val second = viewModel.back()

        // Assert
        assertTrue(first)
        assertEquals(AreaStep.Country, stepAfterFirst)
        assertFalse(second)
    }

    @Test
    fun backFromEditingReturnsToSaved() = runTest {
        // Arrange
        val viewModel = viewModel(AreaFakeSource(cached = true), AreaFakeRepository(HomeLocation("GB", newport, release)))
        viewModel.start()
        viewModel.edit()

        // Act
        val handled = viewModel.back()

        // Assert
        assertTrue(handled)
        assertEquals(AreaStep.Saved, viewModel.step)
    }

    @Test
    fun forgetClearsTheRepositoryAndStartsAgainWithStateReset() = runTest {
        // Arrange
        val repository = AreaFakeRepository(HomeLocation("GB", newport, release, area = setOf(cowes)))
        val viewModel = viewModel(AreaFakeSource(cached = true), repository)
        viewModel.start()
        viewModel.edit()
        viewModel.setQuery("newport")

        // Act
        viewModel.forget()

        // Assert
        assertEquals(listOf("clear"), repository.calls)
        assertNull(repository.location)
        val state = viewModel.uiState.value
        assertEquals(AreaStep.Country, state.step)
        assertEquals("", state.query)
        assertNull(state.home)
        assertNull(state.saved)
    }

    @Test
    fun forgetWithTheDataNotCachedReturnsToTheDisclosureWithoutLoading() = runTest {
        // Arrange
        val source = AreaFakeSource(cached = false)
        val viewModel = viewModel(source, AreaFakeRepository(HomeLocation("GB", newport, release)))
        viewModel.start()

        // Act
        viewModel.forget()

        // Assert
        assertEquals(AreaStep.Disclosure, viewModel.step)
        assertEquals(0, source.loadCalls)
    }

    @Test
    fun aFailedLoadOnEditIsUnavailable() = runTest {
        // Arrange
        val source = AreaFakeSource(cached = true, loadResult = LocalityPackState.Unreachable("gone"))
        val viewModel = viewModel(source, AreaFakeRepository(HomeLocation("GB", newport, release)))
        viewModel.start()

        // Act
        viewModel.edit()

        // Assert
        assertEquals(AreaStep.Unavailable(AreaUnavailable.Unreachable("gone")), viewModel.step)
    }
}
