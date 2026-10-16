package uk.veritaslabs.vesta.home

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.spaces.ProfileRepository
import uk.veritaslabs.vesta.home.HomeDetails
import uk.veritaslabs.vesta.home.HomeListItem
import uk.veritaslabs.vesta.home.MemberRole

import uk.veritaslabs.kits.spaces.SpaceChangeListener
import uk.veritaslabs.kits.spaces.StoredSpace
/** What happened when the current user tried to leave a Home. */
enum class LeaveResult {
    /** They left: the Home is gone from this device, and from their other devices as they sync. */
    Left,

    /** They are the Home's only admin and other members remain, so someone else must become an admin first. */
    OnlyAdmin,
}

/** Reads and writes Homes in the local [KitsDatabase], telling [changes] about each change. */
class HomeRepository(
    private val database: KitsDatabase,
    private val changes: SpaceChangeListener? = null,
    private val profiles: ProfileRepository,
    private val rosterChanges: RosterChangeListener? = null,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /** Every Home, re-emitted whenever the Home table changes. */
    val homes: Flow<List<HomeListItem>> = database.homeQueries.selectAll()
        .asFlow()
        .map { query -> query.awaitAsList().map { HomeListItem(id = it.id, name = it.name) } }

    /** The Home with the given [id], or null if there is none . */
    suspend fun get(id: String): HomeDetails? =
        database.homeQueries.selectById(id).awaitAsOneOrNull()?.let {
            HomeDetails(id = it.id, name = it.name, shape = it.shape)
        }

    /**
     * Creates a Home named [name] with the given [shape], returning its new id. The creator, the current Profile, is
     * its first member and its admin (docs/adr/ADR-014-home-membership.md).
     */
    suspend fun create(name: String, shape: String): String {
        val id = Uuid.random().toString()
        val createdAt = nowMillis()
        val creator = profiles.current()
        database.homeQueries.insert(id, name, shape, createdAt)
        database.homeMemberQueries.insert(
            homeId = id,
            memberId = creator.id,
            name = creator.displayName,
            avatarResourceId = creator.avatarResourceId,
            role = MemberRole.Admin.storageValue,
            joinedAt = createdAt,
        )
        changes?.saved(StoredSpace(id, name, shape, createdAt))
        return id
    }

    /** Renames the Home with the given [id] and changes its [shape]. */
    suspend fun update(id: String, name: String, shape: String) {
        database.homeQueries.updateHome(name, shape, id)
        database.homeQueries.selectById(id).awaitAsOneOrNull()
            ?.let { changes?.saved(StoredSpace(it.id, it.name, it.shape, it.createdAt)) }
    }

    /**
     * The current user leaves the Home with the given [id], which removes it from their devices. The only admin of a Home
     * that has other members can't leave ([LeaveResult.OnlyAdmin], CORE-EXPERIENCE §5). Leaving as the only member
     * leaves nobody, so it ends the Home. The other members are told by [rosterChanges] before the Home goes
     * (docs/adr/ADR-014-home-membership.md).
     */
    suspend fun leave(id: String): LeaveResult {
        val me = profiles.current().id
        val members = database.homeMemberQueries.selectByHome(id).awaitAsList()
        val admin = MemberRole.Admin.storageValue
        val iAmAdmin = members.any { it.memberId == me && it.role == admin }
        val otherAdmins = members.any { it.memberId != me && it.role == admin }
        if (iAmAdmin && !otherAdmins && members.any { it.memberId != me }) return LeaveResult.OnlyAdmin
        rosterChanges?.memberLeft(id, me) // before the Home, and its roster, are gone
        delete(id)
        return LeaveResult.Left
    }

    /** Deletes the Home with the given [id]; its members are removed with it. */
    suspend fun delete(id: String) {
        database.homeQueries.deleteById(id)
        changes?.deleted(id)
    }
}
