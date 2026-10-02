package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.homejoin.HomeInviteError
import uk.veritaslabs.vesta.homejoin.HomeInviteUiState
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class HomeInviteScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theSendButtonNeedsALink() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeInviteScreen(HomeInviteUiState.Idle) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeInviteTestTags.SubmitButton).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun sendingPassesThePastedLinkOn() = runComposeUiTest {
        // Arrange
        var sent: String? = null
        setContent { VestaTheme { HomeInviteScreen(HomeInviteUiState.Idle, onInvite = { sent = it }) } }

        // Act
        onNodeWithTag(HomeInviteTestTags.LinkField).performTextInput("vesta://join/abc")
        onNodeWithTag(HomeInviteTestTags.SubmitButton).assertIsEnabled().performClick()

        // Assert
        assertEquals("vesta://join/abc", sent)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsWhyAnInvitationFailed() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeInviteScreen(HomeInviteUiState.Failed(HomeInviteError.Expired)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeInviteTestTags.Error).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsProgressAndStopsFurtherSendsWhileInviting() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeInviteScreen(HomeInviteUiState.Inviting) } }

        // Act
        onNodeWithTag(HomeInviteTestTags.LinkField).assertIsNotEnabled()

        // Assert
        onNodeWithTag(HomeInviteTestTags.Progress).assertIsDisplayed()
        onNodeWithTag(HomeInviteTestTags.SubmitButton).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun confirmsWhenThePersonHasJoined() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeInviteScreen(HomeInviteUiState.Invited) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeInviteTestTags.Done).assertIsDisplayed()
    }
}
