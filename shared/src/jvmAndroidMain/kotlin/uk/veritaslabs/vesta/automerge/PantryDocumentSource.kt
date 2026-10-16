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
import uk.veritaslabs.vesta.ui.screens.MerchantLinkChange
import uk.veritaslabs.vesta.ui.screens.PantryCategoriesSource
import uk.veritaslabs.vesta.ui.screens.PantryCategoryGroup
import uk.veritaslabs.vesta.ui.screens.PantryContents
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails
import uk.veritaslabs.vesta.ui.screens.PantryListItem
import uk.veritaslabs.vesta.ui.screens.PantrySource

private const val PantryFeature = "pantry"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s Pantry document. */
fun pantryDocumentId(homeId: String): String = homeDocumentId(homeId, PantryFeature)

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
 * Use [openPantryDocumentSource] to get one that loads and saves a Home's document in [KitsDatabase].
 */
class PantryDocumentSource private constructor(
    private val state: MutableStateFlow<PantryContents>,
    private val categoryState: MutableStateFlow<List<PantryCategoryGroup>>,
    private val core: AutomergeDocumentCore,
) : PantrySource, PantryCategoriesSource, HomeDocument by core {

    constructor(
        document: Document,
        persist: suspend (ByteArray) -> Unit = {},
        onLocalChange: () -> Unit = {},
    ) : this(MutableStateFlow(document.toPantryContents()), MutableStateFlow(document.toCategoryGroups()), document, persist, onLocalChange)

    private constructor(
        state: MutableStateFlow<PantryContents>,
        categoryState: MutableStateFlow<List<PantryCategoryGroup>>,
        document: Document,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
    ) : this(
        state,
        categoryState,
        AutomergeDocumentCore(
            document,
            { state.value = document.toPantryContents(); categoryState.value = document.toCategoryGroups() },
            persist,
            onLocalChange,
        ),
    )

    override val contents: Flow<PantryContents> = state

    override val categoriesSource: PantryCategoriesSource get() = this

    override val categories: Flow<List<PantryCategoryGroup>> = categoryState

    // Edits made while this is still an unseeded placeholder (see [isPantrySeeded]); applied, in order, once the creator's
    // document has arrived. Kept in memory only, so an edit made before the seed arrives is lost if the app is closed first.
    private val pending = mutableListOf<(Document) -> Unit>()

    /**
     * Runs [block] as an edit: now when the document is seeded, otherwise once it is. Unlike [mutate] it never writes to
     * an unseeded placeholder, where it would create root maps that compete with the creator's and lose items on merge.
     */
    suspend fun edit(block: (Document) -> Unit) {
        val queued = core.read { if (isPantrySeeded()) false else { pending += block; true } }
        if (!queued) mutate(block)
    }

    /** Runs [block] against the document (e.g. `upsertPantryItem(it, …)`), then republishes and persists it. */
    suspend fun <T> mutate(block: (Document) -> T): T = core.edit { block(this) }

    /** Applies a sync message from a peer, then republishes and persists the document if it changed. */
    override suspend fun receiveSync(state: SyncState, message: ByteArray) {
        core.receiveSync(state, message)
        flushPending()
    }

    /** Applies changes from [changesSince], as [receiveSync] does a sync message. Applying one again changes nothing. */
    override suspend fun applyChanges(changes: ByteArray) {
        core.applyChanges(changes)
        flushPending()
    }

    private suspend fun flushPending() {
        // The creator's document may just have arrived: the edits made before it can now be applied on top of it.
        val ready = core.read {
            if (pending.isNotEmpty() && isPantrySeeded()) pending.toList().also { pending.clear() } else emptyList()
        }
        ready.forEach { mutate(it) }
    }

    override suspend fun save(item: PantryItemDetails) {
        edit { doc ->
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
            // Editing the name/barcode moves the entry to a new dedup key; drop the old one, taking its merchants along
            // unless the edit set them.
            val moved = item.key?.takeIf { it != key }
            val merchants = item.merchantKeys ?: moved?.let { doc.readPantryItem(it)?.merchantKeys }
            merchants?.let { setPantryItemMerchants(doc, key, it) }
            moved?.let { deletePantryItem(doc, it) }
        }
    }

    override suspend fun changeMerchants(itemKeys: Set<String>, change: MerchantLinkChange) {
        edit { changePantryMerchants(it, itemKeys, change) }
    }

    override suspend fun delete(key: String) {
        edit { deletePantryItem(it, key) }
    }

    override suspend fun addPrimary(name: String) = edit { addPrimaryCategory(it, name) }

    override suspend fun addSecondary(primary: String, name: String) =
        edit { addSecondaryCategory(it, primary, name) }

    override suspend fun deletePrimary(name: String) = edit { deletePrimaryCategory(it, name) }

    override suspend fun deleteSecondary(primary: String, name: String) =
        edit { deleteSecondaryCategory(it, primary, name) }

    /** Republishes and persists after the document changed outside [mutate], e.g. by applying a sync message. */
    suspend fun refresh() = core.refresh()
}

private fun Document.toCategoryGroups() = readPantryCategories().map { PantryCategoryGroup(it.name, it.secondary) }

private fun Document.toPantryContents() = PantryContents(
    items = readPantry().map {
        PantryListItem(
            key = it.id,
            primaryCategory = it.primaryCategory,
            secondaryCategory = it.secondaryCategory,
            title = it.title,
            packageSize = it.packageSize,
            stockCount = it.stockCount,
            barcode = it.barcode,
            merchantKeys = it.merchantKeys,
        )
    },
)

/**
 * Opens [homeId]'s Pantry as a [PantryDocumentSource] persisted in [database]'s `AutomergeDocument`
 * table (ADR-007): loads the stored document if there is one, otherwise starts a new empty Pantry and
 * stores it straight away, so the document's identity survives a restart before its first edit. The
 * [homeId] must be an existing `Home` row. A Home that came from another device is given an empty stored
 * document instead (see `storeEmptyPantry`), so it is filled from the one its creator seeded.
 */
suspend fun openPantryDocumentSource(
    database: KitsDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    onLocalChange: () -> Unit = {},
): PantryDocumentSource = checkNotNull(
    openHomeDocument(
        database, homeId, PantryFeature, nowMillis,
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed ->
            migratePantryCategories(document)
            PantryDocumentSource(document, persist, changed)
        },
        newDocument = { newPantryDocument() },
        onLocalChange = onLocalChange,
    ),
)

/**
 * Stores an empty Pantry document for [homeId], unless one is stored already. For a Home that was created on another
 * device: that device seeds the document (root maps and default categories), and seeding a second one here would
 * give every device its own competing `items` and `categories` maps, only one of which survives a merge. An empty
 * document takes everything from the creator's.
 */
suspend fun storeEmptyPantry(
    database: KitsDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val queries = database.automergeDocumentQueries
    val documentId = pantryDocumentId(homeId)
    if (queries.selectById(documentId).awaitAsOneOrNull() != null) return
    queries.upsert(documentId, homeId, PantryFeature, Document().save(), nowMillis())
}
