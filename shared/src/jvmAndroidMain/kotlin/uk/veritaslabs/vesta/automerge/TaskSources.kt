package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import kotlinx.coroutines.flow.SharedFlow
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.docs.SpaceDocumentStore
import uk.veritaslabs.kits.docs.spaceDocumentId
import uk.veritaslabs.vesta.tasks.TaskList

private const val TasksFeature = "tasks"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s tasks document. */
fun tasksDocumentId(homeId: String): String = spaceDocumentId(homeId, TasksFeature)

/**
 * Id of the user's private tasks in [homeId]: kept per Home, but unlike the Home's own documents it only ever syncs between the
 * user's paired devices (ADR-031). It must not end in `:tasks`, which would make it the Home's shared tasks.
 */
fun privateTasksDocumentId(homeId: String): String = spaceDocumentId(homeId, PrivateTasksFeature)

private const val PrivateTasksFeature = "private-tasks"

/** One shared [TaskDocumentSource] per Home (see [PantrySources] for why there must be exactly one), and one private one per Home. */
class TaskSources(
    database: KitsDatabase,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val shared = SpaceDocumentStore(
        database, TasksFeature, nowMillis,
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> TaskDocumentSource(document, TaskList.Home, persist, changed) },
    )
    private val personal = SpaceDocumentStore(
        database, PrivateTasksFeature, nowMillis,
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> TaskDocumentSource(document, TaskList.Private, persist, changed) },
    )

    /** The id of a Home each time its tasks are edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = shared.localChanges

    /** The id of a Home each time the user's private tasks in it are edited on this device, to be pushed to their other devices. */
    val privateChanges: SharedFlow<String> = personal.localChanges

    /** [homeId]'s tasks source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): TaskDocumentSource? = shared.forSpace(homeId)

    /** The user's private tasks in [homeId], opened on first use; null if there is no such Home. */
    suspend fun privateForHome(homeId: String): TaskDocumentSource? = personal.forSpace(homeId)
}
