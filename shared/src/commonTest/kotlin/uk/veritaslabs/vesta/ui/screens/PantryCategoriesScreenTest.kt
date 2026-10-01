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

class PantryCategoriesScreenTest {

    private val categories = listOf(
        PantryCategoryGroup("Condiments", listOf("Ketchup", "Mayonnaise")),
        PantryCategoryGroup("Snacks"),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun listsEachCategoryWithItsSecondaryCategories() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryCategoriesScreen(categories = categories) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Condiments").assertIsDisplayed()
        onNodeWithText("Ketchup").assertIsDisplayed()
        onNodeWithText("Mayonnaise").assertIsDisplayed()
        onNodeWithText("Snacks").assertIsDisplayed()
        onNodeWithText("No subcategories").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addingACategoryReportsItsName() = runComposeUiTest {
        // Arrange
        var added: String? = null
        setContent { VestaTheme { PantryCategoriesScreen(categories = categories, onAddPrimary = { added = it }) } }

        // Act
        onNodeWithTag(PantryCategoriesTestTags.AddCategoryButton).performClick()
        onNodeWithTag(PantryCategoriesTestTags.NameField).performTextInput("Pet Food")
        onNodeWithTag(PantryCategoriesTestTags.ConfirmAdd).performClick()

        // Assert
        assertEquals("Pet Food", added)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addingASecondaryReportsItsPrimaryAndName() = runComposeUiTest {
        // Arrange
        var added: Pair<String, String>? = null
        setContent {
            VestaTheme { PantryCategoriesScreen(categories = categories, onAddSecondary = { p, n -> added = p to n }) }
        }

        // Act
        onNodeWithTag(PantryCategoriesTestTags.addSecondary("Snacks")).performClick()
        onNodeWithTag(PantryCategoriesTestTags.NameField).performTextInput("Crisps")
        onNodeWithTag(PantryCategoriesTestTags.ConfirmAdd).performClick()

        // Assert
        assertEquals("Snacks" to "Crisps", added)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun removingASecondaryReportsItImmediately() = runComposeUiTest {
        // Arrange
        var removed: Pair<String, String>? = null
        setContent {
            VestaTheme { PantryCategoriesScreen(categories = categories, onDeleteSecondary = { p, n -> removed = p to n }) }
        }

        // Act
        onNodeWithTag(PantryCategoriesTestTags.removeSecondary("Condiments", "Ketchup")).performClick()

        // Assert
        assertEquals("Condiments" to "Ketchup", removed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun removingAPrimaryNeedsConfirmation() = runComposeUiTest {
        // Arrange
        var removed: String? = null
        setContent { VestaTheme { PantryCategoriesScreen(categories = categories, onDeletePrimary = { removed = it }) } }

        // Act
        onNodeWithTag(PantryCategoriesTestTags.removePrimary("Snacks")).performClick()
        val beforeConfirm = removed
        onNodeWithTag(PantryCategoriesTestTags.ConfirmRemove).performClick()

        // Assert
        assertEquals(null, beforeConfirm)
        assertEquals("Snacks", removed)
    }
}
