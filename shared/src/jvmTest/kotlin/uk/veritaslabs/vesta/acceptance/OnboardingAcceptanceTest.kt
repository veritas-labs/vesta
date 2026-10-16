package uk.veritaslabs.vesta.acceptance

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.App
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.settings.AppSettingsRepository
import uk.veritaslabs.vesta.home.HomeListItem
import uk.veritaslabs.vesta.ui.screens.HomePickerTestTags
import uk.veritaslabs.vesta.ui.screens.OnboardingSlides
import uk.veritaslabs.vesta.ui.screens.OnboardingTestTags
import uk.veritaslabs.vesta.ui.screens.SettingsTestTags
import uk.veritaslabs.kits.x0x.X0xConnectivity

/**
 * Acceptance tests for the welcome tour (see docs/testing/acceptance-tests.md, docs/adr/ADR-030-onboarding-slideshow.md).
 * Acceptance criteria:
 *
 * 1. A new install shows the tour before anything else, and it cannot be skipped: the only way out is its last slide's button.
 * 2. Finishing the tour lands on the Home picker.
 * 3. The tour is shown once: a later launch over the same data goes straight to the Home picker.
 * 4. An install that already has a Home was here before the tour existed, so it never sees it.
 * 5. The tour can be opened again from Settings (reached from the Home picker once there is a Home), with a close button on every slide, and closing returns to Settings.
 *
 * Driven like a user over the real [App], with an in-memory database standing in for the device's.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class OnboardingAcceptanceTest {

    // The view models' scope is Dispatchers.Main; an unconfined one runs them on the test's own thread (see JoinHomeFromPickerTest).
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ComposeUiTest.launch(db: KitsDatabase, homes: Flow<List<HomeListItem>> = flowOf(emptyList())) = setContent {
        App(
            homes = homes,
            settings = AppSettingsRepository(db),
            profiles = ProfileRepository(db, deviceName = { "Test phone" }),
            x0xConnectivity = { X0xConnectivity.Connected(peers = 5) },
        )
    }

    private fun ComposeUiTest.finishTheTour() {
        waitUntilExactlyOneExists(hasTestTag(OnboardingTestTags.Next))
        repeat(OnboardingSlides.count { it.enabled } - 1) {
            onNodeWithTag(OnboardingTestTags.Next).performClick()
            waitForIdle()
        }
        onNodeWithTag(OnboardingTestTags.Finish).performClick()
    }

    @Test
    fun aNewInstallShowsTheTourFirstWithNoWayToSkipIt() = runComposeUiTest {
        // Arrange — Given a new install
        val db = openInMemoryDatabase()

        // Act — When the app opens
        launch(db)
        waitUntilExactlyOneExists(hasTestTag(OnboardingTestTags.Pager))

        // Assert — Then the tour is showing, with Next but no close button, and the Home picker is not
        onNodeWithTag(OnboardingTestTags.Next).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.Close).assertDoesNotExist()
        onNodeWithTag(HomePickerTestTags.CreateHomeButton).assertDoesNotExist()
    }

    @Test
    fun finishingTheTourLandsOnTheHomePicker() = runComposeUiTest {
        // Arrange — Given a new install showing the tour
        val db = openInMemoryDatabase()
        launch(db)

        // Act — When the user goes through every slide and presses the last button
        finishTheTour()

        // Assert — Then the Home picker is showing
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.CreateHomeButton))
        onNodeWithTag(HomePickerTestTags.CreateHomeButton).assertIsDisplayed()
    }

    @Test
    fun theTourIsShownOnlyOnceOverTheSameData() {
        // Arrange — Given a new install whose tour has been finished
        val db = openInMemoryDatabase()
        runComposeUiTest {
            launch(db)
            finishTheTour()
            waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.CreateHomeButton))
        }

        // Act — When the app is opened again over the same data
        runComposeUiTest {
            launch(db)

            // Assert — Then it goes straight to the Home picker
            waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.CreateHomeButton))
            onNodeWithTag(OnboardingTestTags.Pager).assertDoesNotExist()
        }
        assertEquals(true, runBlocking { AppSettingsRepository(db).onboardingSeen() })
    }

    @Test
    fun anInstallThatAlreadyHasAHomeNeverSeesTheTour() = runComposeUiTest {
        // Arrange — Given an install from before the tour, with a Home
        val db = openInMemoryDatabase()

        // Act — When the app opens
        launch(db, homes = flowOf(listOf(HomeListItem("home-1", "Maple Street"))))

        // Assert — Then the Home picker shows with no tour
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.homeItem("home-1")))
        onNodeWithTag(OnboardingTestTags.Pager).assertDoesNotExist()
        assertEquals(true, runBlocking { AppSettingsRepository(db).onboardingSeen() })
    }

    @Test
    fun theTourCanBeOpenedAgainFromSettingsAndClosedBackToSettings() = runComposeUiTest {
        // Arrange — Given a user with a Home who has finished the tour and opened Settings (the cog only shows once there is a Home)
        val db = openInMemoryDatabase()
        runBlocking { AppSettingsRepository(db).setOnboardingSeen(true) }
        launch(db, homes = flowOf(listOf(HomeListItem("home-1", "Maple Street"))))
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.SettingsButton))
        onNodeWithTag(HomePickerTestTags.SettingsButton).performClick()
        waitUntilExactlyOneExists(hasTestTag(SettingsTestTags.List))
        onNodeWithTag(SettingsTestTags.List).performScrollToNode(hasTestTag(SettingsTestTags.WelcomeTourRow))

        // Act — When they open the welcome tour
        onNodeWithTag(SettingsTestTags.WelcomeTourRow).performClick()

        // Assert — Then it shows with a close button, and closing it returns to Settings
        onNodeWithTag(OnboardingTestTags.Close).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.Close).performClick()
        waitUntilExactlyOneExists(hasTestTag(SettingsTestTags.List))
        onNodeWithTag(OnboardingTestTags.Pager).assertDoesNotExist()
    }
}
