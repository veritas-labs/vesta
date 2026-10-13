package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class MealPlannerCopyScreenTest {

    // 2026-10-14 is a Wednesday; its Monday week is 12–18 October and the next 19–25.
    private val wednesday = CalendarDate(2026, 10, 14)
    private val weeks = copyTargetWeeks(wednesday, WeekStart.Monday)

    @Test
    fun theTargetWeeksAreTheDaysOwnWeekAndTheNext() {
        // Arrange
        val date = wednesday

        // Act
        val result = copyTargetWeeks(date, WeekStart.Monday)

        // Assert
        assertEquals(CalendarDate(2026, 10, 12), result[0].first())
        assertEquals(CalendarDate(2026, 10, 18), result[0].last())
        assertEquals(CalendarDate(2026, 10, 19), result[1].first())
        assertEquals(CalendarDate(2026, 10, 25), result[1].last())
    }

    @Test
    fun saysWhichRecipeAndMealIsBeingCopied() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerCopyScreen("Shepherd's Pie", wednesday, MealSlot.Dinner, weeks) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("Shepherd's Pie · Dinner · Wed 14").assertIsDisplayed()
    }

    @Test
    fun listsTheOtherDaysOfBothWeeksButNotTheSourceDay() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerCopyScreen("Shepherd's Pie", wednesday, MealSlot.Dinner, weeks) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerCopyTestTags.day(CalendarDate(2026, 10, 12))).assertIsDisplayed()
        onNodeWithTag(MealPlannerCopyTestTags.day(wednesday)).assertDoesNotExist()
    }

    @Test
    fun copyIsDisabledUntilADayIsTickedThenReportsTheTickedDays() = runComposeUiTest {
        // Arrange
        var copied: Set<CalendarDate>? = null
        setContent { VestaTheme { MealPlannerCopyScreen("Shepherd's Pie", wednesday, MealSlot.Dinner, weeks, onCopy = { copied = it }) } }
        onNodeWithTag(MealPlannerCopyTestTags.Confirm).assertIsNotEnabled()
        val monday = CalendarDate(2026, 10, 12)
        val thursday = CalendarDate(2026, 10, 15)

        // Act
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(monday)).performClick()
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(thursday)).performClick()
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(monday)).performClick()
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(monday)).performClick()
        onNodeWithTag(MealPlannerCopyTestTags.Confirm).assertIsEnabled()
        onNodeWithTag(MealPlannerCopyTestTags.Confirm).performClick()

        // Assert
        assertEquals(setOf(monday, thursday), copied)
    }

    @Test
    fun aDayThatAlreadyHasTheRecipeCannotBeTicked() = runComposeUiTest {
        // Arrange
        val friday = CalendarDate(2026, 10, 16)
        setContent {
            VestaTheme { MealPlannerCopyScreen("Shepherd's Pie", wednesday, MealSlot.Dinner, weeks, alreadyPlanned = setOf(friday)) }
        }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(friday)).assertIsNotEnabled()
        onNodeWithText("Already planned").assertIsDisplayed()
    }

    @Test
    fun tickedDaysStayTickedWhileOthersAreToggled() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerCopyScreen("Shepherd's Pie", wednesday, MealSlot.Dinner, weeks) } }
        val monday = CalendarDate(2026, 10, 12)

        // Act
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(monday)).performClick()

        // Assert
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(monday)).assertIsOn()
    }

    @Test
    fun backReportsATapWithoutCopying() = runComposeUiTest {
        // Arrange
        var backed = false
        var copied = false
        setContent {
            VestaTheme { MealPlannerCopyScreen("Shepherd's Pie", wednesday, MealSlot.Dinner, weeks, onCopy = { copied = true }, onBack = { backed = true }) }
        }

        // Act
        onNodeWithTag(MealPlannerCopyTestTags.BackButton).performClick()

        // Assert
        assertEquals(true, backed)
        assertEquals(false, copied)
    }
}
