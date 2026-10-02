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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class HomeEditScreenTest {

    private val existingHome = HomeDetails(id = "flat-4b", name = "Flat 4B", shape = HomeShape.Flatshare)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun creatingAHomeHasNoOverflowMenuAndSaveIsDisabledUntilTheNameIsValid() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeEditScreen() } }

        // Act
        onNodeWithTag(HomeEditTestTags.NameField).performTextInput("A")

        // Assert
        onAllNodesWithTag(HomeEditTestTags.OverflowButton).assertCountEquals(0)
        onNodeWithTag(HomeEditTestTags.SaveButton).assertIsDisplayed().assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun savingANewHomeInvokesOnSaveWithTheTrimmedNameAndShapeOther() = runComposeUiTest {
        // Arrange
        var saved: HomeDetails? = null
        setContent { VestaTheme { HomeEditScreen(onSave = { saved = it }) } }

        // Act
        onNodeWithTag(HomeEditTestTags.NameField).performTextInput(" The Johnsons ")
        onNodeWithTag(HomeEditTestTags.SaveButton).assertIsEnabled().performClick()

        // Assert
        assertEquals(HomeDetails(id = null, name = "The Johnsons", shape = HomeShape.Other), saved)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editingAnExistingHomeKeepsItsIdAndShape() = runComposeUiTest {
        // Arrange
        var saved: HomeDetails? = null
        setContent { VestaTheme { HomeEditScreen(home = existingHome, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(HomeEditTestTags.NameField).performTextReplacement("Flat 4B Annex")
        onNodeWithTag(HomeEditTestTags.SaveButton).assertIsEnabled().performClick()

        // Assert
        assertEquals(HomeDetails(id = "flat-4b", name = "Flat 4B Annex", shape = HomeShape.Flatshare), saved)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deletingFromTheOverflowMenuInvokesOnDeleteWithTheHomeId() = runComposeUiTest {
        // Arrange
        var deletedId: String? = null
        setContent { VestaTheme { HomeEditScreen(home = existingHome, onDelete = { deletedId = it }) } }

        // Act
        onNodeWithTag(HomeEditTestTags.OverflowButton).performClick()
        onNodeWithTag(HomeEditTestTags.DeleteMenuItem).performClick()

        // Assert
        assertEquals("flat-4b", deletedId)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backInvokesOnCancel() = runComposeUiTest {
        // Arrange
        var cancelled = false
        setContent { VestaTheme { HomeEditScreen(onCancel = { cancelled = true }) } }

        // Act
        onNodeWithTag(HomeEditTestTags.BackButton).performClick()

        // Assert
        assertEquals(true, cancelled)
    }
}
