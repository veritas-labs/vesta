package uk.veritaslabs.vesta.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class MerchantLinksTest {

    @Test
    fun addAppendsAtTheLowestPrioritySkippingMerchantsAlreadyThere() {
        // Arrange
        val current = listOf("a", "b")

        // Act
        val result = MerchantLinkChange.Add(listOf("c", "a", "d")).applyTo(current)

        // Assert
        assertEquals(listOf("a", "b", "c", "d"), result)
    }

    @Test
    fun removeTakesOffOnlyTheNamedMerchantsKeepingTheOrder() {
        // Arrange
        val current = listOf("a", "b", "c")

        // Act
        val result = MerchantLinkChange.Remove(setOf("b", "z")).applyTo(current)

        // Assert
        assertEquals(listOf("a", "c"), result)
    }

    @Test
    fun replaceGivesExactlyTheNewOrderedListWithoutRepeatsOrBlanks() {
        // Arrange
        val current = listOf("a", "b")

        // Act
        val result = MerchantLinkChange.Replace(listOf("c", "", "a", "c")).applyTo(current)

        // Assert
        assertEquals(listOf("c", "a"), result)
    }

    @Test
    fun linkedMerchantsSkipsKeysNoMerchantHasAndKeepsTheKeyOrder() {
        // Arrange
        val merchants = listOf(Merchant(key = "a", name = "A"), Merchant(key = "b", name = "B"))

        // Act
        val result = linkedMerchants(listOf("b", "gone", "a"), merchants)

        // Assert
        assertEquals(listOf("B", "A"), result.map { it.name })
    }

    @Test
    fun movingUpSwapsWithTheNearestVisibleKeySkippingDeletedOnes() {
        // Arrange
        val keys = listOf("a", "gone", "b")

        // Act
        val result = moveMerchantKey(keys, "b", up = true) { it != "gone" }

        // Assert
        assertEquals(listOf("b", "gone", "a"), result)
    }

    @Test
    fun movingTheLastKeyDownOrTheFirstUpChangesNothing() {
        // Arrange
        val keys = listOf("a", "b")

        // Act
        val down = moveMerchantKey(keys, "b", up = false) { true }
        val up = moveMerchantKey(keys, "a", up = true) { true }

        // Assert
        assertEquals(keys, down)
        assertEquals(keys, up)
    }
}
