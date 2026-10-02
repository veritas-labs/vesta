package uk.veritaslabs.vesta.profile

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.getDeviceName

/** The person using the app: their [id] is their member id in every Home (docs/adr/ADR-014-home-membership.md). */
data class CurrentProfile(val id: String, val displayName: String, val avatarResourceId: String?)

/**
 * The Profile of whoever is using this device. Profile setup and switching aren't wired yet, so there is one: the first
 * Profile in the database, created on first use and named after the device until the user can name themselves.
 */
class ProfileRepository(
    private val database: VestaDatabase,
    private val deviceName: () -> String = ::getDeviceName,
) {
    private val lock = Mutex()

    /** The current Profile, creating it if the device has none. */
    suspend fun current(): CurrentProfile = lock.withLock {
        val existing = database.profileQueries.selectAll().awaitAsList().firstOrNull()
        if (existing != null) {
            return@withLock CurrentProfile(existing.id, existing.displayName, existing.avatarResourceId)
        }
        val id = Uuid.random().toString()
        val name = deviceName()
        database.profileQueries.insert(id, name, null, "default")
        CurrentProfile(id, name, null)
    }
}
