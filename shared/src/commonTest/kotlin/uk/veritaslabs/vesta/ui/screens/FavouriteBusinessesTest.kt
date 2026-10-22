package uk.veritaslabs.vesta.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.vesta.location.BusinessRow

class FavouriteBusinessesTest {

    private class FakeSource(initial: List<Merchant> = emptyList()) : MerchantsSource {
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

    private fun business(id: String = "p1", name: String = "Greens", category: String = "Greengrocer") =
        BusinessRow(id, name, category, "Food", 100.0, 3, false, latitude = 50.7, longitude = -1.29)

    @Test
    fun businessMapsToAnIndependentTraderWithItsPlaceDetails() = runTest {
        // Arrange
        val favourites = FavouriteBusinesses(FakeSource())

        // Act
        val key = favourites.favourite(business(), "Newport")

        // Assert
        val saved = favourites.myShops.first().single()
        assertEquals(key, saved.key)
        assertEquals(
            Merchant(key, "Greens", MerchantType.IndependentTrader, emptyList(), listOf("Greengrocer"), OpeningHours(), 50.7, -1.29, "Newport", "p1"),
            saved,
        )
    }

    @Test
    fun favouritingTwiceKeepsOneMerchant() = runTest {
        // Arrange
        val source = FakeSource()
        val favourites = FavouriteBusinesses(source)

        // Act
        val first = favourites.favourite(business(), "Newport")
        val second = favourites.favourite(business(), "Newport")

        // Assert
        assertEquals(first, second)
        assertEquals(1, source.state.value.size)
    }

    @Test
    fun favouriteIdsFollowSaveAndUnfavourite() = runTest {
        // Arrange
        val favourites = FavouriteBusinesses(FakeSource(listOf(Merchant(key = "hand", name = "Stall"))))

        // Act
        favourites.favourite(business("p1"), "Newport")
        favourites.favourite(business("p2", "Bakes"), "Newport")
        favourites.unfavourite("p1")
        favourites.unfavourite("missing")

        // Assert
        assertEquals(setOf("p2"), favourites.favouriteIds.first())
        assertEquals(2, favourites.myShops.first().size)
    }

    @Test
    fun myShopsSortsAToZIgnoringCaseAndAccents() {
        // Arrange
        val merchants = listOf("Zed", "école", "apple", "Édith", "Banana").map { Merchant(key = it, name = it) }

        // Act
        val sorted = FavouriteBusinesses.myShops(merchants).map { it.name }

        // Assert
        assertEquals(listOf("apple", "Banana", "école", "Édith", "Zed"), sorted)
    }
}
