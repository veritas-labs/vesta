package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingListItem
import uk.veritaslabs.vesta.ui.screens.ShoppingListSource

private const val ShoppingListFeature = "shopping-list"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s shopping-list document. */
fun shoppingListDocumentId(homeId: String): String = "$homeId:$ShoppingListFeature"

/**
 * A [ShoppingListSource] backed by a shopping-list Automerge [document] (ADR-008, fourth amendment), downstream of
 * the Home's [pantry]: saving an item also makes sure the Pantry has it. Same threading contract as
 * [PantryDocumentSource] — route every read or write of [document] through this class.
 *
 * Use [openShoppingListDocumentSource] to get one that loads and saves a Home's document in [VestaDatabase].
 */
class ShoppingListDocumentSource(
    private val document: Document,
    private val pantry: PantryDocumentSource,
    private val persist: suspend (ByteArray) -> Unit = {},
    private val onLocalChange: () -> Unit = {},
) : ShoppingListSource {

    private val lock = Mutex()
    private val state = MutableStateFlow(readItems())

    override val items: Flow<List<ShoppingListItem>> = state

    override suspend fun save(item: ShoppingItemDetails) {
        val key = lock.withLock {
            val key = if (item.key == null) {
                addShoppingItem(
                    document, item.primaryCategory, item.secondaryCategory, item.title,
                    item.quantity, item.packageSize, item.barcode,
                )
            } else {
                updateShoppingItem(
                    document, item.key, item.primaryCategory, item.secondaryCategory, item.title,
                    item.quantity, item.packageSize, item.barcode,
                )
            }
            publishAndPersist()
            key
        }
        if (key == null) return
        // The Pantry is written after the list: a crash in between leaves an item the Pantry doesn't know yet, which
        // the next add or edit of it repairs, rather than a Pantry entry for something never listed.
        pantry.mutate { doc ->
            ensurePantryCategories(doc, item.primaryCategory, item.secondaryCategory)
            ensurePantryItem(
                doc, item.primaryCategory, item.secondaryCategory, item.title, item.packageSize, item.barcode,
                countUse = item.key == null,
            )
        }
        onLocalChange()
    }

    override suspend fun remove(key: String) {
        lock.withLock {
            removeShoppingItem(document, key)
            publishAndPersist()
        }
        onLocalChange()
    }

    /** Republishes and persists after [document] changed outside [save]/[remove], e.g. by applying a sync message. */
    suspend fun refresh() = lock.withLock { publishAndPersist() }

    private suspend fun publishAndPersist() {
        state.value = readItems()
        persist(document.save())
    }

    private fun readItems() = document.readShoppingList().map {
        ShoppingListItem(
            key = it.id,
            primaryCategory = it.primaryCategory,
            secondaryCategory = it.secondaryCategory,
            title = it.title,
            quantity = it.quantity,
            packageSize = it.packageSize,
            barcode = it.barcode,
        )
    }
}

/**
 * Opens [homeId]'s shopping list as a [ShoppingListDocumentSource] persisted in [database]'s `AutomergeDocument`
 * table (ADR-007), downstream of [pantry]: loads the stored document if there is one, otherwise starts a new empty
 * list and stores it straight away. The [homeId] must be an existing `Home` row.
 */
suspend fun openShoppingListDocumentSource(
    database: VestaDatabase,
    homeId: String,
    pantry: PantryDocumentSource,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    onLocalChange: () -> Unit = {},
): ShoppingListDocumentSource {
    val documentId = shoppingListDocumentId(homeId)
    val queries = database.automergeDocumentQueries
    val stored = queries.selectById(documentId).awaitAsOneOrNull()
    val document = Document.load(stored?.bytes ?: newShoppingListDocument())
    val source = ShoppingListDocumentSource(
        document,
        pantry,
        persist = { bytes -> queries.upsert(documentId, homeId, ShoppingListFeature, bytes, nowMillis()) },
        onLocalChange = onLocalChange,
    )
    if (stored == null) source.refresh()
    return source
}
