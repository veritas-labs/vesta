package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.onboarding_home
import vesta.shared.generated.resources.tour_home_body
import vesta.shared.generated.resources.onboarding_home_dark
import vesta.shared.generated.resources.tour_home_title

class OnboardingScreenTest {

    private fun slide(id: String, enabled: Boolean) = OnboardingSlide(
        id = id,
        title = Res.string.tour_home_title,
        body = Res.string.tour_home_body,
        illustration = Res.drawable.onboarding_home,
        illustrationDark = Res.drawable.onboarding_home_dark,
        enabled = enabled,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun firstLaunchStartsOnTheFirstSlideWithNextAndNoWayToLeave() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { OnboardingScreen(OnboardingMode.FirstLaunch) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(OnboardingTestTags.Illustration).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.Title).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.Next).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.Previous).assertDoesNotExist()
        onNodeWithTag(OnboardingTestTags.Close).assertDoesNotExist()
        onNodeWithTag(OnboardingTestTags.Finish).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun firstLaunchHasNoCloseOnTheLastSlideEitherOnlyTheFinishButton() = runComposeUiTest {
        // Arrange
        var finished = 0
        setContent { VestaTheme { OnboardingScreen(OnboardingMode.FirstLaunch, initialPage = Int.MAX_VALUE, onFinish = { finished++ }) } }

        // Act
        onNodeWithTag(OnboardingTestTags.Finish).performClick()

        // Assert
        onNodeWithTag(OnboardingTestTags.Close).assertDoesNotExist()
        onNodeWithTag(OnboardingTestTags.Next).assertDoesNotExist()
        assertEquals(1, finished)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nextAndPreviousMoveBetweenSlides() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { OnboardingScreen(OnboardingMode.FirstLaunch) } }

        // Act
        onNodeWithTag(OnboardingTestTags.Next).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(OnboardingTestTags.Previous).assertIsDisplayed()
        onNodeWithContentDescription("Page 2 of ${OnboardingSlides.count { it.enabled }}").assertExists()

        // Act
        onNodeWithTag(OnboardingTestTags.Previous).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(OnboardingTestTags.Previous).assertDoesNotExist()
        onNodeWithContentDescription("Page 1 of ${OnboardingSlides.count { it.enabled }}").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun revisitShowsACloseButtonOnEverySlide() = runComposeUiTest {
        // Arrange
        var finished = 0
        setContent { VestaTheme { OnboardingScreen(OnboardingMode.Revisit, onFinish = { finished++ }) } }
        onNodeWithTag(OnboardingTestTags.Next).performClick()
        waitForIdle()

        // Act
        onNodeWithTag(OnboardingTestTags.Close).performClick()

        // Assert
        assertEquals(1, finished)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aSlideThatIsSwitchedOffIsLeftOutAndThePageCountFollows() = runComposeUiTest {
        // Arrange
        val slides = listOf(slide("one", enabled = true), slide("off", enabled = false), slide("done", enabled = true))

        // Act
        setContent { VestaTheme { OnboardingScreen(OnboardingMode.FirstLaunch, slides) } }

        // Assert
        onNodeWithContentDescription("Page 1 of 2").assertExists()
        onNodeWithTag(OnboardingTestTags.Next).performClick()
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.Finish).assertIsDisplayed()
    }

    @Test
    fun theAssistantsSlideIsOffUntilTheFeatureIsReadyAndTheTourEndsOnTheFinishingSlide() {
        // Arrange
        val shown = OnboardingSlides.filter { it.enabled }

        // Act
        val ids = shown.map { it.id }

        // Assert
        assertFalse("assistants" in ids)
        assertEquals("done", ids.last())
        assertTrue(ids.size >= 2)
    }
}
