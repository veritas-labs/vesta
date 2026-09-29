package uk.veritaslabs.vesta.home

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.ui.screens.HomeListItem

/** Reads Homes from the local [VestaDatabase]. */
class HomeRepository(private val database: VestaDatabase) {

    /** Every Home, re-emitted whenever the Home table changes. */
    val homes: Flow<List<HomeListItem>> = database.homeQueries.selectAll()
        .asFlow()
        .map { query -> query.awaitAsList().map { HomeListItem(id = it.id, name = it.name) } }
}
