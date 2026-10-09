package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarSettings
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.screens.CalendarSettingsScreen
import uk.veritaslabs.vesta.ui.screens.CalendarSettingsTestTags
import uk.veritaslabs.vesta.ui.screens.CalendarScreen
import uk.veritaslabs.vesta.ui.screens.CalendarTestTags
import uk.veritaslabs.vesta.ui.screens.HomeDashboardScreen
import uk.veritaslabs.vesta.ui.screens.HomeDashboardTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for the Calendar (docs/adr/ADR-025-calendar.md; see docs/testing/acceptance-tests.md). The criteria:
 *
 * 1. The Home dashboard has a Calendar tile that opens the calendar, and back returns to the dashboard.
 * 2. The app bar shows the month over the year, and the calendar opens on today's.
 * 3. Tapping the month and year opens a dialog where I choose another month and year.
 * 4. A square month grid sits at the top and can be collapsed to one week and expanded again.
 * 5. An overflow menu opens Settings, where the week start (Monday, Saturday, Sunday), week numbers, days of other months
 *    and opening collapsed can be set, and the calendar follows them.
 * 6. Collapsed, previous / next (and swiping) step a week at a time; expanded, a month.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarAcceptanceTest {

    private val today = CalendarDate(2026, 10, 9)

    @Test
    fun theDashboardCalendarTileOpensTheCalendarAndBackReturns() = runComposeUiTest {
        // Arrange — Given I am on the Home dashboard
        setContent {
            VestaTheme {
                var open by remember { mutableStateOf(false) }
                if (open) CalendarScreen(today = today, onBack = { open = false })
                else HomeDashboardScreen(homeName = "The Johnsons", onCalendarClick = { open = true })
            }
        }

        // Act — When I tap the Calendar tile, then go back
        onNodeWithTag(HomeDashboardTestTags.CalendarTile).performClick()
        onNodeWithTag(CalendarTestTags.Month, useUnmergedTree = true).assertTextEquals("October")
        onNodeWithTag(CalendarTestTags.BackButton).performClick()

        // Assert — Then I see the dashboard again
        onNodeWithTag(HomeDashboardTestTags.CalendarTile).assertIsDisplayed()
    }

    @Test
    fun jumpingToAnotherMonthFromTheTitleShowsThatMonthStackedOverItsYear() = runComposeUiTest {
        // Arrange — Given the calendar is open on October 2026
        setContent { VestaTheme { CalendarScreen(today = today) } }

        // Act — When I tap the month and year, go back a year and choose February
        onNodeWithTag(CalendarTestTags.Title).performClick()
        onNodeWithTag(CalendarTestTags.PickerPreviousYear).performClick()
        onNodeWithTag(CalendarTestTags.pickerMonth(2)).performClick()

        // Assert — Then the app bar reads February over 2025 and the grid shows that month
        onNodeWithTag(CalendarTestTags.Month, useUnmergedTree = true).assertTextEquals("February")
        onNodeWithTag(CalendarTestTags.Year, useUnmergedTree = true).assertTextEquals("2025")
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2025, 2, 28))).assertIsDisplayed()
    }

    @Test
    fun collapsingAndExpandingTheMonthGrid() = runComposeUiTest {
        // Arrange — Given the month grid is showing the whole month
        setContent { VestaTheme { CalendarScreen(today = today) } }
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 1))).assertIsDisplayed()

        // Act — When I collapse it, then expand it again
        onNodeWithTag(CalendarTestTags.ToggleButton).performClick()
        waitForIdle()
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 1))).assertDoesNotExist()
        onNodeWithTag(CalendarTestTags.ToggleButton).performClick()
        waitForIdle()

        // Assert — Then the whole month is back
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 1))).assertIsDisplayed()
    }

    @Test
    fun changingTheWeekStartInSettingsReordersTheCalendar() = runComposeUiTest {
        // Arrange — Given the calendar is open and the week starts on Monday
        setContent {
            VestaTheme {
                var settings by remember { mutableStateOf(CalendarSettings()) }
                var inSettings by remember { mutableStateOf(false) }
                if (inSettings) CalendarSettingsScreen(settings, onChange = { settings = it }, onBack = { inSettings = false })
                else CalendarScreen(today = today, settings = settings, onSettingsClick = { inSettings = true })
            }
        }
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 9, 28))).assertIsDisplayed()

        // Act — When I open Settings from the overflow menu, choose Sunday and go back
        onNodeWithTag(CalendarTestTags.OverflowButton).performClick()
        onNodeWithTag(CalendarTestTags.SettingsMenuItem).performClick()
        onNodeWithTag(CalendarSettingsTestTags.weekStart(WeekStart.Sunday)).performClick()
        onNodeWithTag(CalendarSettingsTestTags.WeekNumbers).performClick()
        onNodeWithTag(CalendarSettingsTestTags.BackButton).performClick()

        // Assert — Then the first column is Sunday 27 September and the week numbers show
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 9, 27))).assertIsDisplayed()
        onNodeWithTag(CalendarTestTags.weekNumber(0)).assertTextEquals("w40")
    }

    @Test
    fun withTheMonthCollapsedIStepToTheNextAndPreviousWeek() = runComposeUiTest {
        // Arrange — Given the calendar is collapsed to the week of Friday 9 October
        setContent { VestaTheme { CalendarScreen(today = today) } }
        onNodeWithTag(CalendarTestTags.ToggleButton).performClick()
        waitForIdle()

        // Act — When I tap next twice and previous once
        onNodeWithTag(CalendarTestTags.NextButton).performClick()
        onNodeWithTag(CalendarTestTags.NextButton).performClick()
        onNodeWithTag(CalendarTestTags.PreviousButton).performClick()
        waitForIdle()

        // Assert — Then the selected day is a week on, and its week is the one showing
        onNodeWithTag(CalendarTestTags.SelectedDay).assertTextEquals("16 October 2026")
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 12))).assertIsDisplayed()
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 5))).assertDoesNotExist()
    }
}
