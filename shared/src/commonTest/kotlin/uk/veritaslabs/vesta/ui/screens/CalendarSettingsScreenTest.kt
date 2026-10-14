package uk.veritaslabs.vesta.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.allDayMillis
import uk.veritaslabs.vesta.files.FileExporter
import uk.veritaslabs.vesta.files.LocalFileExporter
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.calendar.CalendarDate
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

    private val day = CalendarDate(2026, 10, 14)

    private fun event(id: String, title: String, calendar: EventCalendar) =
        CalendarEvent(id, calendar, title, true, allDayMillis(day), allDayMillis(day))

    private val events = listOf(
        event("h1", "Bins", EventCalendar.Home), event("h2", "Dentist", EventCalendar.Home), event("p1", "Gym", EventCalendar.Private),
    )

    private class Recorder : FileExporter {
        val files = mutableListOf<Triple<String, String, String>>()

        override suspend fun export(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
            files += Triple(fileName, mimeType, bytes.decodeToString())
            return true
        }
    }

    @Test
    fun exportOffersBothCalendarsTickedAndExportsEverything() = runComposeUiTest {
        // Arrange
        val recorder = Recorder()
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalFileExporter provides recorder) {
                    CalendarSettingsScreen(CalendarSettings(), onChange = {}, events = events, homeName = "Maple Street")
                }
            }
        }

        // Act
        onNodeWithTag(CalendarSettingsTestTags.ExportButton).performClick()
        waitForIdle()

        // Assert
        val (name, type, text) = recorder.files.single()
        assertEquals("Maple Street all events.ics", name)
        assertEquals("text/calendar", type)
        assertEquals(3, "BEGIN:VEVENT".toRegex().findAll(text).count())
    }

    @Test
    fun untickingThePrivateCalendarExportsOnlyTheHomes() = runComposeUiTest {
        // Arrange
        val recorder = Recorder()
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalFileExporter provides recorder) {
                    CalendarSettingsScreen(CalendarSettings(), onChange = {}, events = events, homeName = "Maple Street")
                }
            }
        }

        // Act
        onNodeWithTag(CalendarSettingsTestTags.exportCalendar(EventCalendar.Private)).performClick()
        onNodeWithTag(CalendarSettingsTestTags.ExportButton).performClick()
        waitForIdle()

        // Assert
        val (name, _, text) = recorder.files.single()
        assertEquals("Maple Street calendar.ics", name)
        assertTrue("SUMMARY:Bins" in text && "SUMMARY:Dentist" in text && "Gym" !in text)
    }

    @Test
    fun exportIsDisabledWhenNothingIsTickedOrTheTickedCalendarsAreEmpty() = runComposeUiTest {
        // Arrange
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalFileExporter provides Recorder()) {
                    CalendarSettingsScreen(CalendarSettings(), onChange = {}, events = events.filter { it.calendar == EventCalendar.Home })
                }
            }
        }
        onNodeWithTag(CalendarSettingsTestTags.ExportButton).assertIsEnabled()

        // Act
        onNodeWithTag(CalendarSettingsTestTags.exportCalendar(EventCalendar.Home)).performClick()

        // Assert: only the empty private calendar is still ticked
        onNodeWithTag(CalendarSettingsTestTags.ExportButton).assertIsNotEnabled()
    }

    @Test
    fun withNoExporterThereIsNoExportSection() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarSettingsScreen(CalendarSettings(), onChange = {}, events = events) } }

        // Act
        // (no user interaction)

        // Assert
        onNodeWithTag(CalendarSettingsTestTags.ExportSection).assertDoesNotExist()
    }
}
