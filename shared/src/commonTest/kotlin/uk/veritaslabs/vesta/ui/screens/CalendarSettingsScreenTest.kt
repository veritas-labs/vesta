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
import uk.veritaslabs.vesta.files.FileImporter
import uk.veritaslabs.vesta.files.LocalFileImporter
import uk.veritaslabs.vesta.files.PickedFile
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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

    private fun ics(vararg events: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events.joinToString("") {
        "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n"
    } + "END:VCALENDAR\r\n"

    private fun importer(text: String?) = FileImporter { text?.let { PickedFile("cal.ics", it.encodeToByteArray()) } }

    @Test
    fun importReadsTheChosenFileIntoTheHomeCalendarAndSaysHowManyEvents() = runComposeUiTest {
        // Arrange
        val imported = mutableListOf<Pair<EventCalendar, List<CalendarEvent>>>()
        val file = ics("UID:a\r\nDTSTART;VALUE=DATE:20261014\r\nSUMMARY:Bins", "UID:b\r\nDTSTART:20261015T090000Z\r\nSUMMARY:Dentist")
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalFileImporter provides importer(file)) {
                    CalendarSettingsScreen(CalendarSettings(), onChange = {}, onImport = { calendar, events -> imported += calendar to events })
                }
            }
        }

        // Act
        onNodeWithTag(CalendarSettingsTestTags.ImportButton).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(CalendarSettingsTestTags.ImportResult).assertTextContains("Imported 2 events.", substring = true)
        assertEquals(EventCalendar.Home, imported.single().first)
        assertEquals(listOf("Bins", "Dentist"), imported.single().second.map { it.title })
    }

    @Test
    fun theEventsCanBeImportedIntoThePrivateCalendarInstead() = runComposeUiTest {
        // Arrange
        val imported = mutableListOf<Pair<EventCalendar, List<CalendarEvent>>>()
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalFileImporter provides importer(ics("UID:a\r\nDTSTART;VALUE=DATE:20261014\r\nSUMMARY:Gym"))) {
                    CalendarSettingsScreen(CalendarSettings(), onChange = {}, onImport = { calendar, events -> imported += calendar to events })
                }
            }
        }

        // Act
        onNodeWithTag(CalendarSettingsTestTags.importInto(EventCalendar.Private)).performClick()
        onNodeWithTag(CalendarSettingsTestTags.ImportButton).performClick()
        waitForIdle()

        // Assert
        assertEquals(EventCalendar.Private, imported.single().first)
        assertEquals(EventCalendar.Private, imported.single().second.single().calendar)
        onNodeWithTag(CalendarSettingsTestTags.ImportResult).assertTextContains("Imported 1 event.", substring = true)
    }

    @Test
    fun aFileWithNoEventsSaysSoAndImportsNothing() = runComposeUiTest {
        // Arrange
        var calls = 0
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalFileImporter provides importer("not a calendar")) {
                    CalendarSettingsScreen(CalendarSettings(), onChange = {}, onImport = { _, _ -> calls++ })
                }
            }
        }

        // Act
        onNodeWithTag(CalendarSettingsTestTags.ImportButton).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(CalendarSettingsTestTags.ImportResult).assertTextContains("No events found", substring = true)
        assertEquals(0, calls)
    }

    @Test
    fun skippedEventsAndSimplifiedRepeatsAreReported() = runComposeUiTest {
        // Arrange
        val file = ics("SUMMARY:No start", "DTSTART:20261014T090000Z\r\nRRULE:FREQ=WEEKLY;COUNT=3\r\nSUMMARY:Bins")
        setContent {
            VestaTheme { CompositionLocalProvider(LocalFileImporter provides importer(file)) { CalendarSettingsScreen(CalendarSettings(), onChange = {}) } }
        }

        // Act
        onNodeWithTag(CalendarSettingsTestTags.ImportButton).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(CalendarSettingsTestTags.ImportResult).assertTextContains("1 could not be read", substring = true)
        onNodeWithTag(CalendarSettingsTestTags.ImportResult).assertTextContains("1 repeats were simplified", substring = true)
    }

    @Test
    fun backingOutOfTheFileChooserChangesNothing() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CompositionLocalProvider(LocalFileImporter provides importer(null)) { CalendarSettingsScreen(CalendarSettings(), onChange = {}) } } } 

        // Act
        onNodeWithTag(CalendarSettingsTestTags.ImportButton).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(CalendarSettingsTestTags.ImportResult).assertDoesNotExist()
        onNodeWithTag(CalendarSettingsTestTags.ImportSection).assertIsDisplayed()
    }

    @Test
    fun withNoImporterThereIsNoImportSection() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarSettingsScreen(CalendarSettings(), onChange = {}) } }

        // Act
        // (no user interaction)

        // Assert
        onNodeWithTag(CalendarSettingsTestTags.ImportSection).assertDoesNotExist()
    }
}
