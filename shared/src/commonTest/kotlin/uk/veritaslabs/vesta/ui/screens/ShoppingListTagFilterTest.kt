package uk.veritaslabs.vesta.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class ShoppingListTagFilterTest {

    private val milk = ShoppingListItem("milk", "Dairy", null, "Milk", 1, "", tags = listOf("Farm shop"))
    private val sausages = ShoppingListItem("sausages", "Meat", null, "Sausages", 1, "", tags = listOf("Butcher", "farm SHOP"))
    private val beans = ShoppingListItem("beans", "Tins", null, "Beans", 1, "", tags = listOf("Supermarket"))
    private val salt = ShoppingListItem("salt", "Tins", null, "Salt", 1, "")
    private val all = listOf(milk, sausages, beans, salt)

    @Test
    fun tagsAreDistinctIgnoringCaseAndSorted() {
        // Arrange
        val items = all

        // Act
        val tags = shoppingTags(items)

        // Assert
        assertEquals(listOf("Butcher", "Farm shop", "Supermarket"), tags)
    }

    @Test
    fun withNothingSelectedEveryItemShows() {
        // Arrange
        val selected = emptySet<String>()

        // Act
        val shown = filterByTags(all, selected)

        // Assert
        assertEquals(all, shown)
    }

    @Test
    fun selectingATagShowsOnlyItemsWithItIgnoringCase() {
        // Arrange
        val selected = setOf("farm shop")

        // Act
        val shown = filterByTags(all, selected)

        // Assert
        assertEquals(listOf(milk, sausages), shown)
    }

    @Test
    fun selectingSeveralTagsShowsItemsWithAnyOfThem() {
        // Arrange
        val selected = setOf("butcher", "supermarket")

        // Act
        val shown = filterByTags(all, selected)

        // Assert
        assertEquals(listOf(sausages, beans), shown)
    }

    @Test
    fun aSelectedTagNoItemHasAnyMoreIsIgnored() {
        // Arrange
        val selected = setOf("bakery")

        // Act
        val shown = filterByTags(all, selected)

        // Assert
        assertEquals(all, shown)
    }
}
