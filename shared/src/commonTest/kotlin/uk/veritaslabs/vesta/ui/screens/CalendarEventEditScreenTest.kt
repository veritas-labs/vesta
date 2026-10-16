package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.spaces.MemberRole
import uk.veritaslabs.kits.spaces.MemberListItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import uk.veritaslabs.vesta.files.FileExporter
import uk.veritaslabs.vesta.files.LocalFileExporter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.allDayMillis
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class CalendarEventEditScreenTest {

    private val day = CalendarDate(2026, 10, 14)
    private val members = listOf(MemberListItem("a", "Alex", MemberRole.Admin, isYou = true), MemberListItem("b", "Sam", MemberRole.Member))

    @Test
    fun saveStaysDisabledUntilTheEventHasATitle() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarEventEditScreen(event = null, date = day) } }
        onNodeWithTag(EventEditTestTags.SaveButton).assertIsNotEnabled()

        // Act
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput("Dentist")

        // Assert
        onNodeWithTag(EventEditTestTags.SaveButton).assertIsEnabled()
    }

    @Test
    fun aNewEventStartsAtNineOnTheGivenDayForHalfAnHour() = runComposeUiTest {
        // Arrange
        var saved: CalendarEvent? = null
        setContent { VestaTheme { CalendarEventEditScreen(event = null, date = day, newId = { "new" }, onSave = { saved = it }) } }
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput("Dentist")

        // Act
        onNodeWithTag(EventEditTestTags.StartTime).assertTextContains("09:00")
        onNodeWithTag(EventEditTestTags.EndTime).assertTextContains("09:30")
        onNodeWithTag(EventEditTestTags.SaveButton).performClick()

        // Assert
        val event = saved!!
        assertEquals("new", event.id)
        assertEquals(EventCalendar.Home, event.calendar)
        assertEquals(30 * 60_000L, event.endMillis - event.startMillis)
        assertEquals(EventRepeat.None, event.repeat)
    }

    @Test
    fun allDayHidesTheTimesAndSavesDays() = runComposeUiTest {
        // Arrange
        var saved: CalendarEvent? = null
        setContent { VestaTheme { CalendarEventEditScreen(event = null, date = day, onSave = { saved = it }) } }
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput("Holiday")

        // Act
        onNodeWithTag(EventEditTestTags.AllDay).performClick()
        onNodeWithTag(EventEditTestTags.SaveButton).performClick()

        // Assert
        onNodeWithTag(EventEditTestTags.StartTime).assertDoesNotExist()
        assertTrue(saved!!.allDay)
        assertEquals(allDayMillis(day), saved!!.startMillis)
    }

    @Test
    fun theRepeatMenuChoosesHowOftenItComesRound() = runComposeUiTest {
        // Arrange
        var saved: CalendarEvent? = null
        setContent { VestaTheme { CalendarEventEditScreen(event = null, date = day, onSave = { saved = it }) } }
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput("Bins")

        // Act
        onNodeWithTag(EventEditTestTags.Repeat).performClick()
        onNodeWithTag(EventEditTestTags.repeatOption(EventRepeat.Weekly)).performClick()
        onNodeWithTag(EventEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(EventRepeat.Weekly, saved!!.repeat)
    }

    @Test
    fun theColourMenuChoosesTheEventsColour() = runComposeUiTest {
        // Arrange
        var saved: CalendarEvent? = null
        setContent { VestaTheme { CalendarEventEditScreen(event = null, date = day, onSave = { saved = it }) } }
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput("Bins")

        // Act
        onNodeWithTag(EventEditTestTags.ColourField).performClick()
        onNodeWithTag(EventEditTestTags.colourOption(3)).performClick()
        onNodeWithTag(EventEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(3, saved!!.color)
    }

    @Test
    fun choosingThePrivateCalendarHidesParticipantsAndExplainsWhoSeesIt() = runComposeUiTest {
        // Arrange
        var saved: CalendarEvent? = null
        setContent { VestaTheme { CalendarEventEditScreen(event = null, date = day, members = members, onSave = { saved = it }) } }
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput("Gym")
        onNodeWithTag(EventEditTestTags.ParticipantsField).assertIsDisplayed()

        // Act
        onNodeWithTag(EventEditTestTags.CalendarField).performClick()
        onNodeWithTag(EventEditTestTags.calendarOption(EventCalendar.Private)).performClick()
        onNodeWithTag(EventEditTestTags.SaveButton).performClick()

        // Assert
        onNodeWithTag(EventEditTestTags.ParticipantsField).assertDoesNotExist()
        onNodeWithTag(EventEditTestTags.PrivateNote).assertIsDisplayed()
        assertEquals(EventCalendar.Private, saved!!.calendar)
    }

    @Test
    fun participantsAreChosenFromTheHomesMembers() = runComposeUiTest {
        // Arrange
        var saved: CalendarEvent? = null
        setContent { VestaTheme { CalendarEventEditScreen(event = null, date = day, members = members, onSave = { saved = it }) } }
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput("Parents' evening")

        // Act
        onNodeWithTag(EventEditTestTags.ParticipantsField).performClick()
        onNodeWithTag(EventEditTestTags.participant("m:b")).performClick()
        onNodeWithTag(EventEditTestTags.ParticipantsDone).performClick()
        onNodeWithTag(EventEditTestTags.participantChip("m:b")).assertIsDisplayed()
        onNodeWithTag(EventEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(setOf("m:b"), saved!!.participants)
    }

    @Test
    fun editingShowsTheEventsFieldsAndSavesItUnderTheSameId() = runComposeUiTest {
        // Arrange
        val event = CalendarEvent(
            "e1", EventCalendar.Home, "Holiday", allDay = true, startMillis = allDayMillis(day), endMillis = allDayMillis(day.plusDays(2)),
            location = "Cornwall",
        )
        var saved: CalendarEvent? = null
        setContent { VestaTheme { CalendarEventEditScreen(event = event, date = day, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(EventEditTestTags.AllDay).assertIsOn()
        onNodeWithTag(EventEditTestTags.TitleField).performTextReplacement("Holiday 2026")
        onNodeWithTag(EventEditTestTags.SaveButton).performClick()

        // Assert
        val updated = saved!!
        assertEquals("e1", updated.id)
        assertEquals("Holiday 2026", updated.title)
        assertEquals("Cornwall", updated.location)
        assertEquals(allDayMillis(day.plusDays(2)), updated.endMillis)
    }

    @Test
    fun deleteShowsOnlyWhenEditingAndDeletesThatEvent() = runComposeUiTest {
        // Arrange
        val event = CalendarEvent("e1", EventCalendar.Home, "Holiday", allDay = true, startMillis = allDayMillis(day), endMillis = allDayMillis(day))
        val deleted = mutableListOf<String>()
        setContent { VestaTheme { CalendarEventEditScreen(event = event, date = day, onDelete = { deleted += it.id }) } }

        // Act
        onNodeWithTag(EventEditTestTags.DeleteButton).performClick()

        // Assert
        assertEquals(listOf("e1"), deleted)
    }

    @Test
    fun theDeleteButtonIsAbsentOnANewEvent() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarEventEditScreen(event = null, date = day) } }

        // Act
        // (no user interaction)

        // Assert
        onNodeWithTag(EventEditTestTags.DeleteButton).assertDoesNotExist()
    }

    @Test
    fun cancelLeavesWithoutSaving() = runComposeUiTest {
        // Arrange
        var cancelled = 0
        var saved = 0
        setContent { VestaTheme { CalendarEventEditScreen(event = null, date = day, onCancel = { cancelled++ }, onSave = { saved++ }) } }
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput("Dentist")

        // Act
        onNodeWithTag(EventEditTestTags.CancelButton).performClick()

        // Assert
        assertEquals(1, cancelled)
        assertEquals(0, saved)
    }

    private class Recorder : FileExporter {
        val files = mutableListOf<Triple<String, String, String>>()

        override suspend fun export(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
            files += Triple(fileName, mimeType, bytes.decodeToString())
            return true
        }
    }

    private val saved = CalendarEvent("e1", EventCalendar.Home, "Dentist", allDay = true, startMillis = allDayMillis(day), endMillis = allDayMillis(day))

    @Test
    fun theOverflowMenuExportsTheEventAsIcal() = runComposeUiTest {
        // Arrange
        val recorder = Recorder()
        setContent { VestaTheme { CompositionLocalProvider(LocalFileExporter provides recorder) { CalendarEventEditScreen(event = saved, date = day) } } }

        // Act
        onNodeWithTag(EventEditTestTags.OverflowButton).performClick()
        onNodeWithTag(EventEditTestTags.ExportMenuItem).performClick()
        waitForIdle()

        // Assert
        val (name, type, text) = recorder.files.single()
        assertEquals("Dentist.ics", name)
        assertEquals("text/calendar", type)
        assertTrue("UID:e1@vesta" in text && "DTSTART;VALUE=DATE:20261014" in text)
    }

    @Test
    fun theExportIncludesEditsNotYetSaved() = runComposeUiTest {
        // Arrange
        val recorder = Recorder()
        setContent { VestaTheme { CompositionLocalProvider(LocalFileExporter provides recorder) { CalendarEventEditScreen(event = saved, date = day) } } }
        onNodeWithTag(EventEditTestTags.TitleField).performTextReplacement("Dentist (moved)")

        // Act
        onNodeWithTag(EventEditTestTags.OverflowButton).performClick()
        onNodeWithTag(EventEditTestTags.ExportMenuItem).performClick()
        waitForIdle()

        // Assert
        assertTrue("SUMMARY:Dentist (moved)" in recorder.files.single().third)
    }

    @Test
    fun aNewEventHasNoOverflowMenu() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CompositionLocalProvider(LocalFileExporter provides Recorder()) { CalendarEventEditScreen(event = null, date = day) } } }

        // Act
        // (no user interaction)

        // Assert
        onNodeWithTag(EventEditTestTags.OverflowButton).assertDoesNotExist()
    }

    @Test
    fun withNoExporterThereIsNoOverflowMenu() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarEventEditScreen(event = saved, date = day) } }

        // Act
        // (no user interaction)

        // Assert
        onNodeWithTag(EventEditTestTags.OverflowButton).assertDoesNotExist()
    }
}
