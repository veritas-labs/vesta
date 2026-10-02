package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.homejoin.HomeJoinMessage
import uk.veritaslabs.vesta.homejoin.PendingJoin
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomePickerScreenTest {

    private val homes = listOf(
        HomeListItem(id = "the-johnsons", name = "The Johnsons"),
        HomeListItem(id = "flat-4b", name = "Flat 4B"),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachHomeAndTheCreateJoinAndPairDeviceButtons() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomePickerScreen(homes = homes) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        // Text assertions here because the home names are the data under
        // test — a real regression (e.g. the wrong home's name rendering)
        // should fail this test, unlike the tag-based assertions below.
        onNodeWithText("The Johnsons").assertIsDisplayed()
        onNodeWithText("Flat 4B").assertIsDisplayed()
        onNodeWithTag(HomePickerTestTags.CreateHomeButton).assertIsDisplayed()
        onNodeWithTag(HomePickerTestTags.JoinHomeButton).assertIsDisplayed()
        onNodeWithTag(HomePickerTestTags.PairDeviceButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingAHomeInvokesOnHomeSelected() = runComposeUiTest {
        // Arrange
        var selected: HomeListItem? = null
        setContent {
            VestaTheme { HomePickerScreen(homes = homes, onHomeSelected = { selected = it }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.homeItem("the-johnsons")).performClick()

        // Assert
        assertEquals("the-johnsons", selected?.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingCreateHomeInvokesOnCreateHome() = runComposeUiTest {
        // Arrange
        var created = false
        setContent {
            VestaTheme { HomePickerScreen(homes = homes, onCreateHome = { created = true }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.CreateHomeButton).performClick()

        // Assert
        assertTrue(created)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingTheSettingsCogInvokesOnSettingsClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent {
            VestaTheme { HomePickerScreen(homes = homes, onSettingsClick = { clicks++ }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.SettingsButton).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hidesTheSettingsCogWhenThereAreNoHomes() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomePickerScreen(homes = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomePickerTestTags.SettingsButton).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingPairDeviceInvokesOnPairDevice() = runComposeUiTest {
        // Arrange
        var paired = false
        setContent {
            VestaTheme { HomePickerScreen(homes = emptyList(), onPairDevice = { paired = true }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.PairDeviceButton).performClick()

        // Assert
        assertTrue(paired)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingJoinHomeInvokesOnJoinHome() = runComposeUiTest {
        // Arrange
        var joined = false
        setContent {
            VestaTheme { HomePickerScreen(homes = homes, onJoinHome = { joined = true }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.JoinHomeButton).performClick()

        // Assert
        assertTrue(joined)
    }

    private val waiting = PendingJoin("maple", "Maple Street", expiresAt = 1_000L, denial = null)
    private val declined = PendingJoin("elm", "Elm Court", expiresAt = 1_000L, denial = HomeJoinMessage.DeniedDeclined)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsACardForEachInviteLinkStillWaitingOrRefused() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomePickerScreen(homes = homes, pendingJoins = listOf(waiting, declined)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomePickerTestTags.pendingJoin("maple")).assertIsDisplayed()
        onNodeWithTag(HomePickerTestTags.pendingJoin("elm")).assertIsDisplayed()
        onNodeWithText("Maple Street").assertIsDisplayed()
        onNodeWithText("Elm Court").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsPendingJoinsEvenWithNoHomesYet() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomePickerScreen(homes = emptyList(), pendingJoins = listOf(waiting)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomePickerTestTags.pendingJoin("maple")).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cancellingAPendingJoinPassesItOn() = runComposeUiTest {
        // Arrange
        var cancelled: PendingJoin? = null
        setContent { VestaTheme { HomePickerScreen(homes = homes, pendingJoins = listOf(waiting), onCancelPendingJoin = { cancelled = it }) } }

        // Act
        onNodeWithTag(HomePickerTestTags.pendingJoinCancel("maple")).performClick()

        // Assert
        assertEquals(waiting, cancelled)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dismissingARefusedJoinPassesItOn() = runComposeUiTest {
        // Arrange
        var dismissed: PendingJoin? = null
        setContent { VestaTheme { HomePickerScreen(homes = homes, pendingJoins = listOf(declined), onCancelPendingJoin = { dismissed = it }) } }

        // Act
        onNodeWithTag(HomePickerTestTags.pendingJoinCancel("elm")).performClick()

        // Assert
        assertEquals(declined, dismissed)
    }
}
