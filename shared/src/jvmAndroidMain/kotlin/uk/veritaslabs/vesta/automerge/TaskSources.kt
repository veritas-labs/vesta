package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.db.VestaDatabase

private const val TasksFeature = "tasks"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s tasks document. */
fun tasksDocumentId(homeId: String): String = "$homeId:$TasksFeature"

/**
 * Id of the user's private tasks in [homeId]: kept per Home, but unlike the Home's own documents it only ever syncs between the
 * user's paired devices (ADR-031). It must not end in `:tasks`, which would make it the Home's shared tasks.
 */
fun privateTasksDocumentId(homeId: String): String = "$homeId:$PrivateTasksFeature"

private const val PrivateTasksFeature = "private-tasks"

/** One shared [TaskDocumentSource] per Home (see [PantrySources] for why there must be exactly one), and one private one per Home. */
class TaskSources(
    private val database: VestaDatabase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val lock = Mutex()
    private val sources = mutableMapOf<String, TaskDocumentSource>()
    private val privateSources = mutableMapOf<String, TaskDocumentSource>()
    private val _localChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Home each time its tasks are edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = _localChanges.asSharedFlow()

    private val _privateChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Home each time the user's private tasks in it are edited on this device, to be pushed to their other devices. */
    val privateChanges: SharedFlow<String> = _privateChanges.asSharedFlow()

    /** [homeId]'s tasks source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): TaskDocumentSource? = lock.withLock {
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) {
            sources.remove(homeId)
            return@withLock null
        }
        sources[homeId] ?: openHome(homeId).also { sources[homeId] = it }
    }

    /** The user's private tasks in [homeId], opened on first use; null if there is no such Home. */
    suspend fun privateForHome(homeId: String): TaskDocumentSource? = lock.withLock {
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) {
            privateSources.remove(homeId)
            return@withLock null
        }
        privateSources[homeId] ?: openPrivate(homeId).also { privateSources[homeId] = it }
    }

    private suspend fun openHome(homeId: String): TaskDocumentSource {
        val documentId = tasksDocumentId(homeId)
        val queries = database.automergeDocumentQueries
        val stored = queries.selectById(documentId).awaitAsOneOrNull()
        val source = TaskDocumentSource(
            Document.load(stored?.bytes ?: Document().save()),
            TaskList.Home,
            persist = { bytes -> queries.upsert(documentId, homeId, TasksFeature, bytes, nowMillis()) },
            onLocalChange = { _localChanges.tryEmit(homeId) },
        )
        if (stored == null) source.refresh()
        return source
    }

    private suspend fun openPrivate(homeId: String): TaskDocumentSource {
        val documentId = privateTasksDocumentId(homeId)
        val queries = database.automergeDocumentQueries
        val stored = queries.selectById(documentId).awaitAsOneOrNull()
        val source = TaskDocumentSource(
            Document.load(stored?.bytes ?: Document().save()),
            TaskList.Private,
            persist = { bytes -> queries.upsert(documentId, homeId, PrivateTasksFeature, bytes, nowMillis()) },
            onLocalChange = { _privateChanges.tryEmit(homeId) },
        )
        if (stored == null) source.refresh()
        return source
    }
}
