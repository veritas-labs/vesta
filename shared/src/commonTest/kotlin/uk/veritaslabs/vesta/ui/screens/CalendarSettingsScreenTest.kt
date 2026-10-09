package uk.veritaslabs.vesta.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.calendar.CalendarSettings
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class CalendarSettingsScreenTest {

    @Test
    fun choosingAWeekStartReportsIt() = runComposeUiTest {
        // Arrange
        var latest by mutableStateOf(CalendarSettings())
        setContent { VestaTheme { CalendarSettingsScreen(latest, onChange = { latest = it }) } }

        // Act
        onNodeWithTag(CalendarSettingsTestTags.weekStart(WeekStart.Sunday)).performClick()

        // Assert
        assertEquals(WeekStart.Sunday, latest.weekStart)
    }

    @Test
    fun theSwitchesReportTheirChanges() = runComposeUiTest {
        // Arrange
        var latest by mutableStateOf(CalendarSettings())
        setContent { VestaTheme { CalendarSettingsScreen(latest, onChange = { latest = it }) } }

        // Act
        onNodeWithTag(CalendarSettingsTestTags.WeekNumbers).performClick()
        onNodeWithTag(CalendarSettingsTestTags.OtherMonths).performClick()
        onNodeWithTag(CalendarSettingsTestTags.StartCollapsed).performClick()

        // Assert
        assertEquals(CalendarSettings(showWeekNumbers = true, showOtherMonthDays = false, startCollapsed = true), latest)
    }

    @Test
    fun backInvokesOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { CalendarSettingsScreen(CalendarSettings(), onChange = {}, onBack = { backs++ }) } }

        // Act
        onNodeWithTag(CalendarSettingsTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }
}
