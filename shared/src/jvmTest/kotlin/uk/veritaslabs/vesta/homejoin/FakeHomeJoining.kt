package uk.veritaslabs.vesta.homejoin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

import uk.veritaslabs.kits.spaces.ActiveInvite
import uk.veritaslabs.kits.spaces.InviteLink
import uk.veritaslabs.kits.spaces.JoinRequest
import uk.veritaslabs.kits.spaces.PendingJoin
import uk.veritaslabs.kits.spaces.RedeemResult
import uk.veritaslabs.kits.spaces.RequestResult
import uk.veritaslabs.kits.spaces.SpaceJoining
/** A [SpaceJoining] that records what it is asked and answers from the fields below, for view model tests. */
class FakeHomeJoining : SpaceJoining {
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
    override fun observeJoinRequests(homeId: String): Flow<List<JoinRequest>> = requests.map { list -> list.filter { it.spaceId == homeId } }
    override fun observePendingJoins(): Flow<List<PendingJoin>> = pending
    var createCalls = 0
    override suspend fun createInviteLink(homeId: String): InviteLink? {
        createCalls++
        return created
    }
    override suspend fun inviteLinks(homeId: String) = links.toList()
    override suspend fun revokeInviteLink(homeId: String, id: String) {
        revoked += homeId to id
        links.removeAll { it.id == id }
    }
}
