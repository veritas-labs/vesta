package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import uk.veritaslabs.vesta.files.FileExporter
import uk.veritaslabs.vesta.files.FileImporter
import uk.veritaslabs.vesta.files.LocalFileImporter
import uk.veritaslabs.vesta.files.PickedFile
import uk.veritaslabs.vesta.calendar.icalCalendar
import uk.veritaslabs.vesta.files.LocalFileExporter
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.CalendarDocumentSource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.allDayMillis
import uk.veritaslabs.vesta.ui.flows.CalendarFlow
import uk.veritaslabs.vesta.ui.screens.CalendarTestTags
import uk.veritaslabs.vesta.ui.screens.CalendarSettingsScreen
import uk.veritaslabs.vesta.ui.screens.CalendarSettingsTestTags
import uk.veritaslabs.vesta.ui.screens.CalendarViewModel
import uk.veritaslabs.vesta.calendar.CalendarSettings
import uk.veritaslabs.vesta.ui.screens.EventEditTestTags
import uk.veritaslabs.vesta.home.MemberListItem
import uk.veritaslabs.vesta.home.MemberRole
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for calendar events (docs/adr/ADR-029-calendar-events.md; see docs/testing/acceptance-tests.md). Each test is one
 * scenario driven through what a user can see and tap, over the real Automerge-backed Home and private calendars, the view model and
 * the calendar flow. The criteria:
 *
 *  1. A plus button on the calendar opens a form for a new event on the selected day, a title, 09:00 to 09:30 by default.
 *  2. An event needs a title; Save is disabled without one, and Cancel adds nothing.
 *  3. A saved event is listed under the grid on its day, with its times, and its day has a dot of its colour in the grid.
 *  4. An event can be all day, and can repeat daily, weekly, monthly or yearly: it then shows on each day it comes round to.
 *  5. I can choose the event's colour and give it a location and a description.
 *  6. I can choose participants from the Home's members on the Home calendar.
 *  7. I can open an event, change it, and see the change; and delete it.
 *  8. An event can be on the Home calendar (shared) or on my private calendar, which is marked with a padlock, has no participants,
 *     and is saved in a separate document that is never shared with the Home (that it syncs only to my own devices is
 *     DeviceDataSyncServiceTest).
 *  9. Events survive an app restart.
 * 10. An open event's overflow menu has "Export as iCal event", which offers the event as an .ics file.
 * 12. The calendar settings have an Import section: I choose a calendar and a .ics file, its events are added there, and importing a file
 *     again (or one Vesta exported) updates them instead of duplicating.
 * 11. The calendar settings have an Export section: I choose the Home calendar, my private one or both, and export them as one .ics file.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CalendarEventsAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its writes run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val today = CalendarDate(2026, 10, 14)
    private val members = listOf(MemberListItem("mark", "Mark", MemberRole.Admin, isYou = true), MemberListItem("sam", "Sam", MemberRole.Member))

    private class Calendars(home: ByteArray? = null, private: ByteArray? = null) {
        var homeBytes: ByteArray = home ?: Document().save()
        var privateBytes: ByteArray = private ?: Document().save()
        val home = CalendarDocumentSource(Document.load(homeBytes), EventCalendar.Home, persist = { homeBytes = it })
        val private = CalendarDocumentSource(Document.load(privateBytes), EventCalendar.Private, persist = { privateBytes = it })

        fun homeEvents() = runBlocking { home.events.first() }

        fun privateEvents() = runBlocking { private.events.first() }
    }

    private fun ComposeUiTest.showCalendar(calendars: Calendars) {
        setContent {
            val viewModel = remember { CalendarViewModel(calendars.home, calendars.private, flowOf(members)) }
            VestaTheme { CalendarFlow(viewModel, homeName = "Maple Street", today = today) }
        }
    }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForNoTag(tag: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    /** Opens the form from the plus button, types [title] and saves; returns once the form has closed. */
    private fun ComposeUiTest.addEvent(title: String, configure: ComposeUiTest.() -> Unit = {}) {
        onNodeWithTag(CalendarTestTags.AddButton).performClick()
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput(title)
        configure()
        onNodeWithTag(EventEditTestTags.SaveButton).performClick()
        waitForNoTag(EventEditTestTags.SaveButton)
    }

    @Test
    fun thePlusButtonOpensAFormOnTheSelectedDayWithDefaultTimes() = runComposeUiTest {
        // Arrange — Given the calendar with the 20th selected
        showCalendar(Calendars())
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 20))).performClick()

        // Act — When I tap the plus button
        onNodeWithTag(CalendarTestTags.AddButton).performClick()

        // Assert — Then a new event form opens, from 09:00 to 09:30, with no title yet
        onNodeWithTag(EventEditTestTags.TitleField).assertIsDisplayed()
        onNodeWithTag(EventEditTestTags.StartTime).assertTextContains("09:00")
        onNodeWithTag(EventEditTestTags.EndTime).assertTextContains("09:30")
        onNodeWithTag(EventEditTestTags.StartDate).assertTextContains("20 Oct 2026")
        onNodeWithTag(EventEditTestTags.SaveButton).assertIsNotEnabled()
    }

    @Test
    fun cancellingTheFormAddsNothingAndReturnsToTheSameDay() = runComposeUiTest {
        // Arrange — Given the form open with a title typed, from the 20th
        val calendars = Calendars()
        showCalendar(calendars)
        onNodeWithTag(CalendarTestTags.day(CalendarDate(2026, 10, 20))).performClick()
        onNodeWithTag(CalendarTestTags.AddButton).performClick()
        onNodeWithTag(EventEditTestTags.TitleField).performTextInput("Dentist")

        // Act — When I cancel
        onNodeWithTag(EventEditTestTags.CancelButton).performClick()

        // Assert — Then nothing was added and the calendar is still on the 20th
        onNodeWithTag(CalendarTestTags.SelectedDay).assertTextContains("20 October 2026")
        onNodeWithTag(CalendarTestTags.NoEvents).assertIsDisplayed()
        assertEquals(emptyList(), calendars.homeEvents())
    }

    @Test
    fun aSavedEventIsListedOnItsDayWithItsTimesAndMarkedWithADot() = runComposeUiTest {
        // Arrange — Given the calendar on today
        val calendars = Calendars()
        showCalendar(calendars)

        // Act — When I add "Dentist" for today
        addEvent("Dentist")

        // Assert — Then it is listed with 09:00 – 09:30, today's cell has a dot, and it is saved on the Home calendar
        waitForTag(CalendarTestTags.event(calendars.homeEvents().single().id))
        onNodeWithTag(CalendarTestTags.event(calendars.homeEvents().single().id)).assertTextContains("09:00 – 09:30", substring = true)
        onNodeWithTag(CalendarTestTags.dots(today), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf("Dentist"), calendars.homeEvents().map { it.title })
        assertEquals(emptyList(), calendars.privateEvents())
    }

    @Test
    fun anAllDayEventShowsAsAllDay() = runComposeUiTest {
        // Arrange — Given the calendar on today
        val calendars = Calendars()
        showCalendar(calendars)

        // Act — When I add "Holiday" and tick All day
        addEvent("Holiday") { onNodeWithTag(EventEditTestTags.AllDay).performClick() }

        // Assert — Then it is listed as all day on today
        waitForTag(CalendarTestTags.event(calendars.homeEvents().single().id))
        val event = calendars.homeEvents().single()
        assertTrue(event.allDay)
        assertEquals(allDayMillis(today), event.startMillis)
        onNodeWithTag(CalendarTestTags.event(event.id)).assertTextContains("All day", substring = true)
    }

    @Test
    fun aWeeklyEventShowsAgainAWeekLaterAndNotTheDayAfter() = runComposeUiTest {
        // Arrange — Given a weekly "Bin day" added today
        val calendars = Calendars()
        showCalendar(calendars)
        addEvent("Bin day") {
            onNodeWithTag(EventEditTestTags.Repeat).performClick()
            onNodeWithTag(EventEditTestTags.repeatOption(EventRepeat.Weekly)).performClick()
        }
        waitForTag(CalendarTestTags.event(calendars.homeEvents().single().id))
        val id = calendars.homeEvents().single().id

        // Act — When I look at the same weekday next week, and at the day after
        onNodeWithTag(CalendarTestTags.day(today.plusDays(7))).performClick()
        val nextWeek = onAllNodesWithTag(CalendarTestTags.event(id)).fetchSemanticsNodes().size
        onNodeWithTag(CalendarTestTags.day(today.plusDays(8))).performClick()
        val dayAfter = onAllNodesWithTag(CalendarTestTags.event(id)).fetchSemanticsNodes().size

        // Assert — Then it is there next week and not the day after that
        assertEquals(1, nextWeek)
        assertEquals(0, dayAfter)
        onNodeWithTag(CalendarTestTags.dots(today.plusDays(14)), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theColourLocationAndDescriptionAreSaved() = runComposeUiTest {
        // Arrange — Given the calendar on today
        val calendars = Calendars()
        showCalendar(calendars)

        // Act — When I add an event with a red colour, a location and a description
        addEvent("Dentist") {
            onNodeWithTag(EventEditTestTags.ColourField).performClick()
            onNodeWithTag(EventEditTestTags.colourOption(3)).performClick()
            onNodeWithTag(EventEditTestTags.LocationField).performTextInput("High Street")
            onNodeWithTag(EventEditTestTags.DescriptionField).performTextInput("Bring the forms")
        }

        // Assert — Then they are on the event
        waitUntil(timeoutMillis = 5_000) { calendars.homeEvents().isNotEmpty() }
        val event = calendars.homeEvents().single()
        assertEquals(3, event.color)
        assertEquals("High Street", event.location)
        assertEquals("Bring the forms", event.description)
    }

    @Test
    fun participantsAreChosenFromTheHomesMembers() = runComposeUiTest {
        // Arrange — Given the calendar on today, in a Home with Mark and Sam
        val calendars = Calendars()
        showCalendar(calendars)

        // Act — When I add an event and tick Sam as a participant
        addEvent("Parents' evening") {
            onNodeWithTag(EventEditTestTags.ParticipantsField).performClick()
            onNodeWithTag(EventEditTestTags.participant("m:sam")).performClick()
            onNodeWithTag(EventEditTestTags.ParticipantsDone).performClick()
        }

        // Assert — Then Sam is on the event
        waitUntil(timeoutMillis = 5_000) { calendars.homeEvents().isNotEmpty() }
        assertEquals(setOf("m:sam"), calendars.homeEvents().single().participants)
    }

    @Test
    fun anEventCanBeOpenedChangedAndSeenChanged() = runComposeUiTest {
        // Arrange — Given an event "Dentist" on today
        val calendars = Calendars()
        runBlocking {
            calendars.home.save(CalendarEvent("e1", EventCalendar.Home, "Dentist", true, allDayMillis(today), allDayMillis(today)))
        }
        showCalendar(calendars)
        waitForTag(CalendarTestTags.event("e1"))

        // Act — When I open it, rename it and save
        onNodeWithTag(CalendarTestTags.event("e1")).performClick()
        onNodeWithTag(EventEditTestTags.TitleField).performTextReplacement("Dentist (moved)")
        onNodeWithTag(EventEditTestTags.SaveButton).assertIsEnabled().performClick()
        waitForNoTag(EventEditTestTags.SaveButton)

        // Assert — Then the day lists the new title, and there is still one event
        waitUntil(timeoutMillis = 5_000) { calendars.homeEvents().single().title == "Dentist (moved)" }
        onNodeWithTag(CalendarTestTags.event("e1")).assertTextContains("Dentist (moved)")
    }

    @Test
    fun anEventCanBeDeleted() = runComposeUiTest {
        // Arrange — Given an event on today
        val calendars = Calendars()
        runBlocking {
            calendars.home.save(CalendarEvent("e1", EventCalendar.Home, "Dentist", true, allDayMillis(today), allDayMillis(today)))
        }
        showCalendar(calendars)
        waitForTag(CalendarTestTags.event("e1"))

        // Act — When I open it and delete it
        onNodeWithTag(CalendarTestTags.event("e1")).performClick()
        onNodeWithTag(EventEditTestTags.DeleteButton).performClick()

        // Assert — Then the day has no events and the dot has gone
        waitForTag(CalendarTestTags.NoEvents)
        onNodeWithTag(CalendarTestTags.dots(today), useUnmergedTree = true).assertDoesNotExist()
        assertEquals(emptyList(), calendars.homeEvents())
    }

    @Test
    fun aPrivateEventIsSavedOnThePrivateCalendarMarkedWithAPadlockAndHasNoParticipants() = runComposeUiTest {
        // Arrange — Given the calendar on today
        val calendars = Calendars()
        showCalendar(calendars)

        // Act — When I add "Gym" on my private calendar
        addEvent("Gym") {
            onNodeWithTag(EventEditTestTags.CalendarField).performClick()
            onNodeWithTag(EventEditTestTags.calendarOption(EventCalendar.Private)).performClick()
            onNodeWithTag(EventEditTestTags.PrivateNote).assertIsDisplayed()
        }

        // Assert — Then it is in the private document and not the Home's, listed with a padlock
        waitUntil(timeoutMillis = 5_000) { calendars.privateEvents().isNotEmpty() }
        assertEquals(listOf("Gym"), calendars.privateEvents().map { it.title })
        assertEquals(emptyList(), calendars.homeEvents())
        waitForTag(CalendarTestTags.event(calendars.privateEvents().single().id))
        onNodeWithContentDescription("Private event", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun movingAnEventToThePrivateCalendarTakesItOffTheHomeCalendar() = runComposeUiTest {
        // Arrange — Given a Home event "Dentist" with Sam as a participant
        val calendars = Calendars()
        runBlocking {
            calendars.home.save(CalendarEvent("e1", EventCalendar.Home, "Dentist", true, allDayMillis(today), allDayMillis(today), participants = setOf("m:sam")))
        }
        showCalendar(calendars)
        waitForTag(CalendarTestTags.event("e1"))

        // Act — When I open it and change its calendar to my private one
        onNodeWithTag(CalendarTestTags.event("e1")).performClick()
        onNodeWithTag(EventEditTestTags.CalendarField).performClick()
        onNodeWithTag(EventEditTestTags.calendarOption(EventCalendar.Private)).performClick()
        onNodeWithTag(EventEditTestTags.SaveButton).performClick()
        waitForNoTag(EventEditTestTags.SaveButton)

        // Assert — Then the Home calendar no longer has it, and the private one has it without participants
        waitUntil(timeoutMillis = 5_000) { calendars.privateEvents().isNotEmpty() && calendars.homeEvents().isEmpty() }
        assertEquals(emptySet(), calendars.privateEvents().single().participants)
        assertEquals("e1", calendars.privateEvents().single().id)
    }

    @Test
    fun eventsOnBothCalendarsAreSeenTogetherAfterARestart() = runComposeUiTest {
        // Arrange — Given a Home event and a private event, saved, and the app restarted over the same stored documents
        val first = Calendars()
        runBlocking {
            first.home.save(CalendarEvent("h", EventCalendar.Home, "Bins", true, allDayMillis(today), allDayMillis(today)))
            first.private.save(CalendarEvent("p", EventCalendar.Private, "Gym", true, allDayMillis(today), allDayMillis(today)))
        }
        val restarted = Calendars(first.homeBytes, first.privateBytes)

        // Act — When I open the calendar
        showCalendar(restarted)

        // Assert — Then both events are on today
        waitForTag(CalendarTestTags.event("h"))
        onNodeWithTag(CalendarTestTags.event("p")).assertIsDisplayed()
    }

    @Test
    fun anOpenEventCanBeExportedAsAnIcalFile() = runComposeUiTest {
        // Arrange — Given a weekly "Bin day" on today, and an app that can save files
        val calendars = Calendars()
        runBlocking {
            calendars.home.save(
                CalendarEvent("e1", EventCalendar.Home, "Bin day", true, allDayMillis(today), allDayMillis(today), EventRepeat.Weekly, location = "Front gate"),
            )
        }
        val exported = mutableListOf<Triple<String, String, String>>()
        val exporter = FileExporter { name, type, bytes -> exported += Triple(name, type, bytes.decodeToString()); true }
        setContent {
            val viewModel = remember { CalendarViewModel(calendars.home, calendars.private, flowOf(members)) }
            VestaTheme { CompositionLocalProvider(LocalFileExporter provides exporter) { CalendarFlow(viewModel, homeName = "Maple Street", today = today) } }
        }
        waitForTag(CalendarTestTags.event("e1"))

        // Act — When I open it and choose Export as iCal event from the overflow menu
        onNodeWithTag(CalendarTestTags.event("e1")).performClick()
        onNodeWithTag(EventEditTestTags.OverflowButton).performClick()
        onNodeWithTag(EventEditTestTags.ExportMenuItem).performClick()
        waitUntil(timeoutMillis = 5_000) { exported.isNotEmpty() }

        // Assert — Then a "Bin day.ics" calendar file is offered, holding the event with its weekly repeat and location
        val (name, type, text) = exported.single()
        assertEquals("Bin day.ics", name)
        assertEquals("text/calendar", type)
        assertTrue("SUMMARY:Bin day" in text && "RRULE:FREQ=WEEKLY" in text && "LOCATION:Front gate" in text)
    }

    @Test
    fun theWholePrivateCalendarCanBeExportedFromTheSettings() = runComposeUiTest {
        // Arrange — Given a Home event and two private events, and an app that can save files
        val calendars = Calendars()
        runBlocking {
            calendars.home.save(CalendarEvent("h", EventCalendar.Home, "Bins", true, allDayMillis(today), allDayMillis(today)))
            calendars.private.save(CalendarEvent("p1", EventCalendar.Private, "Gym", true, allDayMillis(today), allDayMillis(today)))
            calendars.private.save(CalendarEvent("p2", EventCalendar.Private, "Surprise party", true, allDayMillis(today.plusDays(9)), allDayMillis(today.plusDays(9))))
        }
        val exported = mutableListOf<Triple<String, String, String>>()
        val exporter = FileExporter { name, type, bytes -> exported += Triple(name, type, bytes.decodeToString()); true }
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalFileExporter provides exporter) {
                    CalendarSettingsScreen(CalendarSettings(), onChange = {}, events = calendars.homeEvents() + calendars.privateEvents(), homeName = "Maple Street")
                }
            }
        }

        // Act — When I untick the Home calendar and tap Export as iCal
        onNodeWithTag(CalendarSettingsTestTags.exportCalendar(EventCalendar.Home)).performClick()
        onNodeWithTag(CalendarSettingsTestTags.ExportButton).performClick()
        waitUntil(timeoutMillis = 5_000) { exported.isNotEmpty() }

        // Assert — Then one .ics file holds just my two private events
        val (name, type, text) = exported.single()
        assertEquals("Maple Street private calendar.ics", name)
        assertEquals("text/calendar", type)
        assertEquals(2, "BEGIN:VEVENT".toRegex().findAll(text).count())
        assertTrue("SUMMARY:Gym" in text && "SUMMARY:Surprise party" in text && "Bins" !in text)
    }

    @Test
    fun anImportedFileAddsItsEventsToTheChosenCalendarAndImportingItAgainDoesNotDuplicate() = runComposeUiTest {
        // Arrange — Given a calendar file from another program with a dentist appointment today, and the settings screen
        val calendars = Calendars()
        val file = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:abc-123@example.com\r\nDTSTART;VALUE=DATE:20261014\r\n" +
            "SUMMARY:Dentist\r\nLOCATION:High Street\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val importer = FileImporter { PickedFile("dentist.ics", file.encodeToByteArray()) }
        setContent {
            val viewModel = remember { CalendarViewModel(calendars.home, calendars.private, flowOf(members)) }
            VestaTheme {
                CompositionLocalProvider(LocalFileImporter provides importer) {
                    CalendarSettingsScreen(CalendarSettings(), onChange = {}, homeName = "Maple Street", onImport = { calendar, events -> viewModel.import(calendar, events) })
                }
            }
        }

        // Act — When I import it into my private calendar, twice
        onNodeWithTag(CalendarSettingsTestTags.importInto(EventCalendar.Private)).performClick()
        onNodeWithTag(CalendarSettingsTestTags.ImportButton).performClick()
        waitUntil(timeoutMillis = 5_000) { calendars.privateEvents().isNotEmpty() }
        onNodeWithTag(CalendarSettingsTestTags.ImportButton).performClick()
        waitForIdle()

        // Assert — Then my private calendar has the one event, with its location, and the Home's has none
        onNodeWithTag(CalendarSettingsTestTags.ImportResult).assertTextContains("Imported 1 event.", substring = true)
        val event = calendars.privateEvents().single()
        assertEquals("Dentist", event.title)
        assertEquals("High Street", event.location)
        assertEquals(allDayMillis(today), event.startMillis)
        assertEquals(emptyList(), calendars.homeEvents())
    }

    @Test
    fun aCalendarExportedFromVestaCanBeImportedBackIntoAnotherCalendar() = runComposeUiTest {
        // Arrange — Given an exported file of two Home events, and an empty calendar to import it into
        val source = Calendars()
        runBlocking {
            source.home.save(CalendarEvent("e1", EventCalendar.Home, "Bins", true, allDayMillis(today), allDayMillis(today), EventRepeat.Weekly))
            source.home.save(CalendarEvent("e2", EventCalendar.Home, "Dentist", false, 1_700_000_000_000L, 1_700_001_800_000L))
        }
        val file = icalCalendar(source.homeEvents())
        val target = Calendars()
        setContent {
            val viewModel = remember { CalendarViewModel(target.home, target.private, flowOf(members)) }
            VestaTheme {
                CompositionLocalProvider(LocalFileImporter provides FileImporter { PickedFile("export.ics", file.encodeToByteArray()) }) {
                    CalendarSettingsScreen(CalendarSettings(), onChange = {}, onImport = { calendar, events -> viewModel.import(calendar, events) })
                }
            }
        }

        // Act — When I import it into the Home calendar
        onNodeWithTag(CalendarSettingsTestTags.ImportButton).performClick()
        waitUntil(timeoutMillis = 5_000) { target.homeEvents().size == 2 }

        // Assert — Then the same two events, with their repeat and times, are there
        val byId = target.homeEvents().associateBy { it.id }
        assertEquals(EventRepeat.Weekly, byId.getValue("e1").repeat)
        assertEquals(1_700_000_000_000L, byId.getValue("e2").startMillis)
        assertEquals(1_700_001_800_000L, byId.getValue("e2").endMillis)
    }
}
