package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import kotlinx.coroutines.flow.SharedFlow
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.docs.HomeDocumentStore

/** One shared [MerchantsDocumentSource] per Home (see [PantrySources] for why there must be exactly one). */
class MerchantsSources(database: KitsDatabase) {

    private val store = HomeDocumentStore(
        database, "merchants", { Clock.System.now().toEpochMilliseconds() },
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> MerchantsDocumentSource(document, persist, changed) },
    )

    /** The id of a Home each time its merchants is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = store.localChanges

    /** [homeId]'s merchants source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): MerchantsDocumentSource? = store.forHome(homeId)
}
