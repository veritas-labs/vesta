package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.mailbox.changesSince
import uk.veritaslabs.vesta.mailbox.encodeHeads
import uk.veritaslabs.vesta.ui.screens.CookbookSource
import uk.veritaslabs.vesta.ui.screens.Recipe

private const val CookbookFeature = "cookbook"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s cookbook document. */
fun cookbookDocumentId(homeId: String): String = "$homeId:$CookbookFeature"

/**
 * A [CookbookSource] backed by a cookbook Automerge [document] (ADR-022). Recipes are keys of the document's root map, so there is no
 * root map to seed (and race over) on a Home that has existed since before the cookbook. Same threading contract as
 * [ShoppingListDocumentSource]: route every read or write of [document] through this class.
 *
 * Use [openCookbookDocumentSource] to get one that loads and saves a Home's document in [VestaDatabase].
 */
@OptIn(ExperimentalUuidApi::class)
class CookbookDocumentSource(
    private val document: Document,
    private val persist: suspend (ByteArray) -> Unit = {},
    private val onLocalChange: () -> Unit = {},
    private val newKey: () -> String = { Uuid.random().toString() },
) : CookbookSource {

    private val lock = Mutex()
    private val state = MutableStateFlow(document.readCookbook())

    override val recipes: Flow<List<Recipe>> = state

    override suspend fun save(recipe: Recipe): String {
        // A new recipe's key is chosen up front so a recipe saved before the cookbook has arrived keeps the key we return.
        val key = recipe.key.ifBlank { newKey() }
        lock.withLock {
            if (recipe.key.isBlank() || !updateRecipe(document, key, recipe)) addRecipe(document, key, recipe)
            publishAndPersist()
        }
        onLocalChange()
        return key
    }

    override suspend fun remove(key: String) = edit { removeRecipe(document, key) }

    override suspend fun setRating(key: String, rating: Int) = edit { setRecipeRating(document, key, rating) }

    override suspend fun setFavourite(key: String, favourite: Boolean) = edit { setRecipeFavourite(document, key, favourite) }

    private suspend fun edit(change: () -> Unit) {
        lock.withLock {
            change()
            publishAndPersist()
        }
        onLocalChange()
    }

    /** The next Automerge sync message for the peer whose progress is [state], or null when it is up to date. */
    suspend fun generateSync(state: SyncState): ByteArray? = lock.withLock {
        document.generateSyncMessage(state).orElse(null)
    }

    /** Applies a sync message from a peer, then republishes and persists the cookbook if it changed. */
    suspend fun receiveSync(state: SyncState, message: ByteArray) = applyIncoming { receiveSyncMessage(state, message) }

    /** The cookbook's current heads, saved as [encodeHeads] writes them (for the mailbox, ADR-020). */
    suspend fun heads(): ByteArray = lock.withLock { encodeHeads(document.heads) }

    /** The changes made since the heads [saved] by [heads] (everything, for none), for another device's [applyChanges]. */
    suspend fun changesSince(saved: ByteArray): ByteArray = lock.withLock { document.changesSince(saved) }

    /** Applies changes from [changesSince], as [receiveSync] does a sync message. Applying one again changes nothing. */
    suspend fun applyChanges(changes: ByteArray) = applyIncoming { applyEncodedChanges(changes) }

    private suspend fun applyIncoming(apply: Document.() -> Unit) {
        lock.withLock {
            val before = document.heads
            document.apply()
            if (!document.heads.contentEquals(before)) publishAndPersist()
        }
    }

    /** Republishes and persists after [document] changed outside [save]/[remove], e.g. by applying a sync message. */
    suspend fun refresh() = lock.withLock { publishAndPersist() }

    private suspend fun publishAndPersist() {
        state.value = document.readCookbook()
        persist(document.save())
    }
}

/**
 * Opens [homeId]'s cookbook as a [CookbookDocumentSource] persisted in [database]'s `AutomergeDocument` table
 * (ADR-007): loads the stored document if there is one, otherwise starts a new empty cookbook and stores it straight
 * away. The [homeId] must be an existing `Home` row.
 */
suspend fun openCookbookDocumentSource(
    database: VestaDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    onLocalChange: () -> Unit = {},
): CookbookDocumentSource {
    val documentId = cookbookDocumentId(homeId)
    val queries = database.automergeDocumentQueries
    val stored = queries.selectById(documentId).awaitAsOneOrNull()
    val document = Document.load(stored?.bytes ?: Document().save())
    val source = CookbookDocumentSource(
        document,
        persist = { bytes -> queries.upsert(documentId, homeId, CookbookFeature, bytes, nowMillis()) },
        onLocalChange = onLocalChange,
    )
    if (stored == null) source.refresh()
    return source
}
