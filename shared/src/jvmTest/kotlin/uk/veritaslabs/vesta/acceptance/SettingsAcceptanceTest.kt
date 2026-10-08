package uk.veritaslabs.vesta.acceptance

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.AppInfo
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.settings.AppSettingsRepository
import uk.veritaslabs.vesta.settings.Appearance
import uk.veritaslabs.vesta.ui.screens.SettingsScreen
import uk.veritaslabs.vesta.ui.screens.SettingsTestTags
import uk.veritaslabs.vesta.ui.theme.AppTheme
import uk.veritaslabs.vesta.ui.theme.ThemeMode
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xConnectivity

/**
 * Acceptance tests for Settings (see docs/testing/acceptance-tests.md). Acceptance criteria:
 *
 * 1. The screen shows the app version and build code, and the platform the device runs on.
 * 2. Appearance: the user picks Light, Dark or System; the app is drawn light or dark accordingly, and System follows the
 *    operating system. The choice starts as System.
 * 3. Appearance: a Theme drop-down lists the available themes ("Vesta" and "Halloween") and Vesta is the one chosen at first.
 * 4. Network: the x0x status says whether the device is connected, connecting or offline, and tapping it opens
 *    network diagnostics; the Peers row (with the count) appears only when connected, and opens the peer graph.
 * 5. Network: "Use mobile data" starts on and the user can turn it off; it is greyed out and untouchable on a device with
 *    no mobile data.
 * 6. Profile: with a profile the user sees their name and can tap it to change it; without one there is no Profile section.
 * 7. The user can open Paired devices and Open source licences from Settings, and go back from Settings.
 *
 * 8. The theme, the Light / Dark / System choice and the "Use mobile data" choice are remembered, so the app comes back the
 *    way the user left it.
 *
 * Driven like a user over the real [SettingsScreen] and [VestaTheme]; the harness holds the state and navigation that
 * `App.kt` holds (it shares [ThemeMode.isDark] with it), and a destination screen is just the text naming where the tap went.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsAcceptanceTest {

    private fun ComposeUiTest.showSettings(
        systemDark: Boolean = false,
        mobileDataAvailable: Boolean = true,
        x0xConnectivity: X0xConnectivity = X0xConnectivity.Connecting,
        profileName: String? = null,
        platformName: String = "Android 15",
        appInfo: AppInfo = AppInfo("1.0", "1"),
        settings: AppSettingsRepository? = null,
    ) {
        val saved = settings?.let { runBlocking { it.appearance() } } ?: Appearance()
        val savedMobileData = settings?.let { runBlocking { it.useMobileData() } } ?: true
        setContent {
            var themeMode by remember { mutableStateOf(saved.themeMode) }
            var appTheme by remember { mutableStateOf(saved.appTheme) }
            var useMobileData by remember { mutableStateOf(savedMobileData) }
            var destination by remember { mutableStateOf<String?>(null) }
            VestaTheme(darkTheme = themeMode.isDark(systemDark)) {
                // Stands in for "what the app looks like": the same background every screen is drawn on.
                val drawnDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Text(if (drawnDark) "Drawn dark" else "Drawn light")
                destination?.let { Text("Went to $it") } ?: SettingsScreen(
                    platformName = platformName,
                    appInfo = appInfo,
                    x0xConnectivity = x0xConnectivity,
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        settings?.let { repository -> runBlocking { repository.setThemeMode(it) } }
                    },
                    appTheme = appTheme,
                    onAppThemeChange = {
                        appTheme = it
                        settings?.let { repository -> runBlocking { repository.setAppTheme(it) } }
                    },
                    useMobileData = useMobileData,
                    onUseMobileDataChange = {
                        useMobileData = it
                        settings?.let { repository -> runBlocking { repository.setUseMobileData(it) } }
                    },
                    mobileDataAvailable = mobileDataAvailable,
                    onSyncedDevicesClick = { destination = "Paired devices" },
                    onNetworkStatusClick = { destination = "Network diagnostics" },
                    onPeersClick = { destination = "Peers" },
                    onOpenSourceLicensesClick = { destination = "Open source licences" },
                    profileName = profileName,
                    onProfileNameClick = { destination = "Change name" },
                    onBack = { destination = "Back" },
                )
            }
        }
    }

    private fun ComposeUiTest.tap(tag: String) = onNodeWithTag(tag).performClick()

    private fun ComposeUiTest.assertShown(vararg texts: String) = texts.forEach { onNodeWithText(it).assertIsDisplayed() }

    @Test
    fun theUserSeesTheVersionBuildCodeAndPlatform() = runComposeUiTest {
        // Arrange — Given a device running Android 15 with version 2.3.1, build 457
        showSettings(platformName = "Android 15", appInfo = AppInfo("2.3.1", "457"))

        // Act — When the user opens Settings
        // (nothing to do: Settings is what is showing)

        // Assert — Then the version, build code and platform are on screen
        assertShown("2.3.1", "457", "Android 15")
    }

    @Test
    fun settingsStartsOnSystemAndFollowsTheOperatingSystem() = runComposeUiTest {
        // Arrange — Given a phone whose system theme is dark, and a user who has not chosen a mode
        showSettings(systemDark = true)

        // Act — When the user opens Settings
        // (nothing to do)

        // Assert — Then System is selected and the app is drawn dark
        onNodeWithTag(SettingsTestTags.ThemeModeSystem).assertIsSelected()
        onNodeWithText("Drawn dark").assertIsDisplayed()
    }

    @Test
    fun choosingDarkDrawsTheAppDarkEvenWhenTheSystemIsLight() = runComposeUiTest {
        // Arrange — Given a phone whose system theme is light
        showSettings(systemDark = false)
        onNodeWithText("Drawn light").assertIsDisplayed()

        // Act — When the user chooses Dark
        tap(SettingsTestTags.ThemeModeDark)

        // Assert — Then the app is drawn dark and only Dark is selected
        onNodeWithText("Drawn dark").assertIsDisplayed()
        onNodeWithTag(SettingsTestTags.ThemeModeDark).assertIsSelected()
        onNodeWithTag(SettingsTestTags.ThemeModeLight).assertIsNotSelected()
        onNodeWithTag(SettingsTestTags.ThemeModeSystem).assertIsNotSelected()
    }

    @Test
    fun choosingLightDrawsTheAppLightEvenWhenTheSystemIsDark() = runComposeUiTest {
        // Arrange — Given a phone whose system theme is dark
        showSettings(systemDark = true)

        // Act — When the user chooses Light
        tap(SettingsTestTags.ThemeModeLight)

        // Assert — Then the app is drawn light
        onNodeWithText("Drawn light").assertIsDisplayed()
        onNodeWithTag(SettingsTestTags.ThemeModeLight).assertIsSelected()
    }

    @Test
    fun choosingSystemAgainHandsTheChoiceBackToTheOperatingSystem() = runComposeUiTest {
        // Arrange — Given a phone whose system theme is dark, where the user has forced Light
        showSettings(systemDark = true)
        tap(SettingsTestTags.ThemeModeLight)

        // Act — When the user chooses System
        tap(SettingsTestTags.ThemeModeSystem)

        // Assert — Then the app is drawn dark again, as the system is
        onNodeWithText("Drawn dark").assertIsDisplayed()
    }

    @Test
    fun theThemeDropdownOffersVestaAndVestaIsChosen() = runComposeUiTest {
        // Arrange — Given Settings is open
        showSettings()
        onNodeWithTag(SettingsTestTags.ThemeRow).assertIsDisplayed()
        onNodeWithText("Vesta").assertIsDisplayed()

        // Act — When the user opens the Theme drop-down
        tap(SettingsTestTags.ThemeRow)

        // Assert — Then Vesta is offered as the only theme, and choosing it closes the menu
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Vesta").fetchSemanticsNodes().size == 2 }
        onAllNodesWithText("Vesta").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Vesta").fetchSemanticsNodes().size == 1 }
    }

    @Test
    fun theStatusRowSaysWhetherTheDeviceIsConnectedAndOpensDiagnostics() = runComposeUiTest {
        // Arrange — Given a device connected to the x0x network
        showSettings(x0xConnectivity = X0xConnectivity.Connected(peers = 4))
        assertShown("Connected")

        // Act — When the user taps the x0x status row
        tap(SettingsTestTags.NetworkStatusRow)

        // Assert — Then network diagnostics open
        onNodeWithText("Went to Network diagnostics").assertIsDisplayed()
    }

    @Test
    fun theStatusRowSaysConnectingAndOffline() = runComposeUiTest {
        // Arrange — Given a device still connecting to the x0x network
        showSettings(x0xConnectivity = X0xConnectivity.Connecting)

        // Act — When the user looks at the Network section
        // (nothing to do)

        // Assert — Then it says Connecting… and shows no peers row
        assertShown("Connecting…")
        onNodeWithTag(SettingsTestTags.NetworkPeersRow).assertDoesNotExist()
    }

    @Test
    fun theStatusRowSaysOfflineAndShowsNoPeers() = runComposeUiTest {
        // Arrange — Given a device that cannot reach the x0x network
        showSettings(x0xConnectivity = X0xConnectivity.Offline)

        // Act — When the user looks at the Network section
        // (nothing to do)

        // Assert — Then it says Offline and shows no peers row
        assertShown("Offline")
        onNodeWithTag(SettingsTestTags.NetworkPeersRow).assertDoesNotExist()
    }

    @Test
    fun whenConnectedThePeersRowShowsTheCountAndOpensThePeers() = runComposeUiTest {
        // Arrange — Given a device connected to 23 peers
        showSettings(x0xConnectivity = X0xConnectivity.Connected(peers = 23))
        assertShown("23")

        // Act — When the user taps the Peers row
        tap(SettingsTestTags.NetworkPeersRow)

        // Assert — Then the peers screen opens
        onNodeWithText("Went to Peers").assertIsDisplayed()
    }

    @Test
    fun mobileDataStartsOnAndTheUserCanTurnItOffAndOnAgain() = runComposeUiTest {
        // Arrange — Given a phone that has mobile data
        showSettings(mobileDataAvailable = true)
        onNodeWithTag(SettingsTestTags.MobileDataRow).assertIsOn()

        // Act — When the user taps Use mobile data
        tap(SettingsTestTags.MobileDataRow)

        // Assert — Then it is off, and tapping again turns it back on
        onNodeWithTag(SettingsTestTags.MobileDataRow).assertIsOff()
        tap(SettingsTestTags.MobileDataRow)
        onNodeWithTag(SettingsTestTags.MobileDataRow).assertIsOn()
    }

    @Test
    fun mobileDataCannotBeChangedOnADeviceWithoutIt() = runComposeUiTest {
        // Arrange — Given a desktop with no mobile data
        showSettings(mobileDataAvailable = false)

        // Act — When the user taps Use mobile data
        tap(SettingsTestTags.MobileDataRow)

        // Assert — Then the row is disabled and still on
        onNodeWithTag(SettingsTestTags.MobileDataRow).assertIsNotEnabled()
        onNodeWithTag(SettingsTestTags.MobileDataRow).assertIsOn()
    }

    @Test
    fun theUserSeesTheirNameAndCanOpenItToChangeIt() = runComposeUiTest {
        // Arrange — Given a user whose profile name is Mark
        showSettings(profileName = "Mark")
        assertShown("Profile", "Mark")

        // Act — When the user taps their name
        tap(SettingsTestTags.YourNameRow)

        // Assert — Then the change-name screen opens
        onNodeWithText("Went to Change name").assertIsDisplayed()
    }

    @Test
    fun withoutAProfileThereIsNoProfileSection() = runComposeUiTest {
        // Arrange — Given no profile
        showSettings(profileName = null)

        // Act — When the user opens Settings
        // (nothing to do)

        // Assert — Then there is no Your name row
        onNodeWithTag(SettingsTestTags.YourNameRow).assertDoesNotExist()
    }

    @Test
    fun theUserCanOpenPairedDevices() = runComposeUiTest {
        // Arrange — Given Settings is open
        showSettings()

        // Act — When the user taps Paired devices
        tap(SettingsTestTags.SyncedDevicesRow)

        // Assert — Then the paired devices screen opens
        onNodeWithText("Went to Paired devices").assertIsDisplayed()
    }

    @Test
    fun theUserCanOpenTheOpenSourceLicences() = runComposeUiTest {
        // Arrange — Given Settings is open
        showSettings()

        // Act — When the user taps Open source licences
        tap(SettingsTestTags.OpenSourceLicensesRow)

        // Assert — Then the licences screen opens
        onNodeWithText("Went to Open source licences").assertIsDisplayed()
    }

    @Test
    fun theBackArrowLeavesSettings() = runComposeUiTest {
        // Arrange — Given Settings is open
        showSettings()

        // Act — When the user taps the back arrow
        tap(SettingsTestTags.BackButton)

        // Assert — Then Settings is left
        onNodeWithText("Went to Back").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theChosenThemeModeAndMobileDataSettingAreStillChosenAfterTheAppIsRelaunched() {
        // Arrange — Given a user who picks the Halloween theme and Dark mode and turns off mobile data in Settings
        val settings = AppSettingsRepository(openInMemoryDatabase())
        runComposeUiTest {
            showSettings(settings = settings)
            tap(SettingsTestTags.ThemeRow)
            onNodeWithText("Halloween").performClick()
            tap(SettingsTestTags.ThemeModeDark)
            tap(SettingsTestTags.MobileDataRow)
        }

        // Act — When the app is relaunched (a new Settings screen over the same saved settings)
        runComposeUiTest { showSettings(settings = settings)

            // Assert — Then Halloween is the theme shown, Dark is selected, the app is drawn dark and mobile data is off
            onNodeWithText("Halloween").assertIsDisplayed()
            onNodeWithTag(SettingsTestTags.ThemeModeDark).assertIsSelected()
            assertShown("Drawn dark")
            onNodeWithTag(SettingsTestTags.MobileDataRow).assertIsOff()
        }
    }
}
