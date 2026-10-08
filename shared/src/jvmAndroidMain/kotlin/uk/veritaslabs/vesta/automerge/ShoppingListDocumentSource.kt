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
import uk.veritaslabs.vesta.mailbox.changesSince
import uk.veritaslabs.vesta.mailbox.encodeHeads
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

    // Saves and removals made while this list is still an unseeded placeholder (see [isShoppingListSeeded]); run, in order,
    // once the creator's list has arrived. In memory only, so one made before then is lost if the app is closed first.
    private val pending = mutableListOf<suspend () -> Unit>()

    private suspend fun queuedUntilSeeded(operation: suspend () -> Unit): Boolean = lock.withLock {
        if (document.isShoppingListSeeded()) false else { pending += operation; true }
    }

    override suspend fun save(item: ShoppingItemDetails) {
        if (queuedUntilSeeded { save(item) }) return
        val key = lock.withLock {
            val key = if (item.key == null) {
                addShoppingItem(
                    document, item.primaryCategory, item.secondaryCategory, item.title,
                    item.quantity, item.packageSize, item.barcode, item.tags,
                )
            } else {
                updateShoppingItem(
                    document, item.key, item.primaryCategory, item.secondaryCategory, item.title,
                    item.quantity, item.packageSize, item.barcode, item.tags,
                )
            }
            publishAndPersist()
            key
        }
        if (key == null) return
        // The Pantry is written after the list: a crash in between leaves an item the Pantry doesn't know yet, which
        // the next add or edit of it repairs, rather than a Pantry entry for something never listed.
        pantry.edit { doc ->
            ensurePantryCategories(doc, item.primaryCategory, item.secondaryCategory)
            ensurePantryItem(
                doc, item.primaryCategory, item.secondaryCategory, item.title, item.packageSize, item.barcode,
                countUse = item.key == null,
                refreshFields = item.key != null,
            )
        }
        onLocalChange()
    }

    override suspend fun remove(key: String) {
        if (queuedUntilSeeded { remove(key) }) return
        lock.withLock {
            removeShoppingItem(document, key)
            publishAndPersist()
        }
        onLocalChange()
    }

    override suspend fun setProgress(key: String, got: Int, outOfStock: Boolean) {
        if (queuedUntilSeeded { setProgress(key, got, outOfStock) }) return
        lock.withLock {
            setShoppingItemProgress(document, key, got, outOfStock)
            publishAndPersist()
        }
        onLocalChange()
    }

    /** The next Automerge sync message for the peer whose progress is [state], or null when it is up to date. */
    suspend fun generateSync(state: SyncState): ByteArray? = lock.withLock {
        document.generateSyncMessage(state).orElse(null)
    }

    /** Applies a sync message from a peer, then republishes and persists the list if it changed. */
    suspend fun receiveSync(state: SyncState, message: ByteArray) = applyIncoming { receiveSyncMessage(state, message) }

    /** The list's current heads, saved as [encodeHeads] writes them (for the mailbox, ADR-020). */
    suspend fun heads(): ByteArray = lock.withLock { encodeHeads(document.heads) }

    /** The changes made since the heads [saved] by [heads] (everything, for none), for another device's [applyChanges]. */
    suspend fun changesSince(saved: ByteArray): ByteArray = lock.withLock { document.changesSince(saved) }

    /** Applies changes from [changesSince], as [receiveSync] does a sync message. Applying one again changes nothing. */
    suspend fun applyChanges(changes: ByteArray) = applyIncoming { applyEncodedChanges(changes) }

    private suspend fun applyIncoming(apply: Document.() -> Unit) {
        val ready = lock.withLock {
            val before = document.heads
            document.apply()
            if (!document.heads.contentEquals(before)) publishAndPersist()
            // The creator's list may just have arrived: what was saved or removed before it can now be done on top of it.
            if (pending.isNotEmpty() && document.isShoppingListSeeded()) pending.toList().also { pending.clear() } else emptyList()
        }
        ready.forEach { it() }
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
            tags = it.tags,
            got = it.got,
            outOfStock = it.outOfStock,
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

/**
 * Stores an empty shopping-list document for [homeId], unless one is stored already. For a Home that was created on
 * another device: that device seeds the document's root map, and a second one seeded here would give every device its
 * own competing `items` map, only one of which survives a merge (see [storeEmptyPantry]).
 */
suspend fun storeEmptyShoppingList(
    database: VestaDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val queries = database.automergeDocumentQueries
    val documentId = shoppingListDocumentId(homeId)
    if (queries.selectById(documentId).awaitAsOneOrNull() != null) return
    queries.upsert(documentId, homeId, ShoppingListFeature, Document().save(), nowMillis())
}
