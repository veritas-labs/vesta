package uk.veritaslabs.kits.docs

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import uk.veritaslabs.kits.store.KitsDatabase

/** The id a Space's [feature] document is stored and synced under: `<spaceId>:<feature>`. */
public fun spaceDocumentId(spaceId: String, feature: String) = "$spaceId:$feature"

/**
 * One shared source [S] per Space for one [feature], opened on first use. Every `*Sources` class repeated this; there
 * must be exactly one source per document per Space, or two in-memory copies would diverge. A private variant of a
 * feature (private tasks, private calendar) is simply another store with its own [feature] string.
 *
 * [create] builds the source over the opened [Document]. It is given [persist] (stores the document's bytes under this
 * Space and feature) and `onLocalChange` (which the source must call after a local edit, never after an incoming sync;
 * it feeds [localChanges]). It returns null when the source cannot be opened yet (Shopping without a Pantry), in which
 * case nothing is cached and any earlier entry is evicted.
 *
 * [newDocument] is the bytes of a document with nothing stored (seeded, for Pantry; empty by default).
 *
 * [refreshNew] is called on a source whose document was not stored yet, so its state is published and the empty
 * document is stored straight away. It is a hook rather than an interface bound on [S] so a source need not change
 * shape to be stored here; typically `{ it.refresh() }`.
 */
public class SpaceDocumentStore<S : SpaceDocument>(
    private val database: KitsDatabase,
    val feature: String,
    private val nowMillis: () -> Long,
    private val refreshNew: suspend (S) -> Unit,
    private val create: suspend (
        spaceId: String,
        document: Document,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
    ) -> S?,
    private val newDocument: () -> ByteArray = { Document().save() },
) {

    private val lock = Mutex()
    private val sources = mutableMapOf<String, S>()
    private val _localChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Space each time its document is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = _localChanges.asSharedFlow()

    /** [spaceId]'s source, opened on first use; null if there is no such Space or [create] cannot open it yet. */
    suspend fun forSpace(spaceId: String): S? = lock.withLock {
        if (database.homeQueries.selectById(spaceId).awaitAsOneOrNull() == null) {
            sources.remove(spaceId)
            return@withLock null
        }
        sources[spaceId]?.let { return@withLock it }
        val source = openSpaceDocument(database, spaceId, feature, nowMillis, refreshNew, create, newDocument) { _localChanges.tryEmit(spaceId) }
        if (source == null) {
            sources.remove(spaceId)
            return@withLock null
        }
        sources[spaceId] = source
        source
    }
}

/**
 * The open path [SpaceDocumentStore.forSpace] uses, also callable on its own (the `openXDocumentSource` functions): loads
 * [spaceId]'s [feature] document from [database] or starts an empty one, builds the source with [create], and calls
 * [refreshNew] when nothing was stored so the empty document is stored straight away. Does not check the Space exists.
 */
public suspend fun <S : SpaceDocument> openSpaceDocument(
    database: KitsDatabase,
    spaceId: String,
    feature: String,
    nowMillis: () -> Long,
    refreshNew: suspend (S) -> Unit,
    create: suspend (
        spaceId: String,
        document: Document,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
    ) -> S?,
    newDocument: () -> ByteArray = { Document().save() },
    onLocalChange: () -> Unit,
): S? {
    val queries = database.automergeDocumentQueries
    val documentId = spaceDocumentId(spaceId, feature)
    val stored = queries.selectById(documentId).awaitAsOneOrNull()
    val source = create(
        spaceId,
        Document.load(stored?.bytes ?: newDocument()),
        { bytes -> queries.upsert(documentId, spaceId, feature, bytes, nowMillis()) },
        onLocalChange,
    ) ?: return null
    if (stored == null) refreshNew(source)
    return source
}
