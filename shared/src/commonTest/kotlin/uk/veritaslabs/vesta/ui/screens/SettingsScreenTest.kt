package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.AppInfo
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xConnectivity

class SettingsScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsThePlatformName() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsScreen(platformName = "Android 15") } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Android 15").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheAppVersionAndBuildCode() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsScreen(appInfo = AppInfo(version = "2.3.1", buildCode = "457")) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("2.3.1").assertIsDisplayed()
        onNodeWithText("457").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scrollsToTheLastRowWhenTheScreenIsShort() = runComposeUiTest {
        // Arrange
        setContent {
            VestaTheme {
                Box(Modifier.height(240.dp)) { SettingsScreen(x0xConnectivity = X0xConnectivity.Connected(peers = 23)) }
            }
        }

        // Act
        onNodeWithTag(SettingsTestTags.List).performScrollToNode(hasTestTag(SettingsTestTags.NetworkPeersRow))

        // Assert
        onNodeWithTag(SettingsTestTags.NetworkPeersRow).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsPeerCountWhenConnected() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsScreen(x0xConnectivity = X0xConnectivity.Connected(peers = 23)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Connected").assertIsDisplayed()
        onNodeWithTag(SettingsTestTags.NetworkPeersRow).assertIsDisplayed()
        onNodeWithText("23").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hidesPeerCountWhenOffline() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsScreen(x0xConnectivity = X0xConnectivity.Offline) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Offline").assertIsDisplayed()
        onNodeWithTag(SettingsTestTags.NetworkPeersRow).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun mobileDataSwitchIsOffByDefault() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsScreen() } }

        // Act
        onNodeWithTag(SettingsTestTags.List).performScrollToNode(hasTestTag(SettingsTestTags.MobileDataRow))

        // Assert
        onNodeWithTag(SettingsTestTags.MobileDataRow).assertIsOff()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun togglingMobileDataReportsTheNewValue() = runComposeUiTest {
        // Arrange
        var requested: Boolean? = null
        setContent { VestaTheme { SettingsScreen(useMobileData = false, onUseMobileDataChange = { requested = it }) } }
        onNodeWithTag(SettingsTestTags.List).performScrollToNode(hasTestTag(SettingsTestTags.MobileDataRow))

        // Act
        onNodeWithTag(SettingsTestTags.MobileDataRow).performClick()

        // Assert
        assertEquals(true, requested)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun darkThemeSwitchReflectsTheCurrentSetting() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsScreen(darkTheme = true) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SettingsTestTags.DarkThemeRow).assertIsOn()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun togglingDarkThemeReportsTheNewValue() = runComposeUiTest {
        // Arrange
        var requested: Boolean? = null
        setContent { VestaTheme { SettingsScreen(darkTheme = false, onDarkThemeChange = { requested = it }) } }

        // Act
        onNodeWithTag(SettingsTestTags.DarkThemeRow).performClick()

        // Assert
        assertEquals(true, requested)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingSyncedDevicesInvokesOnSyncedDevicesClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent { VestaTheme { SettingsScreen(onSyncedDevicesClick = { clicks++ }) } }

        // Act
        onNodeWithTag(SettingsTestTags.SyncedDevicesRow).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingBackInvokesOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { SettingsScreen(onBack = { backs++ }) } }

        // Act
        onNodeWithTag(SettingsTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheUsersNameAndOpensItForChanging() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent { VestaTheme { SettingsScreen(profileName = "Mark", onProfileNameClick = { clicks++ }) } }

        // Act
        onNodeWithText("Mark").assertIsDisplayed()
        onNodeWithTag(SettingsTestTags.YourNameRow).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hidesTheProfileSectionWithoutAProfile() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsScreen() } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SettingsTestTags.YourNameRow).assertDoesNotExist()
    }
}
