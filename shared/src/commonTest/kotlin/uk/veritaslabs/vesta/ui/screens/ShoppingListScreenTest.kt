package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class ShoppingListScreenTest {

    private val milk = ShoppingListItem("whole milk", "Dairy", "Milk", "Whole Milk", 3, "2 pint")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachItemUnderItsCategoryWithItsQuantity() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Dairy").assertIsDisplayed()
        onNodeWithText("Milk").assertIsDisplayed()
        onNodeWithText("Whole Milk").assertIsDisplayed()
        onNodeWithText("×3").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAMessageWhenTheListIsEmpty() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Nothing to buy").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingARowSelectsThatItem() = runComposeUiTest {
        // Arrange
        var selected: ShoppingListItem? = null
        setContent { VestaTheme { ShoppingListScreen(items = listOf(milk), onItemSelected = { selected = it }) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.item("whole milk")).performClick()

        // Assert
        assertEquals(milk, selected)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theAddButtonAsksToAddAnItem() = runComposeUiTest {
        // Arrange
        var added = false
        setContent { VestaTheme { ShoppingListScreen(items = emptyList(), onAddItem = { added = true }) } }

        // Act
        onNodeWithTag(ShoppingListTestTags.AddItemButton).performClick()

        // Assert
        assertEquals(true, added)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theItemFormUsesQuantityWordingAndStartsAtOne() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme {
                PantryItemEditScreen(
                    item = PantryItemDetails(title = "Milk", primaryCategory = "Dairy", stockCount = 1),
                    labels = ShoppingItemFormLabels,
                    onSave = { saved = it },
                )
            }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.StockIncreaseButton).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performClick()

        // Assert
        onNodeWithText("Add shopping item").assertIsDisplayed()
        assertEquals(2, saved?.stockCount)
    }

    private val pantry = listOf(
        PantryListItem("whole milk", "Dairy", "Milk", "Whole Milk", "2 pint", 1, barcode = "123"),
        PantryListItem("oat milk", "Dairy", "Milk", "Oat Milk", "1 litre", 0),
        PantryListItem("bananas", "Fruit", null, "Bananas", "6 pack", 2),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun typingANameSuggestsMatchingPantryItemsAndPickingOneFillsTheForm() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent {
            VestaTheme {
                PantryItemEditScreen(labels = ShoppingItemFormLabels, suggestions = pantry, onSave = { saved = it })
            }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.NameField).performTextInput("mil")
        onNodeWithTag(PantryItemEditTestTags.suggestion("oat milk")).assertIsDisplayed()
        onNodeWithTag(PantryItemEditTestTags.suggestion("bananas")).assertDoesNotExist()
        onNodeWithTag(PantryItemEditTestTags.suggestion("whole milk")).performClick()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(
            PantryItemDetails(
                primaryCategory = "Dairy", secondaryCategory = "Milk", title = "Whole Milk",
                packageSize = "2 pint", barcode = "123", stockCount = 1,
            ),
            saved,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noSuggestionsWhileEditingAnExistingItem() = runComposeUiTest {
        // Arrange
        setContent {
            VestaTheme {
                PantryItemEditScreen(
                    item = PantryItemDetails(key = "k", primaryCategory = "Dairy", title = "Mil", stockCount = 1),
                    labels = ShoppingItemFormLabels,
                    suggestions = pantry,
                )
            }
        }

        // Act
        // (no user interaction — the name already matches Pantry items)

        // Assert
        onNodeWithTag(PantryItemEditTestTags.suggestion("whole milk")).assertDoesNotExist()
    }

    @Test
    fun suggestionsListTitlesStartingWithTheTextFirstAndAreCapped() {
        // Arrange
        val many = (1..8).map { PantryListItem("k$it", "C", null, "Item $it", "", 0) } +
            PantryListItem("tea", "C", null, "Green Tea", "", 0) +
            PantryListItem("team", "C", null, "Team Snack", "", 0)

        // Act
        val result = suggestionsFor("te", many).map { it.title }

        // Assert
        assertEquals(listOf("Team Snack", "Item 1", "Item 2", "Item 3", "Item 4"), result)
    }
}
