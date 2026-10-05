package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
