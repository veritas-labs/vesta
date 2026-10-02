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

/** Reads a Home's members from the local [VestaDatabase] as the Members tab shows them: one entry per person. */
class MemberRepository(
    private val database: VestaDatabase,
    private val profiles: ProfileRepository,
) {

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
