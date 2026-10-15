package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.automerge.Document
import uk.veritaslabs.vesta.docs.AutomergeDocumentCore
import uk.veritaslabs.vesta.docs.HomeDocument
import uk.veritaslabs.vesta.docs.homeDocumentId
import uk.veritaslabs.vesta.docs.openHomeDocument
import uk.veritaslabs.vesta.blobs.BlobReference
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.ui.screens.CookbookSource
import uk.veritaslabs.vesta.ui.screens.Recipe

private const val CookbookFeature = "cookbook"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s cookbook document. */
fun cookbookDocumentId(homeId: String): String = homeDocumentId(homeId, CookbookFeature)

/**
 * A [CookbookSource] backed by a cookbook Automerge [document] (ADR-022). Recipes are keys of the document's root map, so there is no
 * root map to seed (and race over) on a Home that has existed since before the cookbook. Same threading contract as
 * [ShoppingListDocumentSource]: route every read or write of [document] through this class.
 *
 * Use [openCookbookDocumentSource] to get one that loads and saves a Home's document in [VestaDatabase].
 */
@OptIn(ExperimentalUuidApi::class)
class CookbookDocumentSource private constructor(
    private val state: MutableStateFlow<List<Recipe>>,
    private val core: AutomergeDocumentCore,
    private val newKey: () -> String,
) : CookbookSource, HomeDocument by core {

    constructor(
        document: Document,
        persist: suspend (ByteArray) -> Unit = {},
        onLocalChange: () -> Unit = {},
        newKey: () -> String = { Uuid.random().toString() },
    ) : this(MutableStateFlow(document.readCookbook()), document, persist, onLocalChange, newKey)

    private constructor(
        state: MutableStateFlow<List<Recipe>>,
        document: Document,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
        newKey: () -> String,
    ) : this(state, AutomergeDocumentCore(document, { state.value = document.readCookbook() }, persist, onLocalChange), newKey)

    override val recipes: Flow<List<Recipe>> = state

    /** Every blob the cookbook currently refers to (ADR-023), so a device can tell which photos it still lacks. */
    fun blobReferences(): Set<BlobReference> = state.value.blobReferences()

    override suspend fun save(recipe: Recipe): String {
        // A new recipe's key is chosen up front so a recipe saved before the cookbook has arrived keeps the key we return.
        val key = recipe.key.ifBlank { newKey() }
        core.edit { if (recipe.key.isBlank() || !updateRecipe(this, key, recipe)) addRecipe(this, key, recipe) }
        return key
    }

    override suspend fun remove(key: String) = edit { removeRecipe(this, key) }

    override suspend fun setRating(key: String, rating: Int) = edit { setRecipeRating(this, key, rating) }

    override suspend fun setFavourite(key: String, favourite: Boolean) = edit { setRecipeFavourite(this, key, favourite) }

    private suspend fun edit(change: Document.() -> Unit) = core.edit(change)

    /** Republishes and persists after the document changed outside [save]/[remove], e.g. by applying a sync message. */
    suspend fun refresh() = core.refresh()
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
): CookbookDocumentSource = checkNotNull(
    openHomeDocument(
        database, homeId, CookbookFeature, nowMillis,
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> CookbookDocumentSource(document, persist, changed) },
        onLocalChange = onLocalChange,
    ),
)
