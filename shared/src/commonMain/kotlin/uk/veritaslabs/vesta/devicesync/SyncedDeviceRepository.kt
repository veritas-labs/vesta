package uk.veritaslabs.vesta.devicesync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.coroutines.asFlow
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.ui.screens.SyncedDevice

/**
 * Reads and writes the user's synced devices in the local [VestaDatabase]. This is only the record of which
 * devices are enrolled; the enrollment itself (ADR-003) calls [add] once a device has been certified.
 */
class SyncedDeviceRepository(
    private val database: VestaDatabase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /** Every synced device, oldest first, re-emitted whenever the table changes. */
    val devices: Flow<List<SyncedDevice>> = database.syncedDeviceQueries.selectAll()
        .asFlow()
        .map { query -> query.awaitAsList().map { SyncedDevice(name = it.name, agentId = it.agentId) } }

    /** Records the device with [agentId] as synced under [name]; adding an existing agent renames it. */
    suspend fun add(name: String, agentId: String) {
        database.syncedDeviceQueries.insert(agentId = agentId, name = name, addedAt = nowMillis())
    }

    /** Forgets the device with [agentId]; a no-op if it isn't synced. */
    suspend fun remove(agentId: String) {
        database.syncedDeviceQueries.deleteByAgentId(agentId)
    }
}
