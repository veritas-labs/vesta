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

class PantryScreenTest {

    private val items = listOf(
        PantryListItem(
            key = "5000157024671",
            primaryCategory = "Condiments",
            secondaryCategory = "Ketchup",
            title = "Heinz Ketchup",
            packageSize = "6 pack",
            stockCount = 4,
        ),
        PantryListItem(
            key = "whole milk",
            primaryCategory = "Dairy",
            secondaryCategory = "Milk",
            title = "Whole Milk",
            packageSize = "2 pint",
            stockCount = 0,
        ),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachItemGroupedByCategoryWithItsStockCount() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryScreen(items = items) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        // Text assertions here because the item/category/stock data is the
        // data under test — a real regression (e.g. the wrong stock count
        // rendering) should fail this test, unlike the tag-based assertion.
        onNodeWithText("Condiments").assertIsDisplayed()
        onNodeWithText("Heinz Ketchup").assertIsDisplayed()
        onNodeWithText("4 on shelf").assertIsDisplayed()
        onNodeWithText("Dairy").assertIsDisplayed()
        onNodeWithText("Whole Milk").assertIsDisplayed()
        onNodeWithText("Out of stock").assertIsDisplayed()
        onNodeWithTag(PantryTestTags.pantryItem("5000157024671")).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsHeaderAndEmptyMessageForACategoryWithNoItems() = runComposeUiTest {
        // Arrange
        setContent {
            VestaTheme { PantryScreen(items = items, emptyCategories = listOf("Snacks")) }
        }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Snacks").assertIsDisplayed()
        onNodeWithText("No items yet").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEmptyStateWhenThereAreNoItems() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryScreen(items = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Your pantry is empty").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingAnItemInvokesOnItemSelectedForThatItem() = runComposeUiTest {
        // Arrange
        var selected: PantryListItem? = null
        setContent {
            VestaTheme { PantryScreen(items = items, onItemSelected = { selected = it }) }
        }

        // Act
        onNodeWithTag(PantryTestTags.pantryItem("whole milk")).performClick()

        // Assert
        assertEquals("whole milk", selected?.key)
    }
}
