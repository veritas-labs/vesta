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

    /**
     * Makes [profile] this device's Profile, as the user's other devices already know them: the id and details are
     * taken over from the Profile this device has, and the person's memberships follow the new id
     * (docs/adr/ADR-014-home-membership.md). Does nothing if it already is. Returns the id this device had, if
     * it changed, so the Homes can retire it.
     */
    suspend fun adopt(profile: CurrentProfile): String? = lock.withLock {
        val existing = database.profileQueries.selectAll().awaitAsList().firstOrNull()
        when {
            existing == null -> {
                database.profileQueries.insert(profile.id, profile.displayName, profile.avatarResourceId, "default")
                null
            }
            existing.id == profile.id && existing.displayName == profile.displayName &&
                existing.avatarResourceId == profile.avatarResourceId -> null
            else -> {
                if (existing.id != profile.id) {
                    database.profileQueries.updateId(profile.id, existing.id)
                    database.homeMemberQueries.updateMemberId(profile.id, existing.id)
                    // A row that already existed under the new id made the update skip it; the old one is now a stray.
                    database.homeMemberQueries.deleteMemberships(existing.id)
                }
                database.profileQueries.updateProfile(profile.displayName, profile.avatarResourceId, existing.x0xInstanceName, profile.id)
                database.homeMemberQueries.updateMemberProfile(profile.displayName, profile.avatarResourceId, profile.id)
                existing.id.takeIf { it != profile.id }
            }
        }
    }

    private companion object {
        // Shared by every repository, because the sync service and the UI each make one over the same database and
        // two first-run calls must not each create a Profile.
        val lock = Mutex()
    }
}
