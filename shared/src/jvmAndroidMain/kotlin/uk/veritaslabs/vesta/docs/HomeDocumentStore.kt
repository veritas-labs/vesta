package uk.veritaslabs.vesta.docs

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import uk.veritaslabs.vesta.db.VestaDatabase

/** The id a Home's [feature] document is stored and synced under: `<homeId>:<feature>`. */
internal fun homeDocumentId(homeId: String, feature: String) = "$homeId:$feature"

/**
 * One shared source [S] per Home for one [feature], opened on first use. Every `*Sources` class repeated this; there
 * must be exactly one source per document per Home, or two in-memory copies would diverge. A private variant of a
 * feature (private tasks, private calendar) is simply another store with its own [feature] string.
 *
 * [create] builds the source over the opened [Document]. It is given [persist] (stores the document's bytes under this
 * Home and feature) and `onLocalChange` (which the source must call after a local edit, never after an incoming sync;
 * it feeds [localChanges]). It returns null when the source cannot be opened yet (Shopping without a Pantry), in which
 * case nothing is cached and any earlier entry is evicted.
 *
 * [refreshNew] is called on a source whose document was not stored yet, so its state is published and the empty
 * document is stored straight away. It is a hook rather than an interface bound on [S] so a source need not change
 * shape to be stored here; typically `{ it.refresh() }`.
 */
internal class HomeDocumentStore<S : HomeDocument>(
    private val database: VestaDatabase,
    val feature: String,
    private val nowMillis: () -> Long,
    private val refreshNew: suspend (S) -> Unit,
    private val create: suspend (
        homeId: String,
        document: Document,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
    ) -> S?,
) {

    private val lock = Mutex()
    private val sources = mutableMapOf<String, S>()
    private val _localChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Home each time its document is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = _localChanges.asSharedFlow()

    /** [homeId]'s source, opened on first use; null if there is no such Home or [create] cannot open it yet. */
    suspend fun forHome(homeId: String): S? = lock.withLock {
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) {
            sources.remove(homeId)
            return@withLock null
        }
        sources[homeId]?.let { return@withLock it }
        val queries = database.automergeDocumentQueries
        val documentId = homeDocumentId(homeId, feature)
        val stored = queries.selectById(documentId).awaitAsOneOrNull()
        val source = create(
            homeId,
            Document.load(stored?.bytes ?: Document().save()),
            { bytes -> queries.upsert(documentId, homeId, feature, bytes, nowMillis()) },
            { _localChanges.tryEmit(homeId) },
        )
        if (source == null) {
            sources.remove(homeId)
            return@withLock null
        }
        if (stored == null) refreshNew(source)
        sources[homeId] = source
        source
    }
}
