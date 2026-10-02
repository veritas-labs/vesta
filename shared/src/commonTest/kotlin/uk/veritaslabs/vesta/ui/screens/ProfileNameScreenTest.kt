package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class ProfileNameScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun startsWithTheSuggestedNameAndItsLength() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ProfileNameScreen("Mark's laptop", ProfileNameMode.Setup) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(ProfileNameTestTags.NameField).assertTextEquals("Display name", "Mark's laptop")
        onNodeWithTag(ProfileNameTestTags.Counter).assertTextEquals("13/40")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun savingPassesOnTheTrimmedName() = runComposeUiTest {
        // Arrange
        var saved: String? = null
        setContent { VestaTheme { ProfileNameScreen("x", ProfileNameMode.Setup, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(ProfileNameTestTags.NameField).performTextClearance()
        onNodeWithTag(ProfileNameTestTags.NameField).performTextInput("  Mark  ")
        onNodeWithTag(ProfileNameTestTags.SaveButton).performClick()

        // Assert
        assertEquals("Mark", saved)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aNameThatIsTooShortIsNotSavedAndShowsWhy() = runComposeUiTest {
        // Arrange
        var saved: String? = null
        setContent { VestaTheme { ProfileNameScreen("x", ProfileNameMode.Setup, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(ProfileNameTestTags.SaveButton).performClick()

        // Assert
        assertEquals(null, saved)
        onNodeWithTag(ProfileNameTestTags.Error).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theErrorWaitsUntilTheUserTriesToSave() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ProfileNameScreen("x", ProfileNameMode.Setup) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(ProfileNameTestTags.Error).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theNameCannotGrowPastFortyCharacters() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ProfileNameScreen("", ProfileNameMode.Setup) } }

        // Act
        onNodeWithTag(ProfileNameTestTags.NameField).performTextInput("x".repeat(60))

        // Assert
        onNodeWithTag(ProfileNameTestTags.Counter).assertTextEquals("40/40")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingUpOffersAWayBack() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ProfileNameScreen("Mark", ProfileNameMode.Setup) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(ProfileNameTestTags.BackButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun renamingCanGoBackWithoutSaving() = runComposeUiTest {
        // Arrange
        var backs = 0
        var saved: String? = null
        setContent {
            VestaTheme { ProfileNameScreen("Mark", ProfileNameMode.Rename, onSave = { saved = it }, onBack = { backs++ }) }
        }

        // Act
        onNodeWithTag(ProfileNameTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
        assertEquals(null, saved)
    }
}
