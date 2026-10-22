package uk.veritaslabs.vesta.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.location.BusinessGroupCount
import uk.veritaslabs.vesta.location.BusinessRow
import uk.veritaslabs.vesta.location.BusinessSection
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.NearbyBusinesses
import uk.veritaslabs.vesta.location.TinyPack.id
import uk.veritaslabs.vesta.ui.flows.MarketplaceAreaFlow
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/** The WP-M5 visual refresh: area card, Change, Directions and the tile order (ADR-039). */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MarketplaceAreaRedesignTest {

    private val release = "2026-09-23.1"

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ComposeUiTest.showFlow() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repository = AreaFakeRepository(HomeLocation("GB", id(3), release, area = setOf(id(5), id(7))))
        val viewModel = MarketplaceAreaViewModel("home", AreaFakeSource(cached = true), repository, AreaFakePlaces()) { "GB" }
        setContent { VestaTheme { MarketplaceAreaFlow(viewModel, onBack = {}) } }
        waitForIdle()
        onNodeWithTag(MarketplaceAreaTestTags.SavedSeeBusinesses).performClick()
        waitForIdle()
    }

    private fun row(id: String, latitude: Double?, longitude: Double?) =
        BusinessRow(id, "Shop $id", "Pub", "Food and drink", 0.0, 0, false, "Newport", latitude, longitude)

    @Test
    fun theAreaCardShowsTheTotalAndChange() = runComposeUiTest {
        // Arrange / Act
        showFlow()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesAreaCard).assertIsDisplayed()
        onNode(hasTestTag(MarketplaceAreaTestTags.BusinessesAreaCard) and hasAnyDescendant(hasText("9", substring = true))).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesChangeArea).assertIsDisplayed()
    }

    @Test
    fun changeOpensTheAreaChoiceStep() = runComposeUiTest {
        // Arrange
        showFlow()

        // Act
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesChangeArea).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.HomeSearchField).assertIsDisplayed()
    }

    @Test
    fun theTilesAreMyShopsThenAllThenGroupsAndOtherLast() = runComposeUiTest {
        // Arrange
        val groups = listOf(
            BusinessGroupCount("food_and_drink", "Food and drink", 8),
            BusinessGroupCount("services", "Services", 3),
            BusinessGroupCount(NearbyBusinesses.UNKNOWN_GROUP, "Unknown", 50),
        )
        val state = AreaBusinessesState(loaded = 61, groups = groups)

        // Act
        setContent { VestaTheme { AreaBusinessGroupsScreen(state, myShopsCount = 2, areaName = "Newport") } }
        waitForIdle()

        // Assert
        val tags = listOf(
            MarketplaceAreaTestTags.MyShopsRow,
            MarketplaceAreaTestTags.BusinessesAll,
            MarketplaceAreaTestTags.businessesGroup("food_and_drink"),
            MarketplaceAreaTestTags.businessesGroup("services"),
            MarketplaceAreaTestTags.businessesGroup(NearbyBusinesses.UNKNOWN_GROUP),
        )
        val positions = tags.map { tag ->
            val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            bounds.top.value to bounds.left.value
        }
        assertEquals(positions.sortedWith(compareBy({ it.first }, { it.second })), positions)
    }

    @Test
    fun directionsOpensAnOpenStreetMapPinOnThePlace() = runComposeUiTest {
        // Arrange
        val opened = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        val state = row("a", 50.7, -1.3).let { AreaBusinessesState(matching = 1, items = listOf(it), sections = listOf(BusinessSection(0, "", listOf(it)))) }

        // Act
        setContent { VestaTheme { CompositionLocalProvider(LocalUriHandler provides handler) { AreaBusinessesListScreen(state) } } }
        waitForIdle()
        onNodeWithTag(MarketplaceAreaTestTags.directions("a")).performClick()

        // Assert
        assertEquals(listOf("https://www.openstreetmap.org/?mlat=50.7&mlon=-1.3#map=18/50.7/-1.3"), opened)
    }

    @Test
    fun directionsIsAbsentWhenThePlaceHasNoCoordinates() = runComposeUiTest {
        // Arrange
        val state = row("b", null, null).let { AreaBusinessesState(matching = 1, items = listOf(it), sections = listOf(BusinessSection(0, "", listOf(it)))) }

        // Act
        setContent { VestaTheme { AreaBusinessesListScreen(state) } }
        waitForIdle()

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.businessesRow("b")).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.directions("b")).assertDoesNotExist()
    }
}
