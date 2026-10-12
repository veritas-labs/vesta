package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase

/** One shared [MerchantsDocumentSource] per Home (see [PantrySources] for why there must be exactly one). */
class MerchantsSources(private val database: VestaDatabase) {

    private val lock = Mutex()
    private val sources = mutableMapOf<String, MerchantsDocumentSource>()
    private val _localChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Home each time its merchants is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = _localChanges.asSharedFlow()

    /** [homeId]'s merchants source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): MerchantsDocumentSource? = lock.withLock {
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) {
            sources.remove(homeId)
            return@withLock null
        }
        sources[homeId] ?: openMerchantsDocumentSource(database, homeId, onLocalChange = { _localChanges.tryEmit(homeId) })
            .also { sources[homeId] = it }
    }
}
