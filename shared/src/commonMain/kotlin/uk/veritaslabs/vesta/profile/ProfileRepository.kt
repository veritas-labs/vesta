package uk.veritaslabs.vesta.profile

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.coroutines.asFlow
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.Profile as ProfileRow
import uk.veritaslabs.vesta.getDeviceName

/**
 * The person using the app: their [id] is their member id in every Home (docs/adr/ADR-014-home-membership.md).
 * [named] is false while [displayName] is only a placeholder taken from the device, so the user is yet to choose one.
 */
data class CurrentProfile(
    val id: String,
    val displayName: String,
    val avatarResourceId: String?,
    val named: Boolean = false,
)

/** Whether a display name is acceptable (CORE-EXPERIENCE: 2 to 40 characters once trimmed). */
object ProfileNameRules {
    const val MinLength = 2
    const val MaxLength = 40

    fun isValid(name: String): Boolean = name.trim().length in MinLength..MaxLength
}

/** What happened when the user tried to change their name. */
enum class RenameResult { Renamed, InvalidName }

/**
 * The Profile of whoever is using this device. Profile switching isn't wired yet, so there is one: the first Profile in
 * the database, created on first use named after the device, until the user chooses a name ([rename]). Changes the
 * user makes are reported to [changes].
 */
class ProfileRepository(
    private val database: VestaDatabase,
    private val deviceName: () -> String = ::getDeviceName,
    private val changes: ProfileChangeListener? = null,
) {
    /** The current Profile, creating it if the device has none. */
    suspend fun current(): CurrentProfile = lock.withLock { currentLocked() }

    /** [current], for a caller already holding [lock]. */
    private suspend fun currentLocked(): CurrentProfile {
        val existing = database.profileQueries.selectAll().awaitAsList().firstOrNull()
        if (existing != null) return existing.toCurrent()
        val id = Uuid.random().toString()
        val name = deviceName()
        database.profileQueries.insert(id, name, null, "default")
        return CurrentProfile(id, name, null)
    }

    /** The current Profile, re-emitted whenever it changes; the first Profile is created if there is none yet. */
    fun observe(): Flow<CurrentProfile> = flow {
        current()
        emitAll(database.profileQueries.selectAll().asFlow().map { it.awaitAsList().first().toCurrent() })
    }

    /**
     * Sets the user's display name, which also marks it as chosen. Their memberships in every Home carry the new name,
     * and [changes] is told so it reaches their other devices and the Homes' other members.
     */
    suspend fun rename(name: String): RenameResult {
        if (!ProfileNameRules.isValid(name)) return RenameResult.InvalidName
        val trimmed = name.trim()
        lock.withLock {
            val existing = currentLocked() // a name can be chosen before anything else has made the Profile
            database.profileQueries.updateDetails(trimmed, existing.avatarResourceId, 1L, existing.id)
            database.homeMemberQueries.updateMemberProfile(trimmed, existing.avatarResourceId, existing.id)
        }
        changes?.changed()
        return RenameResult.Renamed
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
                if (profile.named) database.profileQueries.updateDetails(profile.displayName, profile.avatarResourceId, 1L, profile.id)
                null
            }
            // The name chosen here may not have reached the shared Profile yet; a placeholder must not undo it.
            existing.id == profile.id && existing.named == 1L && !profile.named -> null
            existing.id == profile.id && existing.displayName == profile.displayName &&
                existing.avatarResourceId == profile.avatarResourceId && (existing.named == 1L) == profile.named -> null
            else -> {
                if (existing.id != profile.id) {
                    database.profileQueries.updateId(profile.id, existing.id)
                    database.homeMemberQueries.updateMemberId(profile.id, existing.id)
                    // A row that already existed under the new id made the update skip it; the old one is now a stray.
                    database.homeMemberQueries.deleteMemberships(existing.id)
                }
                database.profileQueries.updateDetails(profile.displayName, profile.avatarResourceId, if (profile.named) 1L else 0L, profile.id)
                database.homeMemberQueries.updateMemberProfile(profile.displayName, profile.avatarResourceId, profile.id)
                existing.id.takeIf { it != profile.id }
            }
        }
    }

    private fun ProfileRow.toCurrent() = CurrentProfile(id, displayName, avatarResourceId, named == 1L)

    private companion object {
        // Shared by every repository, because the sync service and the UI each make one over the same database and
        // two first-run calls must not each create a Profile.
        val lock = Mutex()
    }
}
