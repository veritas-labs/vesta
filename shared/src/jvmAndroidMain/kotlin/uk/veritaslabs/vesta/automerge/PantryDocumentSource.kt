package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.ui.screens.PantryCategoriesSource
import uk.veritaslabs.vesta.ui.screens.PantryCategoryGroup
import uk.veritaslabs.vesta.ui.screens.PantryContents
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails
import uk.veritaslabs.vesta.ui.screens.PantryListItem
import uk.veritaslabs.vesta.ui.screens.PantrySource

private const val PantryFeature = "pantry"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s Pantry document. */
fun pantryDocumentId(homeId: String): String = "$homeId:$PantryFeature"

/**
 * A [PantrySource] backed by a Pantry Automerge [document] (ADR-008).
 *
 * Automerge documents don't notify observers, so [contents] is republished — and [persist] called with
 * the document's saved bytes — after every [mutate] and whenever the caller reports an out-of-band change
 * via [refresh]. All access to [document] is serialised on an internal lock, so route every read or write of it
 * through this class. [onLocalChange] is told after each [mutate] (an edit made here) so it can be synced to other
 * devices; changes that arrive through [receiveSync] don't call it.
 *
 * It is also the Home's [PantryCategoriesSource] (ADR-010). [PantryContents.emptyCategories] is still always
 * empty: registered categories without items are shown on the categories screen, not the Pantry.
 *
 * Use [openPantryDocumentSource] to get one that loads and saves a Home's document in [VestaDatabase].
 */
class PantryDocumentSource(
    private val document: Document,
    private val persist: suspend (ByteArray) -> Unit = {},
    private val onLocalChange: () -> Unit = {},
) : PantrySource, PantryCategoriesSource {

    private val lock = Mutex()
    private val state = MutableStateFlow(readContents())
    private val categoryState = MutableStateFlow(readCategories())

    override val contents: Flow<PantryContents> = state

    override val categoriesSource: PantryCategoriesSource get() = this

    override val categories: Flow<List<PantryCategoryGroup>> = categoryState

    /** Runs [block] against the document (e.g. `upsertPantryItem(it, …)`), then republishes and persists it. */
    suspend fun <T> mutate(block: (Document) -> T): T = lock.withLock {
        block(document).also { publishAndPersist() }
    }.also { onLocalChange() }

    /** The next Automerge sync message for the peer whose progress is [state], or null when it is up to date. */
    suspend fun generateSync(state: SyncState): ByteArray? = lock.withLock {
        document.generateSyncMessage(state).orElse(null)
    }

    /** Applies a sync message from a peer, then republishes and persists the document if it changed. */
    suspend fun receiveSync(state: SyncState, message: ByteArray) = lock.withLock {
        val before = document.heads
        document.receiveSyncMessage(state, message)
        if (!document.heads.contentEquals(before)) publishAndPersist()
    }

    override suspend fun save(item: PantryItemDetails) {
        mutate { doc ->
            ensurePantryCategories(doc, item.primaryCategory, item.secondaryCategory)
            val key = upsertPantryItem(
                doc,
                primaryCategory = item.primaryCategory,
                secondaryCategory = item.secondaryCategory,
                title = item.title,
                packageSize = item.packageSize,
                barcode = item.barcode,
            )
            correctStock(doc, key, item.stockCount)
            // Editing the name/barcode moves the entry to a new dedup key; drop the old one.
            item.key?.takeIf { it != key }?.let { deletePantryItem(doc, it) }
        }
    }

    override suspend fun delete(key: String) {
        mutate { deletePantryItem(it, key) }
    }

    override suspend fun addPrimary(name: String) = mutate { addPrimaryCategory(it, name) }

    override suspend fun addSecondary(primary: String, name: String) =
        mutate { addSecondaryCategory(it, primary, name) }

    override suspend fun deletePrimary(name: String) = mutate { deletePrimaryCategory(it, name) }

    override suspend fun deleteSecondary(primary: String, name: String) =
        mutate { deleteSecondaryCategory(it, primary, name) }

    /** Republishes and persists after [document] changed outside [mutate], e.g. by applying a sync message. */
    suspend fun refresh() = lock.withLock { publishAndPersist() }

    private suspend fun publishAndPersist() {
        state.value = readContents()
        categoryState.value = readCategories()
        persist(document.save())
    }

    private fun readCategories() = document.readPantryCategories().map { PantryCategoryGroup(it.name, it.secondary) }

    private fun readContents() = PantryContents(
        items = document.readPantry().map {
            PantryListItem(
                key = it.id,
                primaryCategory = it.primaryCategory,
                secondaryCategory = it.secondaryCategory,
                title = it.title,
                packageSize = it.packageSize,
                stockCount = it.stockCount,
                barcode = it.barcode,
            )
        },
    )
}

/**
 * Opens [homeId]'s Pantry as a [PantryDocumentSource] persisted in [database]'s `AutomergeDocument`
 * table (ADR-007): loads the stored document if there is one, otherwise starts a new empty Pantry and
 * stores it straight away, so the document's identity survives a restart before its first edit. The
 * [homeId] must be an existing `Home` row. A Home that came from another device is given an empty stored
 * document instead (see `storeEmptyPantry`), so it is filled from the one its creator seeded.
 */
suspend fun openPantryDocumentSource(
    database: VestaDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    onLocalChange: () -> Unit = {},
): PantryDocumentSource {
    val documentId = pantryDocumentId(homeId)
    val queries = database.automergeDocumentQueries
    val stored = queries.selectById(documentId).awaitAsOneOrNull()
    val document = Document.load(stored?.bytes ?: newPantryDocument())
    val source = PantryDocumentSource(
        document,
        persist = { bytes -> queries.upsert(documentId, homeId, PantryFeature, bytes, nowMillis()) },
        onLocalChange = onLocalChange,
    )
    if (stored == null) source.refresh()
    return source
}

/**
 * Stores an empty Pantry document for [homeId], unless one is stored already. For a Home that was created on another
 * device: that device seeds the document (root maps and default categories), and seeding a second one here would
 * give every device its own competing `items` and `categories` maps, only one of which survives a merge. An empty
 * document takes everything from the creator's.
 */
suspend fun storeEmptyPantry(
    database: VestaDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val queries = database.automergeDocumentQueries
    val documentId = pantryDocumentId(homeId)
    if (queries.selectById(documentId).awaitAsOneOrNull() != null) return
    queries.upsert(documentId, homeId, PantryFeature, Document().save(), nowMillis())
}
