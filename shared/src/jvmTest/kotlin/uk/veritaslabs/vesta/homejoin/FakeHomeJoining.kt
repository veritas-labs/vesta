package uk.veritaslabs.vesta.homejoin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map

/** A [HomeJoining] that records what it is asked and answers from the fields below, for view model tests. */
class FakeHomeJoining : HomeJoining {
    var created: InviteLink? = null
    var links = mutableListOf<ActiveInvite>()
    val revoked = mutableListOf<Pair<String, String>>()
    var redeemResult = RedeemResult.Started
    val redeemed = mutableListOf<InviteLink>()
    val cancelled = mutableListOf<String>()
    val approved = mutableListOf<Pair<String, String>>()
    val declined = mutableListOf<Pair<String, String>>()
    val requests = MutableStateFlow<List<JoinRequest>>(emptyList())
    val pending = MutableStateFlow<List<PendingJoin>>(emptyList())

    override val joined: SharedFlow<JoinedHome> = MutableSharedFlow()
    var joinLink = JoinLink("agent-me", "member-me", expiresAt = 1_000L, name = "Me")
    override suspend fun openLink() = joinLink
    override fun cancelLink() = Unit
    override suspend fun invite(homeId: String, link: JoinLink): Result<Unit> = Result.success(Unit)
    override suspend fun joinRequests(homeId: String) = requests.value
    override suspend fun approve(homeId: String, memberId: String): RequestResult {
        approved += homeId to memberId
        return RequestResult.Done
    }
    override suspend fun decline(homeId: String, memberId: String): RequestResult {
        declined += homeId to memberId
        return RequestResult.Done
    }
    override suspend fun redeemInvite(link: InviteLink): RedeemResult {
        redeemed += link
        return redeemResult
    }
    override suspend fun pendingJoins() = pending.value
    override suspend fun cancelPendingJoin(homeId: String) {
        cancelled += homeId
    }
    override fun observeJoinRequests(homeId: String): Flow<List<JoinRequest>> = requests.map { list -> list.filter { it.homeId == homeId } }
    override fun observePendingJoins(): Flow<List<PendingJoin>> = pending
    override suspend fun createInviteLink(homeId: String) = created
    override suspend fun inviteLinks(homeId: String) = links.toList()
    override suspend fun revokeInviteLink(homeId: String, id: String) {
        revoked += homeId to id
        links.removeAll { it.id == id }
    }
}
