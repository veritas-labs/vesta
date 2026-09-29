package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class PantryItemEditScreenTest {

    private val existingItem = PantryItemDetails(
        key = "5000157024671",
        primaryCategory = "Condiments",
        secondaryCategory = "Ketchup",
        title = "Heinz Ketchup",
        packageSize = "500ml",
        barcode = "5000157024671",
        stockCount = 4,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addingANewItemHasNoOverflowMenuAndSaveIsDisabledUntilRequiredFieldsAreFilled() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditScreen() } }

        // Act
        // (no user interaction yet — checking the screen's initial state)

        // Assert
        onAllNodesWithTag(PantryItemEditTestTags.OverflowButton).assertCountEquals(0)
        onNodeWithTag(PantryItemEditTestTags.SaveButton).assertIsDisplayed().assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fillingInTheRequiredFieldsAndSavingInvokesOnSaveWithTheTrimmedValues() = runComposeUiTest {
        // Arrange
        var saved: PantryItemDetails? = null
        setContent { VestaTheme { PantryItemEditScreen(onSave = { saved = it }) } }

        // Act
        onNodeWithTag(PantryItemEditTestTags.NameField).performTextInput(" Bananas ")
        onNodeWithTag(PantryItemEditTestTags.PrimaryCategoryField).performTextInput(" Fruit & Vegetables ")
        onNodeWithTag(PantryItemEditTestTags.PackageSizeField).performTextInput(" 6 pack ")
        onNodeWithTag(PantryItemEditTestTags.SaveButton).assertIsEnabled().performClick()

        // Assert
        assertEquals("Bananas", saved?.title)
        assertEquals("Fruit & Vegetables", saved?.primaryCategory)
        assertEquals("6 pack", saved?.packageSize)
        assertNull(saved?.key)
        assertNull(saved?.secondaryCategory)
        assertNull(saved?.barcode)
        assertEquals(0, saved?.stockCount)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editingAnExistingItemPrefillsItsFieldsAndShowsTheOverflowMenu() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryItemEditScreen(item = existingItem) } }

        // Act
        // (no user interaction — checking the screen's initial, pre-filled state)

        // Assert
        onNodeWithTag(PantryItemEditTestTags.OverflowButton).assertIsDisplayed()
        onNodeWithTag(PantryItemEditTestTags.SaveButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun choosingDeleteFromTheOverflowMenuInvokesOnDeleteWithTheItemsKey() = runComposeUiTest {
        // Arrange
        var deletedKey: String? = null
        setContent {
            VestaTheme { PantryItemEditScreen(item = existingItem, onDelete = { deletedKey = it }) }
        }

        // Act
        onNodeWithTag(PantryItemEditTestTags.OverflowButton).performClick()
        onNodeWithTag(PantryItemEditTestTags.DeleteMenuItem).performClick()

        // Assert
        assertEquals("5000157024671", deletedKey)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingCancelInvokesOnCancel() = runComposeUiTest {
        // Arrange
        var cancelled = false
        setContent { VestaTheme { PantryItemEditScreen(onCancel = { cancelled = true }) } }

        // Act
        onNodeWithTag(PantryItemEditTestTags.CancelButton).performClick()

        // Assert
        assertEquals(true, cancelled)
    }
}
