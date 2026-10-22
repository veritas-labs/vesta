package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.TinyPack.id
import uk.veritaslabs.vesta.ui.flows.MarketplaceAreaFlow
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MarketplaceAreaFavouritesFlowTest {

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ComposeUiTest.show(
        merchants: FakeMerchantsSource? = FakeMerchantsSource(),
        usage: MerchantUsage = MerchantUsage.None,
    ): MarketplaceAreaViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = MarketplaceAreaViewModel(
            "home", AreaFakeSource(cached = true),
            AreaFakeRepository(HomeLocation("GB", id(3), "2026-09-23.1", area = setOf(id(5)))),
            AreaFakePlaces(), merchants?.let { FavouriteBusinesses(it, usage) },
        ) { "GB" }
        setContent { VestaTheme { MarketplaceAreaFlow(viewModel, onBack = {}) } }
        waitForIdle()
        return viewModel
    }

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openList() {
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        tap(MarketplaceAreaTestTags.BusinessesAll)
    }

    @Test
    fun tappingTheHeartFillsItAndRetappingEmptiesIt() = runComposeUiTest {
        // Arrange
        val viewModel = show()
        openList()
        val business = viewModel.uiState.value.businesses.items.first()
        val heart = MarketplaceAreaTestTags.favouriteHeart(business.id)
        onNodeWithTag(heart).assertContentDescriptionEquals("Add ${business.name} to My Shops")

        // Act
        tap(heart)

        // Assert
        onNodeWithTag(heart).assertContentDescriptionEquals("Remove ${business.name} from My Shops")
        assertEquals(setOf(business.id), viewModel.uiState.value.favouriteIds)

        // Act
        tap(heart)

        // Assert
        onNodeWithTag(heart).assertContentDescriptionEquals("Add ${business.name} to My Shops")
    }

    @Test
    fun unfavouritingAShopInUseAsksAndCancelKeepsIt() = runComposeUiTest {
        // Arrange
        val viewModel = show(usage = { 4 })
        openList()
        val business = viewModel.uiState.value.businesses.items.first()
        tap(MarketplaceAreaTestTags.favouriteHeart(business.id))

        // Act
        tap(MarketplaceAreaTestTags.favouriteHeart(business.id))

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.RemoveShopDialog).assertIsDisplayed()

        // Act
        tap(MarketplaceAreaTestTags.RemoveShopCancel)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.RemoveShopDialog).assertDoesNotExist()
        assertEquals(setOf(business.id), viewModel.uiState.value.favouriteIds)
    }

    @Test
    fun confirmingTheQuestionRemovesTheShop() = runComposeUiTest {
        // Arrange
        val viewModel = show(usage = { 4 })
        openList()
        val business = viewModel.uiState.value.businesses.items.first()
        tap(MarketplaceAreaTestTags.favouriteHeart(business.id))
        tap(MarketplaceAreaTestTags.favouriteHeart(business.id))

        // Act
        tap(MarketplaceAreaTestTags.RemoveShopConfirm)

        // Assert
        assertEquals(emptySet(), viewModel.uiState.value.favouriteIds)
    }

    @Test
    fun myShopsRowOpensTheListWithHandMadeShopsAndHeartsRemove() = runComposeUiTest {
        // Arrange
        val merchants = FakeMerchantsSource(listOf(Merchant(key = "z", name = "Zed Stall"), Merchant(key = "a", name = "Apple Cart")))
        show(merchants)
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        onNodeWithTag(MarketplaceAreaTestTags.MyShopsRow).assertIsDisplayed()

        // Act
        tap(MarketplaceAreaTestTags.MyShopsRow)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.myShopsItem("a")).assertIsDisplayed()
        onNodeWithTag(MarketplaceAreaTestTags.myShopsItem("z")).assertIsDisplayed()

        // Act
        tap(MarketplaceAreaTestTags.myShopsHeart("z"))

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.myShopsItem("z")).assertDoesNotExist()
        assertEquals(listOf("a"), merchants.state.value.map { it.key })
    }

    @Test
    fun backFromMyShopsReturnsToTheCategories() = runComposeUiTest {
        // Arrange
        show()
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)
        tap(MarketplaceAreaTestTags.MyShopsRow)

        // Act
        tap(MarketplaceAreaTestTags.BackButton)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.BusinessesGroups).assertIsDisplayed()
    }

    @Test
    fun withoutAMerchantsDocumentThereAreNoHeartsAndNoMyShopsRow() = runComposeUiTest {
        // Arrange
        val viewModel = show(merchants = null)

        // Act
        tap(MarketplaceAreaTestTags.SavedSeeBusinesses)

        // Assert
        onNodeWithTag(MarketplaceAreaTestTags.MyShopsRow).assertDoesNotExist()
        tap(MarketplaceAreaTestTags.BusinessesAll)
        val business = viewModel.uiState.value.businesses.items.first()
        onNodeWithTag(MarketplaceAreaTestTags.favouriteHeart(business.id)).assertDoesNotExist()
    }
}
