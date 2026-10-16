package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.vesta.docs.AutomergeDocumentCore
import uk.veritaslabs.vesta.docs.HomeDocument
import uk.veritaslabs.vesta.docs.homeDocumentId
import uk.veritaslabs.vesta.docs.openHomeDocument
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingListItem
import uk.veritaslabs.vesta.ui.screens.ShoppingListSource

private const val ShoppingListFeature = "shopping-list"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s shopping-list document. */
fun shoppingListDocumentId(homeId: String): String = homeDocumentId(homeId, ShoppingListFeature)

/**
 * A [ShoppingListSource] backed by a shopping-list Automerge [document] (ADR-008, fourth amendment), downstream of
 * the Home's [pantry]: saving an item leaves the Pantry alone; [addCheckedToPantry] is what puts bought items in it. Same threading contract as
 * [PantryDocumentSource] — route every read or write of [document] through this class.
 *
 * Use [openShoppingListDocumentSource] to get one that loads and saves a Home's document in [KitsDatabase].
 */
class ShoppingListDocumentSource private constructor(
    private val state: MutableStateFlow<List<ShoppingListItem>>,
    private val core: AutomergeDocumentCore,
    private val pantry: PantryDocumentSource,
) : ShoppingListSource, HomeDocument by core {

    constructor(
        document: Document,
        pantry: PantryDocumentSource,
        persist: suspend (ByteArray) -> Unit = {},
        onLocalChange: () -> Unit = {},
    ) : this(MutableStateFlow(document.toShoppingItems()), document, pantry, persist, onLocalChange)

    private constructor(
        state: MutableStateFlow<List<ShoppingListItem>>,
        document: Document,
        pantry: PantryDocumentSource,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
    ) : this(state, AutomergeDocumentCore(document, { state.value = document.toShoppingItems() }, persist, onLocalChange), pantry)

    override val items: Flow<List<ShoppingListItem>> = state

    // Saves and removals made while this list is still an unseeded placeholder (see [isShoppingListSeeded]); run, in order,
    // once the creator's list has arrived. In memory only, so one made before then is lost if the app is closed first.
    private val pending = mutableListOf<suspend () -> Unit>()

    private suspend fun queuedUntilSeeded(operation: suspend () -> Unit): Boolean = core.read {
        if (isShoppingListSeeded()) false else { pending += operation; true }
    }

    override suspend fun save(item: ShoppingItemDetails) {
        if (queuedUntilSeeded { save(item) }) return
        core.edit {
            if (item.key == null) {
                addShoppingItem(
                    this, item.primaryCategory, item.secondaryCategory, item.title,
                    item.quantity, item.packageSize, item.barcode, item.tags,
                )
            } else {
                updateShoppingItem(
                    this, item.key, item.primaryCategory, item.secondaryCategory, item.title,
                    item.quantity, item.packageSize, item.barcode, item.tags,
                )
            }
        }
    }

    override suspend fun remove(key: String) {
        if (queuedUntilSeeded { remove(key) }) return
        core.edit { removeShoppingItem(this, key) }
    }

    override suspend fun setProgress(key: String, got: Int, outOfStock: Boolean) {
        if (queuedUntilSeeded { setProgress(key, got, outOfStock) }) return
        core.edit { setShoppingItemProgress(this, key, got, outOfStock) }
    }

    override suspend fun addCheckedToPantry(): Int {
        val bought = core.read {
            if (isShoppingListSeeded()) toShoppingItems().filter { it.isBought } else emptyList()
        }
        if (bought.isEmpty()) return 0
        // The Pantry is written first: a crash in between leaves the items checked, to be added again, rather than
        // taking them off the list without their stock ever arriving. This is the only point an item reaches the Pantry:
        // listing something doesn't put it there, buying it does.
        pantry.edit { doc ->
            bought.forEach { item ->
                ensurePantryCategories(doc, item.primaryCategory, item.secondaryCategory)
                val key = ensurePantryItem(
                    doc, item.primaryCategory, item.secondaryCategory, item.title, item.packageSize, item.barcode,
                    refreshFields = true,
                )
                adjustStock(doc, key, item.got.coerceAtMost(item.quantity))
            }
        }
        core.edit {
            bought.forEach { item ->
                val got = item.got.coerceAtMost(item.quantity)
                if (got >= item.quantity) {
                    removeShoppingItem(this, item.key)
                } else {
                    updateShoppingItem(
                        this, item.key, item.primaryCategory, item.secondaryCategory, item.title,
                        item.quantity - got, item.packageSize, item.barcode, item.tags,
                    )
                    setShoppingItemProgress(this, item.key, 0, false)
                }
            }
        }
        return bought.size
    }

    /** Applies a sync message from a peer, then republishes and persists the list if it changed. */
    override suspend fun receiveSync(state: SyncState, message: ByteArray) {
        core.receiveSync(state, message)
        runPending()
    }

    /** Applies changes from [changesSince], as [receiveSync] does a sync message. Applying one again changes nothing. */
    override suspend fun applyChanges(changes: ByteArray) {
        core.applyChanges(changes)
        runPending()
    }

    private suspend fun runPending() {
        // The creator's list may just have arrived: what was saved or removed before it can now be done on top of it.
        val ready = core.read {
            if (pending.isNotEmpty() && isShoppingListSeeded()) pending.toList().also { pending.clear() } else emptyList()
        }
        ready.forEach { it() }
    }

    /** Republishes and persists after the document changed outside [save]/[remove], e.g. by applying a sync message. */
    suspend fun refresh() = core.refresh()
}

private fun Document.toShoppingItems() = readShoppingList().map {
    ShoppingListItem(
        key = it.id,
        primaryCategory = it.primaryCategory,
        secondaryCategory = it.secondaryCategory,
        title = it.title,
        quantity = it.quantity,
        packageSize = it.packageSize,
        barcode = it.barcode,
        tags = it.tags,
        got = it.got,
        outOfStock = it.outOfStock,
    )
}

/**
 * Opens [homeId]'s shopping list as a [ShoppingListDocumentSource] persisted in [database]'s `AutomergeDocument`
 * table (ADR-007), downstream of [pantry]: loads the stored document if there is one, otherwise starts a new empty
 * list and stores it straight away. The [homeId] must be an existing `Home` row.
 */
suspend fun openShoppingListDocumentSource(
    database: KitsDatabase,
    homeId: String,
    pantry: PantryDocumentSource,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    onLocalChange: () -> Unit = {},
): ShoppingListDocumentSource = checkNotNull(
    openHomeDocument(
        database, homeId, ShoppingListFeature, nowMillis,
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> ShoppingListDocumentSource(document, pantry, persist, changed) },
        newDocument = { newShoppingListDocument() },
        onLocalChange = onLocalChange,
    ),
)

/**
 * Stores an empty shopping-list document for [homeId], unless one is stored already. For a Home that was created on
 * another device: that device seeds the document's root map, and a second one seeded here would give every device its
 * own competing `items` map, only one of which survives a merge (see [storeEmptyPantry]).
 */
suspend fun storeEmptyShoppingList(
    database: KitsDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val queries = database.automergeDocumentQueries
    val documentId = shoppingListDocumentId(homeId)
    if (queries.selectById(documentId).awaitAsOneOrNull() != null) return
    queries.upsert(documentId, homeId, ShoppingListFeature, Document().save(), nowMillis())
}
