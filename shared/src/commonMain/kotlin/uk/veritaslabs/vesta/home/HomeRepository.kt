package uk.veritaslabs.vesta.home

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.ui.screens.HomeDetails
import uk.veritaslabs.vesta.ui.screens.HomeListItem
import uk.veritaslabs.vesta.ui.screens.HomeShape

/** Reads and writes Homes in the local [VestaDatabase]. */
class HomeRepository(
    private val database: VestaDatabase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /** Every Home, re-emitted whenever the Home table changes. */
    val homes: Flow<List<HomeListItem>> = database.homeQueries.selectAll()
        .asFlow()
        .map { query -> query.awaitAsList().map { HomeListItem(id = it.id, name = it.name) } }

    /** The Home with the given [id], or null if there is none (or its stored shape is unrecognised). */
    suspend fun get(id: String): HomeDetails? =
        database.homeQueries.selectById(id).awaitAsOneOrNull()?.let {
            HomeDetails(id = it.id, name = it.name, shape = HomeShape.fromStorageValue(it.shape))
        }

    /** Creates a Home named [name] with the given [shape], returning its new id. */
    suspend fun create(name: String, shape: HomeShape): String {
        val id = Uuid.random().toString()
        database.homeQueries.insert(id, name, shape.storageValue, nowMillis())
        return id
    }

    /** Renames the Home with the given [id] and changes its [shape]. */
    suspend fun update(id: String, name: String, shape: HomeShape) {
        database.homeQueries.updateHome(name, shape.storageValue, id)
    }

    /** Deletes the Home with the given [id]; its members are removed with it. */
    suspend fun delete(id: String) {
        database.homeQueries.deleteById(id)
    }
}
