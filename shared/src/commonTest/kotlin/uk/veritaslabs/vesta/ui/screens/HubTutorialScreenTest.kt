package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class HubTutorialScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun startsOnTheFirstPageWithNextAndNoPrevious() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HubTutorialScreen() } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HubTutorialTestTags.Next).assertIsDisplayed()
        onNodeWithTag(HubTutorialTestTags.Skip).assertIsDisplayed()
        onNodeWithTag(HubTutorialTestTags.Previous).assertDoesNotExist()
        onNodeWithTag(HubTutorialTestTags.Finish).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nextAndPreviousMoveBetweenPages() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HubTutorialScreen() } }

        // Act
        onNodeWithTag(HubTutorialTestTags.Next).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(HubTutorialTestTags.Previous).assertIsDisplayed()

        // Act
        onNodeWithTag(HubTutorialTestTags.Previous).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(HubTutorialTestTags.Previous).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun lastPageOffersFinishInsteadOfNextAndSkip() = runComposeUiTest {
        // Arrange
        var finished = 0
        setContent { VestaTheme { HubTutorialScreen(initialPage = Int.MAX_VALUE, onFinish = { finished++ }) } }

        // Act
        onNodeWithTag(HubTutorialTestTags.Finish).performClick()

        // Assert
        assertEquals(1, finished)
        onNodeWithTag(HubTutorialTestTags.Next).assertDoesNotExist()
        onNodeWithTag(HubTutorialTestTags.Skip).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backAndSkipLeave() = runComposeUiTest {
        // Arrange
        var left = 0
        setContent { VestaTheme { HubTutorialScreen(onBack = { left++ }) } }

        // Act
        onNodeWithTag(HubTutorialTestTags.BackButton).performClick()
        onNodeWithTag(HubTutorialTestTags.Skip).performClick()

        // Assert
        assertEquals(2, left)
    }
}
