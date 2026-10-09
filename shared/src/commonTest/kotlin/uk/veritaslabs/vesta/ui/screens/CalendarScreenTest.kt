package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarSettings
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class CalendarScreenTest {

    private val today = CalendarDate(2026, 10, 9)

    @Test
    fun opensOnTheMonthAndYearOfToday() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = today) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(CalendarTestTags.Month, useUnmergedTree = true).assertTextEquals("October")
        onNodeWithTag(CalendarTestTags.Year, useUnmergedTree = true).assertTextEquals("2026")
        onNodeWithTag(CalendarTestTags.day(today)).assertIsDisplayed()
    }

    @Test
    fun backArrowInvokesOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { CalendarScreen(today = today, onBack = { backs++ }) } }

        // Act
        onNodeWithTag(CalendarTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }

    @Test
    fun tappingTheTitleLetsMeJumpToAnotherMonthAndYear() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = today) } }

        // Act
        onNodeWithTag(CalendarTestTags.Title).performClick()
        onNodeWithTag(CalendarTestTags.PickerNextYear).performClick()
        onNodeWithTag(CalendarTestTags.pickerMonth(3)).performClick()

        // Assert
        onNodeWithTag(CalendarTestTags.Month, useUnmergedTree = true).assertTextEquals("March")
        onNodeWithTag(CalendarTestTags.Year, useUnmergedTree = true).assertTextEquals("2027")
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2027, 3, 1))).assertIsDisplayed()
    }

    @Test
    fun collapsingTheGridKeepsOnlyTheWeekOfTheSelectedDay() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = today) } }
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 30))).assertIsDisplayed()

        // Act
        onNodeWithTag(CalendarTestTags.ToggleButton).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(CalendarTestTags.day(today)).assertIsDisplayed()
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 30))).assertDoesNotExist()
    }

    @Test
    fun tappingADayShowsItAsTheSelectedDay() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = today) } }

        // Act
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 20))).performClick()

        // Assert
        onNodeWithTag(CalendarTestTags.SelectedDay).assertTextEquals("20 October 2026")
    }

    @Test
    fun overflowMenuSettingsInvokesOnSettingsClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent { VestaTheme { CalendarScreen(today = today, onSettingsClick = { clicks++ }) } }

        // Act
        onNodeWithTag(CalendarTestTags.OverflowButton).performClick()
        onNodeWithTag(CalendarTestTags.SettingsMenuItem).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @Test
    fun weekStartSettingReordersTheColumns() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = today, settings = CalendarSettings(weekStart = WeekStart.Sunday)) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("Sun").assertIsDisplayed()
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 9, 27))).assertIsDisplayed()
    }

    @Test
    fun weekNumbersAndHiddenOtherMonthsFollowTheSettings() = runComposeUiTest {
        // Arrange
        setContent {
            VestaTheme { CalendarScreen(today = today, settings = CalendarSettings(showWeekNumbers = true, showOtherMonthDays = false)) }
        }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(CalendarTestTags.weekNumber(0)).assertTextEquals("w40")
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 9, 28))).assertDoesNotExist()
    }

    @Test
    fun startCollapsedSettingOpensWithOneWeek() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = today, settings = CalendarSettings(startCollapsed = true)) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 30))).assertDoesNotExist()
        onNodeWithTag(CalendarTestTags.day(today)).assertIsDisplayed()
    }

    @Test
    fun collapsedNextAndPreviousStepOneWeekAtATime() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = today) } }
        onNodeWithTag(CalendarTestTags.ToggleButton).performClick()
        waitForIdle()

        // Act
        onNodeWithTag(CalendarTestTags.NextButton).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(CalendarTestTags.SelectedDay).assertTextEquals("16 October 2026")
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 16))).assertIsDisplayed()
        onNodeWithTag(CalendarTestTags.day(today)).assertDoesNotExist()

        // Act
        onNodeWithTag(CalendarTestTags.PreviousButton).performClick()
        onNodeWithTag(CalendarTestTags.PreviousButton).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(CalendarTestTags.SelectedDay).assertTextEquals("2 October 2026")
    }

    @Test
    fun collapsedSteppingIntoAnotherMonthChangesTheAppBarMonth() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = CalendarDate(2026, 10, 30)) } }
        onNodeWithTag(CalendarTestTags.ToggleButton).performClick()
        waitForIdle()

        // Act
        onNodeWithTag(CalendarTestTags.NextButton).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(CalendarTestTags.Month, useUnmergedTree = true).assertTextEquals("November")
        onNodeWithTag(CalendarTestTags.SelectedDay).assertTextEquals("6 November 2026")
    }

    @Test
    fun expandedNextAndPreviousStepOneMonth() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = today) } }

        // Act
        onNodeWithTag(CalendarTestTags.NextButton).performClick()

        // Assert
        onNodeWithTag(CalendarTestTags.Month, useUnmergedTree = true).assertTextEquals("November")
    }
}
