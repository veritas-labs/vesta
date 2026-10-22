package uk.veritaslabs.vesta.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.kits.x0x.X0xConnectivity
import uk.veritaslabs.kits.spaces.ProfileRepository
import uk.veritaslabs.kits.spaces.SpaceRepository
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.App
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.HomeListItem
import uk.veritaslabs.vesta.home.SelectedHome
import uk.veritaslabs.vesta.ui.screens.AreaFakeRepository
import uk.veritaslabs.vesta.ui.screens.AreaFakeSource
import uk.veritaslabs.vesta.ui.screens.HomeDashboardTestTags
import uk.veritaslabs.vesta.ui.screens.HomeEditTestTags
import uk.veritaslabs.vesta.ui.screens.HomePickerTestTags
import uk.veritaslabs.vesta.ui.screens.MarketplaceAreaTestTags

/** The Marketplace tile opens the area flow directly (ADR-039, WP-M2), and Create a Home never asks for a location. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarketplaceNearbyMerchantsNavigationTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theMarketplaceTileOpensTheAreaFlowAndBackReturnsToTheHome() = runComposeUiTest {
        // Arrange
        val selected = SelectedHome().also { it.select("home-1") }
        setContent {
            App(
                homes = flowOf(listOf(HomeListItem("home-1", "Maple Street"))),
                selectedHome = selected,
                x0xConnectivity = { X0xConnectivity.Connected(peers = 5) },
                localityPackSource = AreaFakeSource(cached = false),
                homeLocationRepository = AreaFakeRepository(),
            )
        }
        waitUntilExactlyOneExists(hasTestTag(HomeDashboardTestTags.MarketplaceTile))

        // Act
        onNodeWithTag(HomeDashboardTestTags.MarketplaceTile).performClick()

        // Assert
        waitUntilExactlyOneExists(hasTestTag(MarketplaceAreaTestTags.Disclosure))
        onNodeWithTag(MarketplaceAreaTestTags.Disclosure).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.DisclosureNotNow).performClick()
        onNodeWithTag(HomeDashboardTestTags.MarketplaceTile).assertIsDisplayed()
    }

    @Test
    fun creatingAHomeNeverMentionsLocation() = runComposeUiTest {
        // Arrange
        val database = openInMemoryDatabase()
        val homeRepository = HomeRepository(SpaceRepository(database, profiles = ProfileRepository(database, deviceName = { "Mark" })))
        setContent { App(homeRepository = homeRepository, x0xConnectivity = { X0xConnectivity.Connected(peers = 5) }) }
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.CreateHomeButton))

        // Act
        onNodeWithTag(HomePickerTestTags.CreateHomeButton).performClick()

        // Assert
        waitUntilExactlyOneExists(hasTestTag(HomeEditTestTags.NameField))
        listOf("location", "area", "nearby", "address", "postcode").forEach { word ->
            onAllNodes(hasText(word, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }
}
