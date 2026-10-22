package uk.veritaslabs.vesta.ui.screens

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.location.HomeLocation
import uk.veritaslabs.vesta.location.TinyPack.id

internal class FakeMerchantsSource(initial: List<Merchant> = emptyList()) : MerchantsSource {
    val state = MutableStateFlow(initial)
    private var next = 1
    override val merchants = state
    override suspend fun save(merchant: Merchant): String {
        val key = merchant.key.ifEmpty { "m${next++}" }
        state.value = state.value.filter { it.key != key } + merchant.copy(key = key)
        return key
    }
    override suspend fun remove(key: String) {
        state.value = state.value.filter { it.key != key }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceAreaFavouritesViewModelTest {

    private val newport = id(3)
    private val cowes = id(5)
    private val release = "2026-09-23.1"

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        merchants: FakeMerchantsSource? = FakeMerchantsSource(),
    ): MarketplaceAreaViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val favourites = merchants?.let { FavouriteBusinesses(it) }
        return MarketplaceAreaViewModel(
            "home", AreaFakeSource(cached = true), AreaFakeRepository(HomeLocation("GB", newport, release, area = setOf(cowes))),
            AreaFakePlaces(), favourites,
        ) { "GB" }.also {
            it.start()
            it.seeBusinesses()
            it.openAll()
        }
    }

    private val MarketplaceAreaViewModel.ui get() = uiState.value

    @Test
    fun heartingABusinessAddsItToMyShopsAndFillsItsHeart() = runTest {
        // Arrange
        val merchants = FakeMerchantsSource()
        val viewModel = viewModel(merchants)
        val business = viewModel.ui.businesses.items.first()

        // Act
        viewModel.toggleFavourite(business)

        // Assert
        assertEquals(setOf(business.id), viewModel.ui.favouriteIds)
        assertEquals(listOf(business.name), viewModel.ui.myShops.map { it.name })
        assertEquals(business.localityName, merchants.state.value.single().address)
    }

    @Test
    fun heartingAgainRemovesTheFavourite() = runTest {
        // Arrange
        val viewModel = viewModel()
        val business = viewModel.ui.businesses.items.first()
        viewModel.toggleFavourite(business)

        // Act
        viewModel.toggleFavourite(business)

        // Assert
        assertTrue(viewModel.ui.favouriteIds.isEmpty())
    }

    @Test
    fun myShopsOpensFromTheCategoriesListsHandMadeOnesAndBackReturns() = runTest {
        // Arrange
        val merchants = FakeMerchantsSource(listOf(Merchant(key = "z", name = "Zed Stall"), Merchant(key = "a", name = "Apple Cart")))
        val viewModel = viewModel(merchants)
        viewModel.back()

        // Act
        viewModel.openMyShops()

        // Assert
        assertEquals(AreaStep.MyShops, viewModel.ui.step)
        assertEquals(listOf("Apple Cart", "Zed Stall"), viewModel.ui.myShops.map { it.name }.sorted())
        assertTrue(viewModel.back())
        assertEquals(AreaStep.BusinessesCategories, viewModel.ui.step)
    }

    @Test
    fun removingAHandMadeShopFromMyShopsWorks() = runTest {
        // Arrange
        val merchants = FakeMerchantsSource(listOf(Merchant(key = "z", name = "Zed Stall")))
        val viewModel = viewModel(merchants)

        // Act
        viewModel.removeShop("z")

        // Assert
        assertTrue(merchants.state.value.isEmpty())
        assertTrue(viewModel.ui.myShops.isEmpty())
    }

    @Test
    fun withoutAMerchantsDocumentFavouritesAreUnavailableAndMyShopsDoesNotOpen() = runTest {
        // Arrange
        val viewModel = viewModel(merchants = null)
        viewModel.back()

        // Act
        viewModel.openMyShops()

        // Assert
        assertEquals(false, viewModel.ui.favouritesAvailable)
        assertEquals(AreaStep.BusinessesCategories, viewModel.ui.step)
    }
}
