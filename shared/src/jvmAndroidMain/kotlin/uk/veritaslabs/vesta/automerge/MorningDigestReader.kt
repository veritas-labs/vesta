package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.flow.first
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.tasks.DoneIndex
import uk.veritaslabs.vesta.tasks.MorningDigestItem
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.tasks.untimedToday

/**
 * What the morning digest says on [today]: in each Home the user is a member of, their open tasks due today with no time (Home and
 * private lists together). Read straight from the persisted documents, so it needs neither a screen nor the x0x daemon; empty if
 * there is no profile yet.
 */
suspend fun morningDigestItems(database: KitsDatabase, sources: TaskSources, today: CalendarDate): List<MorningDigestItem> {
    val me = database.profileQueries.selectAll().awaitAsList().firstOrNull()?.id ?: return emptyList()
    val ref = "m:$me"
    return database.homeQueries.selectAll().awaitAsList().flatMap { home ->
        if (database.homeMemberQueries.selectByHome(home.id).awaitAsList().none { it.memberId == me }) return@flatMap emptyList()
        val shared = sources.forHome(home.id)?.snapshot?.first()
        val own = sources.privateForHome(home.id)?.snapshot?.first()
        val tasks = shared?.tasks.orEmpty() + own?.tasks.orEmpty()
        val done = shared?.done.orEmpty() + own?.done.orEmpty()
        untimedToday(tasks, DoneIndex(done), ref, today)
            .map { MorningDigestItem(it.task.title, home.id, home.name, it.task.list == TaskList.Private) }
    }
}
