package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.tasks.MorningDigestItem
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskList

class MorningDigestReaderTest {
    private val today = CalendarDate(2026, 10, 19)

    private suspend fun fixture() = openInMemoryDatabase().also { database ->
        database.profileQueries.insert("mark", "Mark", null, "default")
        database.homeQueries.insert("home-1", "Maple Street", "family", 1L)
        database.homeMemberQueries.insert("home-1", "mark", "Mark", null, "admin", 1L)
    }

    @Test
    fun itReadsTodaysUntimedHomeAndPrivateTasksWithNoScreenOpen() = runBlocking {
        // Arrange
        val database = fixture()
        val sources = TaskSources(database)
        sources.forHome("home-1")!!.apply {
            save(Task("a", TaskList.Home, "Water the plants", due = today))
            save(Task("b", TaskList.Home, "Dentist", due = today, timeMinute = 9 * 60))
            save(Task("c", TaskList.Home, "Someday", due = null))
        }
        sources.privateForHome("home-1")!!.save(Task("d", TaskList.Private, "Call mum", due = today))

        // Act
        val items = morningDigestItems(database, TaskSources(database), today)

        // Assert
        assertEquals(
            setOf(MorningDigestItem("Water the plants", "home-1", "Maple Street", false), MorningDigestItem("Call mum", "home-1", "Maple Street", true)),
            items.toSet(),
        )
    }

    @Test
    fun aHomeTheUserIsNotAMemberOfAddsNothing() = runBlocking {
        // Arrange
        val database = fixture()
        database.homeQueries.insert("home-2", "Beach house", "family", 1L)
        TaskSources(database).forHome("home-2")!!.save(Task("x", TaskList.Home, "Not mine", due = today))

        // Act
        val items = morningDigestItems(database, TaskSources(database), today)

        // Assert
        assertEquals(emptyList(), items)
    }

    @Test
    fun withNoProfileThereIsNothing() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()

        // Act
        val items = morningDigestItems(database, TaskSources(database), today)

        // Assert
        assertEquals(emptyList(), items)
    }
}
