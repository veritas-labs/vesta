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

    private val tescoItem = ShoppingListItem("beans", "Tins", null, "Beans", 1, "", merchantKeys = listOf("tesco"))
    private val butcherItem = ShoppingListItem("mince", "Meat", null, "Mince", 1, "", merchantKeys = listOf("butcher", "tesco"), got = 1)

    @Test
    fun withMerchantKeysTakesThemFromThePantryEntryAtTheBarcodeOrTitle() {
        // Arrange
        val items = listOf(
            ShoppingListItem("a", "Tins", null, "Beans ", 1, ""),
            ShoppingListItem("b", "Tins", null, "Salt", 1, "", barcode = "123"),
            ShoppingListItem("c", "Tins", null, "Pepper", 1, ""),
        )
        val pantry = listOf(
            PantryListItem("beans", "Tins", null, "Beans", "", 0, merchantKeys = listOf("tesco")),
            PantryListItem("123", "Tins", null, "Salt", "", 0, barcode = "123", merchantKeys = listOf("butcher")),
        )

        // Act
        val result = withMerchantKeys(items, pantry)

        // Assert
        assertEquals(listOf(listOf("tesco"), listOf("butcher"), emptyList()), result.map { it.merchantKeys })
    }

    @Test
    fun selectingAMerchantShowsOnlyItemsBoughtFromIt() {
        // Arrange
        val items = listOf(tescoItem, butcherItem)

        // Act
        val shown = filterByMerchants(items, setOf("butcher"))

        // Assert
        assertEquals(listOf(butcherItem), shown)
    }

    @Test
    fun aSelectedMerchantNoItemUsesIsIgnored() {
        // Arrange
        val items = listOf(tescoItem, butcherItem)

        // Act
        val shown = filterByMerchants(items, setOf("unused"))

        // Assert
        assertEquals(items, shown)
    }

    @Test
    fun hideCheckedDropsCheckedItemsAndCombinesWithTheOtherFilters() {
        // Arrange
        val items = listOf(tescoItem, butcherItem)

        // Act
        val shown = applyShoppingFilters(items, emptySet(), setOf("tesco"), hideChecked = true)

        // Assert
        assertEquals(listOf(tescoItem), shown)
    }
}
