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
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.ui.screens.Merchant
import uk.veritaslabs.vesta.ui.screens.MerchantsSource

private const val MerchantsFeature = "merchants"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s merchants document. */
fun merchantsDocumentId(homeId: String): String = homeDocumentId(homeId, MerchantsFeature)

/**
 * A [MerchantsSource] backed by a merchants Automerge [document] (ADR-026). Merchants are keys of the document's root map, so there is no
 * root map to seed (and race over) on a Home that has existed since before merchants. Same threading contract as
 * [ShoppingListDocumentSource]: route every read or write of [document] through this class.
 *
 * Use [openMerchantsDocumentSource] to get one that loads and saves a Home's document in [KitsDatabase].
 */
@OptIn(ExperimentalUuidApi::class)
class MerchantsDocumentSource private constructor(
    private val state: MutableStateFlow<List<Merchant>>,
    private val core: AutomergeDocumentCore,
    private val newKey: () -> String,
) : MerchantsSource, HomeDocument by core {

    constructor(
        document: Document,
        persist: suspend (ByteArray) -> Unit = {},
        onLocalChange: () -> Unit = {},
        newKey: () -> String = { Uuid.random().toString() },
    ) : this(MutableStateFlow(document.readMerchants()), document, persist, onLocalChange, newKey)

    private constructor(
        state: MutableStateFlow<List<Merchant>>,
        document: Document,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
        newKey: () -> String,
    ) : this(state, AutomergeDocumentCore(document, { state.value = document.readMerchants() }, persist, onLocalChange), newKey)

    override val merchants: Flow<List<Merchant>> = state

    override suspend fun save(merchant: Merchant): String {
        // A new merchant's key is chosen up front so one saved before the document has arrived keeps the key we return.
        val key = merchant.key.ifBlank { newKey() }
        core.edit { if (merchant.key.isBlank() || !updateMerchant(this, key, merchant)) addMerchant(this, key, merchant) }
        return key
    }

    override suspend fun remove(key: String) = core.edit { removeMerchant(this, key) }

    /** Republishes and persists after the document changed outside [save]/[remove], e.g. by applying a sync message. */
    suspend fun refresh() = core.refresh()
}

/**
 * Opens [homeId]'s merchants as a [MerchantsDocumentSource] persisted in [database]'s `AutomergeDocument` table
 * (ADR-007): loads the stored document if there is one, otherwise starts a new empty merchants and stores it straight
 * away. The [homeId] must be an existing `Home` row.
 */
suspend fun openMerchantsDocumentSource(
    database: KitsDatabase,
    homeId: String,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    onLocalChange: () -> Unit = {},
): MerchantsDocumentSource = checkNotNull(
    openHomeDocument(
        database, homeId, MerchantsFeature, nowMillis,
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> MerchantsDocumentSource(document, persist, changed) },
        onLocalChange = onLocalChange,
    ),
)
