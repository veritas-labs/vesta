package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.db.openInMemoryDatabase

class CalendarDocumentSourceTest {

    private fun event(id: String, title: String = "Dentist", calendar: EventCalendar = EventCalendar.Home) = CalendarEvent(
        id = id, calendar = calendar, title = title, allDay = false, startMillis = 1_000_000L, endMillis = 2_800_000L,
        repeat = EventRepeat.Weekly, color = 3, participants = setOf("m:b", "m:a"), location = "High St", description = "Bring forms",
    )

    @Test
    fun anEventIsPublishedWithEveryField() = runBlocking {
        // Arrange
        val source = CalendarDocumentSource(Document(), EventCalendar.Home)

        // Act
        source.save(event("e1"))
        val read = source.events.first().single()

        // Assert
        assertEquals(event("e1"), read)
    }

    @Test
    fun savingAnEventAgainReplacesItAndReportsAChangeOnlyWhenItDiffers() = runBlocking {
        // Arrange
        var changes = 0
        val source = CalendarDocumentSource(Document(), EventCalendar.Home, onLocalChange = { changes++ })
        source.save(event("e1"))

        // Act
        source.save(event("e1"))
        source.save(event("e1", title = "Dentist at 9"))

        // Assert
        assertEquals(listOf("Dentist at 9"), source.events.first().map { it.title })
        assertEquals(2, changes)
    }

    @Test
    fun deletingAnEventRemovesItAndDeletingANonexistentOneChangesNothing() = runBlocking {
        // Arrange
        var changes = 0
        val source = CalendarDocumentSource(Document(), EventCalendar.Home, onLocalChange = { changes++ })
        source.save(event("e1"))
        source.save(event("e2", "Bins"))

        // Act
        source.delete("e1")
        source.delete("nope")

        // Assert
        assertEquals(listOf("Bins"), source.events.first().map { it.title })
        assertEquals(3, changes)
    }

    @Test
    fun anEntryWithoutTimesIsSkipped() = runBlocking {
        // Arrange
        val document = Document()
        val tx = document.startTransaction()
        tx.set(org.automerge.ObjectId.ROOT, "broken", org.automerge.ObjectType.MAP)
        tx.commit()

        // Act
        val source = CalendarDocumentSource(document, EventCalendar.Home)

        // Assert
        assertTrue(source.events.first().isEmpty())
    }

    @Test
    fun twoDevicesAddingDifferentEventsToOneCalendarKeepBoth() = runBlocking {
        // Arrange
        val a = CalendarDocumentSource(Document(), EventCalendar.Home)
        val b = CalendarDocumentSource(Document(), EventCalendar.Home)
        a.save(event("e1", "From A"))
        b.save(event("e2", "From B"))

        // Act
        b.applyChanges(a.changesSince(b.heads()))
        a.applyChanges(b.changesSince(a.heads()))

        // Assert
        assertEquals(listOf("From A", "From B"), a.events.first().map { it.title }.sorted())
        assertEquals(listOf("From A", "From B"), b.events.first().map { it.title }.sorted())
    }

    @Test
    fun aHomesCalendarAndThePrivateOneAreStoredSeparatelyAndComeBackAfterReopening() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Maple Street", "family", 1L)
        val sources = CalendarSources(database)
        sources.forHome("home-1")!!.save(event("e1", "Bins"))
        sources.privateForHome("home-1")!!.save(event("p1", "Gym", EventCalendar.Private))

        // Act
        val reopened = CalendarSources(database)

        // Assert
        assertEquals(listOf("Bins"), reopened.forHome("home-1")!!.events.first().map { it.title })
        assertEquals(listOf("Gym"), reopened.privateForHome("home-1")!!.events.first().map { it.title })
        assertEquals(EventCalendar.Private, reopened.privateForHome("home-1")!!.events.first().single().calendar)
    }

    @Test
    fun aPrivateCalendarBelongsToOneHomeAndIsNotSeenInAnother() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Maple Street", "family", 1L)
        database.homeQueries.insert("home-2", "Beach house", "family", 1L)
        val sources = CalendarSources(database)

        // Act
        sources.privateForHome("home-1")!!.save(event("p1", "Gym", EventCalendar.Private))

        // Assert
        assertEquals(listOf("Gym"), sources.privateForHome("home-1")!!.events.first().map { it.title })
        assertEquals(emptyList(), sources.privateForHome("home-2")!!.events.first())
        assertEquals(null, sources.privateForHome("nope"))
    }

    @Test
    fun noCalendarOpensForAHomeThatDoesNotExist() = runBlocking {
        // Arrange
        val sources = CalendarSources(openInMemoryDatabase())

        // Act
        val source = sources.forHome("nope")

        // Assert
        assertEquals(null, source)
    }
}
