package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDone
import uk.veritaslabs.vesta.tasks.TaskList

class TaskDocumentSourceTest {

    private fun task(id: String, title: String = "Bins", list: TaskList = TaskList.Home) = Task(
        id = id, list = list, title = title, description = "Green one", due = CalendarDate(2026, 10, 14), timeMinute = 8 * 60 + 30,
        repeat = EventRepeat.Weekly, every = 2, assignees = setOf("m:b", "m:a"), createdMillis = 99L, createdBy = "m:a",
    )

    @Test
    fun aTaskIsPublishedWithEveryField() = runBlocking {
        // Arrange
        val source = TaskDocumentSource(Document(), TaskList.Home)

        // Act
        source.save(task("t1"))
        val read = source.snapshot.first().tasks.single()

        // Assert
        assertEquals(task("t1"), read)
    }

    @Test
    fun clearingTheDueDateAndTimeRemovesThem() = runBlocking {
        // Arrange
        val source = TaskDocumentSource(Document(), TaskList.Home)
        source.save(task("t1"))

        // Act
        source.save(task("t1").copy(due = null, timeMinute = null, repeat = EventRepeat.None, every = 1))
        val read = source.snapshot.first().tasks.single()

        // Assert
        assertNull(read.due)
        assertNull(read.timeMinute)
    }

    @Test
    fun savingATaskAgainReportsAChangeOnlyWhenItDiffers() = runBlocking {
        // Arrange
        var changes = 0
        val source = TaskDocumentSource(Document(), TaskList.Home, onLocalChange = { changes++ })
        source.save(task("t1"))

        // Act
        source.save(task("t1"))
        source.save(task("t1", title = "Bins out"))

        // Assert
        assertEquals(listOf("Bins out"), source.snapshot.first().tasks.map { it.title })
        assertEquals(2, changes)
    }

    @Test
    fun aDoneRecordIsKeptPerOccurrenceAndCanBeCleared() = runBlocking {
        // Arrange
        val source = TaskDocumentSource(Document(), TaskList.Home)
        source.save(task("t1"))

        // Act
        source.setDone(TaskDone("t1", "2026-10-14", 5L, "m:a"))
        source.setDone(TaskDone("t1", "2026-10-28", 6L, "m:b"))
        source.clearDone("t1", "2026-10-14")
        val done = source.snapshot.first().done

        // Assert
        assertEquals(listOf(TaskDone("t1", "2026-10-28", 6L, "m:b")), done)
    }

    @Test
    fun tickingTheSameOccurrenceAgainReportsNoChange() = runBlocking {
        // Arrange
        var changes = 0
        val source = TaskDocumentSource(Document(), TaskList.Home, onLocalChange = { changes++ })
        source.setDone(TaskDone("t1", "anytime", 5L, "m:a"))

        // Act
        source.setDone(TaskDone("t1", "anytime", 5L, "m:a"))
        source.clearDone("t1", "nope")

        // Assert
        assertEquals(1, changes)
    }

    @Test
    fun deletingATaskLeavesItsDoneRecordsInTheDocument() = runBlocking {
        // Arrange
        val source = TaskDocumentSource(Document(), TaskList.Home)
        source.save(task("t1"))
        source.setDone(TaskDone("t1", "2026-10-14", 5L, "m:a"))

        // Act
        source.delete("t1")
        val snapshot = source.snapshot.first()

        // Assert
        assertTrue(snapshot.tasks.isEmpty())
        assertEquals(1, snapshot.done.size)
    }

    @Test
    fun anEntryWithoutATitleIsSkippedAndABadIntervalAndTimeAreClamped() = runBlocking {
        // Arrange
        val document = Document()
        val tx = document.startTransaction()
        tx.set(org.automerge.ObjectId.ROOT, "broken", org.automerge.ObjectType.MAP)
        val odd = tx.set(org.automerge.ObjectId.ROOT, "odd", org.automerge.ObjectType.MAP)
        tx.set(odd, "title", "Odd")
        tx.set(odd, "every", "-4")
        tx.set(odd, "time", "99999")
        tx.set(odd, "repeat", "Fortnightly")
        tx.commit()

        // Act
        val read = TaskDocumentSource(document, TaskList.Home).snapshot.first().tasks.single()

        // Assert
        assertEquals("Odd", read.title)
        assertEquals(1, read.every)
        assertNull(read.timeMinute)
        assertEquals(EventRepeat.None, read.repeat)
    }

    @Test
    fun twoDevicesAddingDifferentTasksKeepBothAndTickingOneOccurrenceMergesToOne() = runBlocking {
        // Arrange
        val a = TaskDocumentSource(Document(), TaskList.Home)
        val b = TaskDocumentSource(Document(), TaskList.Home)
        a.save(task("t1", "From A"))
        b.save(task("t2", "From B"))
        a.setDone(TaskDone("t1", "2026-10-14", 5L, "m:a"))
        b.setDone(TaskDone("t1", "2026-10-14", 6L, "m:b"))

        // Act
        b.applyChanges(a.changesSince(b.heads()))
        a.applyChanges(b.changesSince(a.heads()))

        // Assert
        assertEquals(listOf("From A", "From B"), a.snapshot.first().tasks.map { it.title }.sorted())
        assertEquals(listOf("From A", "From B"), b.snapshot.first().tasks.map { it.title }.sorted())
        assertEquals(1, a.snapshot.first().done.size)
        assertEquals(a.snapshot.first().done, b.snapshot.first().done)
    }

    @Test
    fun aHomesTasksAndThePrivateOnesAreStoredSeparatelyAndComeBackAfterReopening() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Maple Street", "family", 1L)
        database.homeQueries.insert("home-2", "Beach house", "family", 1L)
        val sources = TaskSources(database)
        sources.forHome("home-1")!!.save(task("t1", "Bins"))
        sources.privateForHome("home-1")!!.save(task("p1", "Gym", TaskList.Private))

        // Act
        val reopened = TaskSources(database)

        // Assert
        assertEquals(listOf("Bins"), reopened.forHome("home-1")!!.snapshot.first().tasks.map { it.title })
        assertEquals(listOf("Gym"), reopened.privateForHome("home-1")!!.snapshot.first().tasks.map { it.title })
        assertEquals(TaskList.Private, reopened.privateForHome("home-1")!!.snapshot.first().tasks.single().list)
        assertTrue(reopened.privateForHome("home-2")!!.snapshot.first().tasks.isEmpty())
        assertNull(reopened.forHome("nope"))
        assertNull(reopened.privateForHome("nope"))
    }

    @Test
    fun localEditsAnnounceTheHomeOnTheRightFlow() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Maple Street", "family", 1L)
        val sources = TaskSources(database)
        val shared = mutableListOf<String>()
        val private = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { sources.localChanges.collect { shared += it } }
        scope.launch { sources.privateChanges.collect { private += it } }

        // Act
        sources.forHome("home-1")!!.save(task("t1"))
        sources.privateForHome("home-1")!!.save(task("p1", list = TaskList.Private))

        // Assert
        assertEquals(listOf("home-1"), shared)
        assertEquals(listOf("home-1"), private)
        scope.cancel()
    }
}
