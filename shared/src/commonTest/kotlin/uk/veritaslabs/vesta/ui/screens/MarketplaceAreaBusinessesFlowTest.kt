package uk.veritaslabs.vesta.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsOn
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.PlaceRange
import uk.veritaslabs.vesta.location.PlacesPackState
import uk.veritaslabs.vesta.location.TinyPack.id
import uk.veritaslabs.vesta.location.TinyPlaces
import uk.veritaslabs.vesta.ui.flows.MarketplaceAreaFlow
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MarketplaceAreaBusinessesFlowTest {

    private val newport = id(3)
    private val cowes = id(5)
    private val carisbrooke = id(7)
    private val release = "2026-09-23.1"

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ComposeUiTest.show(
        places: AreaFakePlaces = AreaFakePlaces(),
        source: AreaFakeSource = AreaFakeSource(cached = true),
        repository: AreaFakeRepository = AreaFakeRepository(HomeLocation("GB", newport, release, area = setOf(cowes, carisbrooke))),
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

    private fun bigPlaces() = AreaFakePlaces(
        chunkSizes = listOf(262_144, 2_359_296),
        ranges = areaPlacesRanges().mapIndexed { row, ranges ->
            if (row == 3) listOf(PlaceRange(0, 4, 10, 250, false), PlaceRange(1, 4, 10, 74_050, true)) else ranges
        },
    )

    @Test
    fun theSavedCardOffersSeeBusinessesWhenConfigured() = runComposeUiTest {
        // Arrange / Act
        show()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.SavedSeeBusinesses).assertIsDisplayed()
    }

    @Test
    fun theSavedCardExplainsEachUnavailableState() = runComposeUiTest {
        // Arrange / Act
        show(AreaFakePlaces(preflightResult = PlacesPackState.Unconfigured))

        // Assert
        onNodeWithText("Business data is not set up on this device.").assertIsDisplayed()
    }

    @Test
    fun aSmallAreaLandsOnTheGroupsScreenWithCounts() = runComposeUiTest {
        // Arrange
        show()

        // Act
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesGroups).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesBrowseSearch).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesAll).assertTextContains("9", substring = true)
        onNodeWithTag(MarketplaceAreaTestTags.businessesGroup("food_and_drink")).assertTextContains("8", substring = true)
        onNodeWithTag(MarketplaceAreaTestTags.businessesGroup("services")).assertIsDisplayed()
    }

    @Test
    fun aGroupOpensItsCategoriesAndACategoryOpensTheListWithLocalityHeadersAndNoInLabel() = runComposeUiTest {
        // Arrange
        show()
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Act
        tap(MarketplaceAreaTestTags.businessesGroup("food_and_drink"))
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesCategories).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesAllInGroup).assertTextContains("All Food and drink", substring = true)
        tap(MarketplaceAreaTestTags.businessesCategory(2))

        // Assert
        val nearbyPub = MarketplaceAreaTestTags.businessesRow(TinyPlaces.uuid(8))
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesListTitle).assertTextEquals("Pub")
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesHeader).assertTextContains("2 businesses", substring = true)
        onNodeWithTag(nearbyPub).assertIsDisplayed()
        onNodeWithTag(nearbyPub).assertTextContains("Pub, ", substring = true)
        onNodeWithTag(MarketplaceAreaTestTags.businessesSection(7)).assertIsDisplayed()
    }

    @Test
    fun theCategorySearchNarrowsTheCategoriesAndBackStepsUp() = runComposeUiTest {
        // Arrange
        show()
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        tap(MarketplaceAreaTestTags.businessesGroup("food_and_drink"))

        // Act
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesCategorySearch).performTextInput("zzz")
        waitForIdle()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesCategoriesNoMatch).assertIsDisplayed()
        tap(MarketplaceAreaTestTags.BackButton)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesGroups).assertIsDisplayed()
    }

    @Test
    fun theTopLevelSearchRowOpensTheListAcrossAllBusinesses() = runComposeUiTest {
        // Arrange
        show()
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Act
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesBrowseSearch).performTextInput("nearby")
        waitForIdle()
        tap(MarketplaceAreaTestTags.BusinessesSearchAll)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesListTitle).assertTextEquals("All businesses")
        onNodeWithTag(MarketplaceAreaTestTags.businessesRow(TinyPlaces.uuid(8))).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesSearchField).performTextInput("zzz")
        waitForIdle()
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesNoMatch).assertIsDisplayed()
    }

    @Test
    fun aBigAreaShowsTheSummaryAndTheToggleChangesWhatIsConfirmed() = runComposeUiTest {
        // Arrange
        val places = bigPlaces()
        show(places, repository = AreaFakeRepository(HomeLocation("GB", newport, release)))
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert summary
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesTotal).assertTextEquals("74,300 businesses in your area")
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesInside).assertTextContains("250", substring = true)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesNearby).assertTextContains("74,050", substring = true)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesToggleNearby).assertIsOff()

        // Act
        tap(MarketplaceAreaTestTags.BusinessesToggleNearby)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesToggleNearby).assertIsOn()
        tap(MarketplaceAreaTestTags.BusinessesDownload)

        // Assert confirm
        onNodeWithText("This will download about 2.5 MB. Download?").assertIsDisplayed()
        tap(MarketplaceAreaTestTags.BusinessesConfirmNotNow)
        assertEquals(0, places.fetchCalls)
        tap(MarketplaceAreaTestTags.BusinessesDownload)
        tap(MarketplaceAreaTestTags.BusinessesConfirmDownload)
        assertEquals(1, places.fetchCalls)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesGroups).assertIsDisplayed()
    }

    @Test
    fun cancelWhileLoadingReturnsToTheSummary() = runComposeUiTest {
        // Arrange
        val places = AreaFakePlaces().apply { gate = CompletableDeferred() }
        show(places)

        // Act
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesProgress).assertTextContains("1 of 2", substring = true)
        tap(MarketplaceAreaTestTags.BusinessesCancel)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesSummary).assertIsDisplayed()
    }

    @Test
    fun aPartialResultShowsTheBannerAndTryAgain() = runComposeUiTest {
        // Arrange
        val places = AreaFakePlaces().apply { failedChunks = 1 }
        show(places)

        // Act
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesGroups).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesPartial).assertIsDisplayed()
        places.failedChunks = 0
        tap(MarketplaceAreaTestTags.BusinessesPartialRetry)
        assertEquals(2, places.fetchCalls)
    }

    @Test
    fun totalFailureShowsUnreachableWithTryAgain() = runComposeUiTest {
        // Arrange
        val places = AreaFakePlaces().apply {
            failAll = true
            failedChunks = 2
        }
        show(places)

        // Act
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesUnreachable).assertIsDisplayed()
        places.failAll = false
        places.failedChunks = 0
        tap(MarketplaceAreaTestTags.BusinessesTryAgain)
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesGroups).assertIsDisplayed()
    }

    @Test
    fun theDisclosureMentionsBusinessesAndNothingIsFetchedBeforeContinue() = runComposeUiTest {
        // Arrange
        val source = AreaFakeSource(cached = false)
        val places = AreaFakePlaces(cached = false)
        show(places, source)

        // Act
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.Disclosure).assertIsDisplayed()
        onNodeWithText("If you look at businesses", substring = true).assertIsDisplayed()
        assertEquals(0, source.loadCalls + places.loadCalls)
    }

    @Test
    fun theMissingBusinessNoteEndsTheCategoriesScreenAndTheList() = runComposeUiTest {
        // Arrange
        show()

        // Act
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert
        onNode(hasScrollAction()).performScrollToNode(hasTestTag(MarketplaceAreaTestTags.MissingNote))
        onNodeWithTag(MarketplaceAreaTestTags.MissingNote).assertIsDisplayed()
        tap(MarketplaceAreaTestTags.BusinessesAll)
        onNode(hasScrollAction()).performScrollToNode(hasTestTag(MarketplaceAreaTestTags.MissingNote))
        onNodeWithTag(MarketplaceAreaTestTags.MissingNote).assertIsDisplayed()
    }

    @Test
    fun theMissingBusinessNoteShowsWhenASearchFindsNothing() = runComposeUiTest {
        // Arrange
        show()
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        tap(MarketplaceAreaTestTags.BusinessesAll)

        // Act
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesSearchField).performTextInput("zzz")
        waitForIdle()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesNoMatch).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.MissingNote).assertIsDisplayed()
    }
}
