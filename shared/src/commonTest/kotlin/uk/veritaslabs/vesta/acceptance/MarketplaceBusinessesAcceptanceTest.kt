package uk.veritaslabs.vesta.acceptance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.PlaceRange
import uk.veritaslabs.vesta.location.TinyPack.id
import uk.veritaslabs.vesta.location.TinyPlaces
import uk.veritaslabs.vesta.ui.flows.MarketplaceAreaFlow
import uk.veritaslabs.vesta.ui.screens.AreaBusinessesState
import uk.veritaslabs.vesta.ui.screens.AreaFakePlaces
import uk.veritaslabs.vesta.ui.screens.AreaFakeRepository
import uk.veritaslabs.vesta.ui.screens.AreaFakeSource
import uk.veritaslabs.vesta.ui.screens.MarketplaceAreaTestTags
import uk.veritaslabs.vesta.ui.screens.MarketplaceAreaViewModel
import uk.veritaslabs.vesta.ui.screens.areaPlacesRanges
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Marketplace businesses (ADR-039 places). The criteria are the numbered list in docs/testing/acceptance-tests.md under
 * "Marketplace businesses". These tests drive the real screens and view model over the fake sources; the network and cache
 * criteria that need the real source are backed by AutonomiPlacesPackSourceTest, as that document says.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MarketplaceBusinessesAcceptanceTest {

    private val newport = id(3)
    private val cowes = id(5)
    private val carisbrooke = id(7)
    private val release = "2026-09-23.1"

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun smallArea() = AreaFakeRepository(HomeLocation("GB", newport, release, area = setOf(cowes, carisbrooke)))

    private fun ComposeUiTest.show(
        places: AreaFakePlaces = AreaFakePlaces(),
        source: AreaFakeSource = AreaFakeSource(cached = true),
        repository: AreaFakeRepository = smallArea(),
    ): MarketplaceAreaViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = MarketplaceAreaViewModel("home", source, repository, places) { "GB" }
        setContent { VestaTheme { MarketplaceAreaFlow(viewModel, onBack = {}) } }
        waitForIdle()
        return viewModel
    }

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    /** Home with 250 places inside and 74,050 just outside, in two chunks of 0.25 MiB and 2.25 MiB. */
    private fun bigPlaces() = AreaFakePlaces(
        chunkSizes = listOf(262_144, 2_359_296),
        ranges = areaPlacesRanges().mapIndexed { row, ranges ->
            if (row == 3) listOf(PlaceRange(0, 4, 10, 250, false), PlaceRange(1, 4, 10, 74_050, true)) else ranges
        },
    )

    private fun bigArea() = AreaFakeRepository(HomeLocation("GB", newport, release))

    private fun ComposeUiTest.textOf(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    @Test
    fun sizeAndCountsAreShownBeforeAnyDataChunkIsFetched() = runComposeUiTest {
        // Arrange — Given a saved area with 250 businesses inside and 74,050 just outside
        val places = bigPlaces()
        show(places, repository = bigArea())

        // Act — When I open See businesses
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert — Then inside and just-outside counts and sizes are shown separately and nothing has been fetched
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesTotal).assertTextEquals("74,300 businesses in your area")
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesInside).assertTextContains("250", substring = true)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesNearby).assertTextContains("74,050", substring = true)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesBoth).assertTextContains("2.5 MB", substring = true)
        assertEquals(0, places.fetchCalls)
    }

    @Test
    fun decliningTheDownloadFetchesNothing() = runComposeUiTest {
        // Arrange — Given the summary of a big area with the just-outside places included
        val places = bigPlaces()
        show(places, repository = bigArea())
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        tap(MarketplaceAreaTestTags.BusinessesToggleNearby)
        tap(MarketplaceAreaTestTags.BusinessesDownload)
        onNodeWithText("This will download about 2.5 MB. Download?").assertIsDisplayed()

        // Act — When I choose Not now
        tap(MarketplaceAreaTestTags.BusinessesConfirmNotNow)

        // Assert — Then nothing was fetched and I am back on the summary
        assertEquals(0, places.fetchCalls)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesSummary).assertIsDisplayed()
    }

    @Test
    fun aSmallAreaFetchesWithoutAsking() = runComposeUiTest {
        // Arrange — Given an area whose data is under 1 MiB
        val places = AreaFakePlaces()
        show(places)

        // Act — When I open See businesses
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert — Then the list appears with no confirmation, after one fetch
        assertEquals(1, places.fetchCalls)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesList).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesConfirmDialog).assertDoesNotExist()
    }

    @Test
    fun theJustOutsideToggleChangesTheSizeThatIsConfirmedAndFetched() = runComposeUiTest {
        // Arrange — Given the big-area summary with just-outside places not included (inside only is under 1 MiB)
        val places = bigPlaces()
        show(places, repository = bigArea())
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Act — When I include them and press Download
        tap(MarketplaceAreaTestTags.BusinessesToggleNearby)
        tap(MarketplaceAreaTestTags.BusinessesDownload)

        // Assert — Then I am asked about the larger 2.5 MB, and only on confirming is it fetched with them included
        onNodeWithText("This will download about 2.5 MB. Download?").assertIsDisplayed()
        assertEquals(0, places.fetchCalls)
        tap(MarketplaceAreaTestTags.BusinessesConfirmDownload)
        assertEquals(1, places.fetchCalls)
        assertEquals(true, places.lastIncludeNearby)
    }

    @Test
    fun theListIsSortedNearestFirstAndNearbyRowsAreNeverLabelledIn() = runComposeUiTest {
        // Arrange — Given a small area with both inside and just-outside businesses
        val viewModel = show()

        // Act — When the list appears
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert — Then rows run nearest first on screen, and no nearby row's text says it is "in" the area
        val items = viewModel.uiState.value.businesses.items
        assertTrue(items.any { it.nearby } && items.any { !it.nearby })
        assertEquals(items.map { it.distanceMetres }.sorted(), items.map { it.distanceMetres })
        val shown = items.filter { onAllNodesWithTagCount(MarketplaceAreaTestTags.businessesRow(it.id)) > 0 }
        val tops = shown.map { onNodeWithTag(MarketplaceAreaTestTags.businessesRow(it.id)).fetchSemanticsNode().positionInRoot.y }
        assertTrue(shown.size >= 2)
        assertEquals(tops.sorted(), tops)
        val nearbyRow = items.first { it.nearby }
        val text = textOf(MarketplaceAreaTestTags.businessesRow(nearbyRow.id))
        assertTrue(nearbyRow.localityName.isNotEmpty() && text.contains(nearbyRow.localityName), text)
        assertTrue(!Regex("(^|\\s)in(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(text), text)
    }

    private fun ComposeUiTest.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun searchAndGroupFilterNarrowTheList() = runComposeUiTest {
        // Arrange — Given the list is showing
        show()
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        val pub = MarketplaceAreaTestTags.businessesRow(TinyPlaces.uuid(8))

        // Act — When I search "nearby" and then choose the Services group
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesSearchField).performTextInput("nearby")
        waitForIdle()
        onNodeWithTag(pub).assertIsDisplayed()
        tap(MarketplaceAreaTestTags.businessesGroup("services"))

        // Assert — Then the pub is gone with a no-match note, and All brings it back
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesNoMatch).assertIsDisplayed()
        tap(MarketplaceAreaTestTags.BusinessesGroupAll)
        onNodeWithTag(pub).assertIsDisplayed()
    }

    @Test
    fun cancellingKeepsWhatWasCachedAndReturnsToTheSummary() = runComposeUiTest {
        // Arrange — Given a fetch in progress, held after its first chunk
        val places = AreaFakePlaces().apply { gate = CompletableDeferred() }
        show(places)
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesProgress).assertTextContains("1 of 2", substring = true)

        // Act — When I press Cancel
        tap(MarketplaceAreaTestTags.BusinessesCancel)

        // Assert — Then the fetch was cancelled and I am on the summary (the cache keeping its chunks is proved by
        // AutonomiPlacesPackSourceTest.aFailingChunkGivesAPartialResultAndTheOtherChunkIsCached, not by this fake)
        assertTrue(places.cancelled)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesSummary).assertIsDisplayed()
    }

    @Test
    fun aPartialFailureShowsTheBannerWithTryAgain() = runComposeUiTest {
        // Arrange — Given one of the two chunks will fail
        val places = AreaFakePlaces().apply { failedChunks = 1 }
        show(places)

        // Act — When the list loads
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert — Then the list shows with the banner, and Try again fetches once more
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesList).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesPartial).assertIsDisplayed()
        places.failedChunks = 0
        tap(MarketplaceAreaTestTags.BusinessesPartialRetry)
        assertEquals(2, places.fetchCalls)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesPartial).assertDoesNotExist()
    }

    @Test
    fun forgettingTheAreaLeavesTheListAndDropsTheResults() = runComposeUiTest {
        // Arrange — Given the list is showing, and I go back to the saved card
        val repository = smallArea()
        val viewModel = show(repository = repository)
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesList).assertIsDisplayed()
        tap(MarketplaceAreaTestTags.BackButton)
        onNodeWithTag(MarketplaceAreaTestTags.SavedSummary).assertIsDisplayed()

        // Act — When I forget the area and confirm
        tap(MarketplaceAreaTestTags.SavedForget)
        tap("confirm_delete_confirm")

        // Assert — Then no list shows, the results are dropped and the location is gone
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesList).assertDoesNotExist()
        assertEquals(AreaBusinessesState(), viewModel.uiState.value.businesses)
        assertNull(repository.location)
    }

    @Test
    fun noPlacesRequestIsMadeBeforeTheDisclosureIsAccepted() = runComposeUiTest {
        // Arrange — Given nothing is downloaded yet (neither names nor places)
        val source = AreaFakeSource(cached = false)
        val places = AreaFakePlaces(cached = false)
        show(places, source)

        // Act — When I open See businesses and then choose Not now on the disclosure
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        onNodeWithTag(MarketplaceAreaTestTags.Disclosure).assertIsDisplayed()
        tap(MarketplaceAreaTestTags.DisclosureNotNow)

        // Assert — Then neither source was loaded and no places were fetched
        assertEquals(0, source.loadCalls)
        assertEquals(0, places.loadCalls)
        assertEquals(0, places.fetchCalls)
    }
}
