package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
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
 * via [refresh] (call it after applying sync messages to [document]). All access to [document] is
 * serialised on an internal lock, so route every read or write of it through this class.
 *
 * It is also the Home's [PantryCategoriesSource] (ADR-010). [PantryContents.emptyCategories] is still always
 * empty: registered categories without items are shown on the categories screen, not the Pantry.
 *
 * Use [openPantryDocumentSource] to get one that loads and saves a Home's document in [VestaDatabase].
 */
class PantryDocumentSource(
    private val document: Document,
    private val persist: suspend (ByteArray) -> Unit = {},
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
 * [homeId] must be an existing `Home` row.
 */
suspend fun openPantryDocumentSource(
    database: VestaDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): PantryDocumentSource {
    val documentId = pantryDocumentId(homeId)
    val queries = database.automergeDocumentQueries
    val stored = queries.selectById(documentId).awaitAsOneOrNull()
    val document = Document.load(stored?.bytes ?: newPantryDocument())
    val source = PantryDocumentSource(document) { bytes ->
        queries.upsert(documentId, homeId, PantryFeature, bytes, nowMillis())
    }
    if (stored == null) source.refresh()
    return source
}
