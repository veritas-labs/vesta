package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.LocalityPackState
import uk.veritaslabs.vesta.location.TinyPack.id
import uk.veritaslabs.vesta.ui.flows.MarketplaceAreaFlow
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MarketplaceAreaFlowTest {

    private val newport = id(3)
    private val cowes = id(5)
    private val shide = id(13)
    private val release = "2026-09-23.1"

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Harness(val source: AreaFakeSource, val repository: AreaFakeRepository, val viewModel: MarketplaceAreaViewModel) {
        var backs = 0
    }

    private fun ComposeUiTest.show(
        source: AreaFakeSource = AreaFakeSource(),
        repository: AreaFakeRepository = AreaFakeRepository(),
        region: String? = "GB",
    ): Harness {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = MarketplaceAreaViewModel("home", source, repository) { region }
        val harness = Harness(source, repository, viewModel)
        setContent { VestaTheme { MarketplaceAreaFlow(viewModel, onBack = { harness.backs++ }) } }
        waitForIdle()
        return harness
    }

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.toNearby() {
        tap(MarketplaceAreaTestTags.CountryContinue)
        onNodeWithTag(MarketplaceAreaTestTags.HomeSearchField).performTextInput("newp")
        waitForIdle()
        tap(MarketplaceAreaTestTags.place(newport))
    }

    @Test
    fun theDisclosureComesFirstAndNotNowLeavesWithoutLoading() = runComposeUiTest {
        // Arrange
        val harness = show()
        onNodeWithTag(MarketplaceAreaTestTags.Disclosure).assertIsDisplayed()

        // Act
        tap(MarketplaceAreaTestTags.DisclosureNotNow)

        // Assert
        assertEquals(1, harness.backs)
        assertEquals(0, harness.source.loadCalls)
    }

    @Test
    fun continueShowsTheCountryListWithTheRegionPreselected() = runComposeUiTest {
        // Arrange
        show()

        // Act
        tap(MarketplaceAreaTestTags.DisclosureContinue)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.country("GB")).assertIsSelected()
        onNodeWithTag(MarketplaceAreaTestTags.CountryContinue).assertIsEnabled()
        onNodeWithTag(MarketplaceAreaTestTags.CountryMoreComing).assertIsDisplayed()
    }

    @Test
    fun continueIsDisabledUntilACountryIsSelected() = runComposeUiTest {
        // Arrange
        show(region = "FR")
        tap(MarketplaceAreaTestTags.DisclosureContinue)
        onNodeWithTag(MarketplaceAreaTestTags.CountryContinue).assertIsNotEnabled()

        // Act
        tap(MarketplaceAreaTestTags.country("GB"))

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.CountryContinue).assertIsEnabled()
    }

    @Test
    fun typingShowsResultsWithTheirChainsAndNothingBeforeThat() = runComposeUiTest {
        // Arrange
        show(AreaFakeSource(cached = true))
        tap(MarketplaceAreaTestTags.CountryContinue)
        onNodeWithTag(MarketplaceAreaTestTags.HomeHint).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.place(newport)).assertDoesNotExist()

        // Act
        onNodeWithTag(MarketplaceAreaTestTags.HomeSearchField).performTextInput("newp")
        waitForIdle()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.place(newport)).assertIsDisplayed()
        onNodeWithText("Newport, Isle of Wight, England").assertIsDisplayed()
    }

    @Test
    fun aQueryWithNoResultsSaysNoMatch() = runComposeUiTest {
        // Arrange
        show(AreaFakeSource(cached = true))
        tap(MarketplaceAreaTestTags.CountryContinue)

        // Act
        onNodeWithTag(MarketplaceAreaTestTags.HomeSearchField).performTextInput("zzzz")
        waitForIdle()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.HomeNoMatch).assertIsDisplayed()
    }

    @Test
    fun choosingAHomeShowsTheNearbyListWithTheHomeLocked() = runComposeUiTest {
        // Arrange
        show(AreaFakeSource(cached = true))

        // Act
        toNearby()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.NearbyHome).assertIsOn()
        onNodeWithTag(MarketplaceAreaTestTags.NearbyHome).assertIsNotEnabled()
        onNodeWithTag(MarketplaceAreaTestTags.nearby(cowes)).assertIsOff()
        onNodeWithTag(MarketplaceAreaTestTags.nearby(cowes)).assertTextContains("Next door", substring = true)
        onNodeWithTag(MarketplaceAreaTestTags.nearby(cowes)).assertTextContains("km", substring = true)
    }

    @Test
    fun tickingAndUntickingARowChangesTheCount() = runComposeUiTest {
        // Arrange
        show(AreaFakeSource(cached = true))
        toNearby()
        onNodeWithTag(MarketplaceAreaTestTags.NearbyTicked).assertTextContains("1 place ticked")

        // Act
        tap(MarketplaceAreaTestTags.nearby(cowes))

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.nearby(cowes)).assertIsOn()
        onNodeWithTag(MarketplaceAreaTestTags.NearbyTicked).assertTextContains("2 places ticked")
        tap(MarketplaceAreaTestTags.nearby(cowes))
        onNodeWithTag(MarketplaceAreaTestTags.nearby(cowes)).assertIsOff()
        onNodeWithTag(MarketplaceAreaTestTags.NearbyTicked).assertTextContains("1 place ticked")
    }

    @Test
    fun aNestedRowShowsItsLabel() = runComposeUiTest {
        // Arrange
        show(AreaFakeSource(cached = true))

        // Act
        toNearby()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.nearby(shide)).assertTextContains("Inside Newport", substring = true)
    }

    @Test
    fun showMoreAppearsWhenThereAreMoreRowsAndRevealsThem() = runComposeUiTest {
        // Arrange
        show(AreaFakeSource(cached = true, loadResult = areaReadyPack(big = true)))
        toNearby()
        onNodeWithTag(MarketplaceAreaTestTags.NearbyList).performScrollToNode(hasTestTag(MarketplaceAreaTestTags.NearbyShowMore))
        onNodeWithTag(MarketplaceAreaTestTags.NearbyShowMore).assertIsDisplayed()

        // Act
        tap(MarketplaceAreaTestTags.NearbyShowMore)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.NearbyList).performScrollToNode(hasText("Far Away"))
        onNodeWithText("Far Away").assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.NearbyShowMore).assertDoesNotExist()
    }

    @Test
    fun savingShowsTheSavedSummary() = runComposeUiTest {
        // Arrange
        val harness = show(AreaFakeSource(cached = true))
        toNearby()
        tap(MarketplaceAreaTestTags.nearby(cowes))

        // Act
        tap(MarketplaceAreaTestTags.NearbySave)

        // Assert
        onNodeWithText("Searching in Newport and 1 nearby place").assertIsDisplayed()
        assertEquals(newport, harness.repository.location?.homeLocality)
    }

    @Test
    fun forgetAsksFirstThenReturnsToTheStart() = runComposeUiTest {
        // Arrange
        val repository = AreaFakeRepository(HomeLocation("GB", newport, release, area = setOf(cowes)))
        show(AreaFakeSource(cached = false), repository)
        onNodeWithTag(MarketplaceAreaTestTags.SavedSummary).assertIsDisplayed()

        // Act
        tap(MarketplaceAreaTestTags.SavedForget)
        tap("confirm_delete_confirm")

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.Disclosure).assertIsDisplayed()
        assertEquals(null, repository.location)
    }

    @Test
    fun aSavedAreaWithoutThePackSaysYourAreaIsSet() = runComposeUiTest {
        // Arrange
        val repository = AreaFakeRepository(HomeLocation("GB", newport, release))

        // Act
        show(AreaFakeSource(cached = false), repository)

        // Assert
        onNodeWithText("Your area is set").assertIsDisplayed()
    }

    @Test
    fun backKeepsWhatWasTyped() = runComposeUiTest {
        // Arrange
        show(AreaFakeSource(cached = true))
        toNearby()

        // Act
        tap(MarketplaceAreaTestTags.BackButton)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.HomeSearchField).assertTextContains("newp")
        onNodeWithTag(MarketplaceAreaTestTags.place(newport)).assertIsDisplayed()
    }

    @Test
    fun backFromTheFirstStepLeaves() = runComposeUiTest {
        // Arrange
        val harness = show()

        // Act
        tap(MarketplaceAreaTestTags.BackButton)

        // Assert
        assertEquals(1, harness.backs)
    }

    @Test
    fun anUnconfiguredDeviceSaysSo() = runComposeUiTest {
        // Arrange / Act
        show(AreaFakeSource(preflightResult = LocalityPackState.Unconfigured))

        // Assert
        onNodeWithText("Location data is not set up on this device").assertIsDisplayed()
    }

    @Test
    fun aDeviceThatCannotFetchSaysSo() = runComposeUiTest {
        // Arrange / Act
        show(AreaFakeSource(preflightResult = LocalityPackState.NotAvailableOnDevice))

        // Assert
        onNodeWithText("Not available on this device yet").assertIsDisplayed()
    }

    @Test
    fun anUnreachableLoadOffersTryAgain() = runComposeUiTest {
        // Arrange
        val source = AreaFakeSource(loadResult = LocalityPackState.Unreachable("offline"))
        show(source)
        tap(MarketplaceAreaTestTags.DisclosureContinue)
        onNodeWithText("Couldn't load the location data").assertIsDisplayed()
        source.loadResult = areaReadyPack()

        // Act
        tap(MarketplaceAreaTestTags.TryAgain)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.Disclosure).assertIsDisplayed()
    }

    @Test
    fun aStalePlaceAsksToChooseAgain() = runComposeUiTest {
        // Arrange
        val absent = "ffffffff-0000-0000-0000-000000000000"
        val repository = AreaFakeRepository(HomeLocation("GB", absent, release))

        // Act
        show(AreaFakeSource(cached = true), repository)

        // Assert
        onNodeWithText("Your area needs choosing again").assertIsDisplayed()
        tap(MarketplaceAreaTestTags.RechooseChoose)
        onNodeWithTag(MarketplaceAreaTestTags.CountryContinue).assertIsDisplayed()
    }
}
