package uk.veritaslabs.vesta.home

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.MemberListItem
import uk.veritaslabs.vesta.ui.screens.MemberRole

/** What happened when the current user tried to change a member's role. */
enum class RoleChangeResult {
    Changed,

    /** Only an admin of the Home can change roles. */
    NotAnAdmin,

    /** The Home has to keep at least one admin. */
    LastAdmin,

    /** The Home has no such member. */
    NoSuchMember,
}

/**
 * Reads a Home's members from the local [VestaDatabase] as the Members tab shows them (one entry per person), and
 * lets an admin change their roles, telling [changes] about each change.
 */
class MemberRepository(
    private val database: VestaDatabase,
    private val profiles: ProfileRepository,
    private val changes: RosterChangeListener? = null,
) {

    /**
     * Makes [memberId] an admin or an ordinary member of [homeId]. Only an admin can do this, and the Home must keep an
     * admin, so the last one can't be demoted. An admin may step down while another remains.
     */
    suspend fun setRole(homeId: String, memberId: String, role: MemberRole): RoleChangeResult {
        val me = profiles.current().id
        val members = database.homeMemberQueries.selectByHome(homeId).awaitAsList()
        val admin = MemberRole.Admin.storageValue
        if (members.none { it.memberId == me && it.role == admin }) return RoleChangeResult.NotAnAdmin
        val target = members.firstOrNull { it.memberId == memberId } ?: return RoleChangeResult.NoSuchMember
        if (target.role == role.storageValue) return RoleChangeResult.Changed
        if (role == MemberRole.Member && members.count { it.role == admin } == 1) return RoleChangeResult.LastAdmin
        database.homeMemberQueries.updateRole(role.storageValue, homeId, memberId)
        changes?.roleChanged(homeId, memberId, role.storageValue)
        return RoleChangeResult.Changed
    }

    /** The members of [homeId], the current user first and then in the order they joined; re-emitted on change. */
    fun members(homeId: String): Flow<List<MemberListItem>> = flow {
        profiles.current() // make sure there is a Profile to be "you"
        emitAll(
            combine(
                database.profileQueries.selectAll().asFlow().map { it.awaitAsList().firstOrNull()?.id },
                database.homeMemberQueries.selectByHome(homeId).asFlow().map { it.awaitAsList() },
            ) { me, rows ->
                rows
                    .map { row ->
                        MemberListItem(
                            memberId = row.memberId,
                            name = row.name,
                            role = MemberRole.fromStorageValue(row.role),
                            isYou = row.memberId == me,
                        )
                    }
                    .sortedByDescending { it.isYou }
            },
        )
    }
}
