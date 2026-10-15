package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.automerge.storeEmptyPantry
import uk.veritaslabs.vesta.automerge.storeEmptyShoppingList
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.home.StoredHome
import uk.veritaslabs.vesta.automerge.InviteStatus
import uk.veritaslabs.vesta.automerge.inviteSecretHash
import uk.veritaslabs.vesta.homejoin.ActiveInvite
import uk.veritaslabs.vesta.homejoin.HomeJoinMessage
import uk.veritaslabs.vesta.homejoin.HomeJoining
import uk.veritaslabs.vesta.homejoin.InviteLink
import uk.veritaslabs.vesta.homejoin.JoinRequest
import uk.veritaslabs.vesta.homejoin.PendingJoin
import uk.veritaslabs.vesta.homejoin.RedeemResult
import uk.veritaslabs.vesta.homejoin.RequestResult
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.home.MemberRole
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

private val InviteLifetime = 7.days
private const val PendingStatus = "pending"
private const val ApprovedStatus = "approved"
private const val DeclinedStatus = "declined"

/**
 * Joining a Home with an invite link (docs/adr/ADR-018-invite-links.md, ADR-019), entirely over x0x direct messages: no x0x
 * group is involved, because a Home's sync is already limited to the devices its roster lists (ADR-014) and every direct
 * message is end-to-end encrypted to its recipient.
 *
 * - **Admin**: makes invite links ([createInviteLink]), is asked to let people in ([HomeJoinMessage.Request]), and
 *   [approve]s or [decline]s each, sending an Invite until the person answers Accepted and joins the roster.
 * - **Joiner** ([redeemInvite]): asks the admins a link names, every sync round, until one of them sends the Invite, which
 *   creates the Home here, puts the inviter and this user in its roster, and is answered with Accepted.
 */
internal class HomeJoinService(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val rosters: HomeRosters,
    private val homes: HomeChangeListener,
    private val profiles: ProfileRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val messageLifetime: Duration = 14.days,
    private val recorder: SyncEventRecorder? = null,
) : HomeJoining {

    // Serialises the work an Invite does, so two copies of one can't both create the Home.
    private val lock = Mutex()

    /** Handles a direct message if it is a Home-join message; returns whether it was one, so the caller can stop there. */
    suspend fun handle(message: X0xDirectMessage): Boolean {
        val decoded = HomeJoinMessage.decode(message.payload) ?: return false
        if (message.verified != true) return true
        val sender = message.sender ?: return true
        when (decoded) {
            is HomeJoinMessage.Invite -> acceptInvite(sender, decoded)
            is HomeJoinMessage.Accepted -> completeApproved(sender, decoded.homeId)
            is HomeJoinMessage.Left -> {
                acceptLeft(sender, decoded)
                acknowledge(sender, decoded.homeId, HomeJoinMessage.LeftKind)
            }
            is HomeJoinMessage.Removed -> {
                acceptRemoved(sender, decoded)
                acknowledge(sender, decoded.homeId, HomeJoinMessage.RemovedKind)
            }
            is HomeJoinMessage.Ack -> {
                database.pendingMessageQueries.delete(sender, decoded.homeId, decoded.of)
                recorder?.membership(decoded.homeId, sender, "acknowledged")
            }
            is HomeJoinMessage.Request -> receiveRequest(sender, decoded)
            is HomeJoinMessage.Denied -> receiveDenied(sender, decoded)
        }
        return true
    }

    /**
     * An admin removes [memberId] from [homeId]: they are tombstoned in the roster, which stops the Home syncing with
     * their devices, and those devices are told so they can drop the Home (best effort: one that is offline isn't).
     */
    suspend fun removeMember(homeId: String, memberId: String) {
        val devices = rosters.devices(homeId)[memberId].orEmpty()
        rosters.remove(homeId, memberId)
        val message = HomeJoinMessage.encode(HomeJoinMessage.Removed(homeId))
        devices.forEach {
            send(it, homeId, HomeJoinMessage.RemovedKind, message)
            recorder?.membership(homeId, it, "removed")
        }
    }

    /** The current user, [memberId], leaves [homeId]: the devices of the other members are told (best effort). */
    suspend fun announceLeft(homeId: String, memberId: String) {
        val others = rosters.devices(homeId).filterKeys { it != memberId }.values.flatten().toSet()
        val message = HomeJoinMessage.encode(HomeJoinMessage.Left(homeId, memberId))
        others.forEach { send(it, homeId, HomeJoinMessage.LeftKind, message) }
    }

    /**
     * Resends every message still waiting for an acknowledgement, and forgets those that have waited too long. Called
     * every sync round: that is how a device that was offline when it was sent gets it once it is back.
     */
    suspend fun resendPending() {
        database.pendingMessageQueries.deleteOlderThan(nowMillis() - messageLifetime.inWholeMilliseconds)
        database.pendingMessageQueries.selectAll().awaitAsList().forEach { sendQuietly(it.recipient, it.payload) }
        database.joinRequestQueries.deleteOlderThan(nowMillis() - messageLifetime.inWholeMilliseconds)
        database.joinRequestQueries.selectApproved().awaitAsList().forEach { request ->
            inviteMessage(request.homeId)?.let { sendQuietly(request.agentId, it) }
        }
        resendJoinRequests()
    }

    override suspend fun redeemInvite(link: InviteLink): RedeemResult {
        val now = nowMillis()
        if (link.isExpired(now)) return RedeemResult.Expired
        if (database.homeQueries.selectById(link.homeId).awaitAsOneOrNull() != null) return RedeemResult.AlreadyInHome
        database.pendingJoinQueries.upsert(link.homeId, link.homeName, link.secret, link.adminAgentIds.joinToString(","), link.expiresAt, now)
        database.pendingJoinQueries.selectById(link.homeId).awaitAsOneOrNull()?.let { sendJoinRequest(it.homeId, it.secret, it.agents()) }
        return RedeemResult.Started
    }

    override suspend fun pendingJoins(): List<PendingJoin> =
        database.pendingJoinQueries.selectAll().awaitAsList().map { PendingJoin(it.homeId, it.homeName, it.expiresAt, it.denial) }

    override fun observeJoinRequests(homeId: String): Flow<List<JoinRequest>> =
        database.joinRequestQueries.selectUndecidedOrApprovedByHome(homeId).asFlow().map { query ->
            query.awaitAsList().map { JoinRequest(it.homeId, it.memberId, it.name, it.createdAt, approved = it.status == ApprovedStatus) }
        }

    override fun observePendingJoins(): Flow<List<PendingJoin>> =
        database.pendingJoinQueries.selectAll().asFlow().map { query ->
            query.awaitAsList().map { PendingJoin(it.homeId, it.homeName, it.expiresAt, it.denial) }
        }

    override suspend fun createInviteLink(homeId: String): InviteLink? {
        val ownAgent = runCatching { client.agent().agentId }.getOrNull()
        return rosters.createInvite(homeId, profiles.current().id, InviteLifetime.inWholeMilliseconds, ownAgentId = ownAgent)
    }

    override suspend fun inviteLinks(homeId: String): List<ActiveInvite> {
        if (!isAdminOf(homeId)) return emptyList()
        val now = nowMillis()
        return rosters.invites(homeId)
            .filter { !it.revoked && now < it.expiresAt && it.usedBy.size < it.maxUses }
            .map { ActiveInvite(it.secretHash, it.expiresAt, it.usedBy.size, it.maxUses) }
    }

    override suspend fun revokeInviteLink(homeId: String, id: String) {
        if (isAdminOf(homeId)) rosters.revokeInvite(homeId, id)
    }

    override suspend fun cancelPendingJoin(homeId: String) {
        database.pendingJoinQueries.delete(homeId)
    }

    /**
     * Asks the admins of every invite link this user is waiting on again, unless it has expired or was refused. A pending
     * join is kept after its link expires for as long as an admin might still resend an approved Invite, then forgotten,
     * as is one whose Home has arrived some other way (from another of the user's devices).
     */
    private suspend fun resendJoinRequests() {
        val now = nowMillis()
        database.pendingJoinQueries.deleteExpiredBefore(now - messageLifetime.inWholeMilliseconds)
        database.pendingJoinQueries.selectAll().awaitAsList().forEach { join ->
            when {
                database.homeQueries.selectById(join.homeId).awaitAsOneOrNull() != null -> database.pendingJoinQueries.delete(join.homeId)
                join.denial == null && now < join.expiresAt -> sendJoinRequest(join.homeId, join.secret, join.agents())
            }
        }
    }

    private suspend fun sendJoinRequest(homeId: String, secret: String, adminAgents: List<String>) {
        val me = profiles.current()
        val agentId = runCatching { client.agent().agentId }.getOrNull() ?: return
        val message = HomeJoinMessage.encode(HomeJoinMessage.Request(homeId, secret, me.id, me.displayName, agentId))
        adminAgents.forEach { sendQuietly(it, message) }
    }

    /** An admin refused a request of ours: remembered, believed only from an agent the invite link named. */
    private suspend fun receiveDenied(sender: String, denied: HomeJoinMessage.Denied) {
        val join = database.pendingJoinQueries.selectById(denied.homeId).awaitAsOneOrNull() ?: return
        if (sender in join.agents()) database.pendingJoinQueries.setDenial(denied.reason, denied.homeId)
    }

    private fun uk.veritaslabs.vesta.db.PendingJoin.agents() = adminAgents.split(",").filter { it.isNotBlank() }

    override suspend fun joinRequests(homeId: String): List<JoinRequest> =
        database.joinRequestQueries.selectUndecidedOrApprovedByHome(homeId).awaitAsList().map {
            JoinRequest(it.homeId, it.memberId, it.name, it.createdAt, approved = it.status == ApprovedStatus)
        }

    override suspend fun approve(homeId: String, memberId: String): RequestResult {
        if (!isAdminOf(homeId)) return RequestResult.NotAnAdmin
        val request = database.joinRequestQueries.selectById(homeId, memberId).awaitAsOneOrNull()
            ?.takeIf { it.status != DeclinedStatus } ?: return RequestResult.NoSuchRequest
        val status = rosters.inviteStatus(homeId, request.secretHash, memberId)
        if (status != InviteStatus.Valid) {
            // Revoked, spent or expired while it waited, or gone from the roster.
            database.joinRequestQueries.delete(homeId, memberId)
            status?.let { denial(it) }?.let { sendQuietly(request.agentId, deniedMessage(homeId, it)) }
            return RequestResult.InviteNoLongerValid
        }
        database.joinRequestQueries.setStatus(ApprovedStatus, homeId, memberId)
        inviteMessage(homeId)?.let { sendQuietly(request.agentId, it) }
        return RequestResult.Done
    }

    override suspend fun decline(homeId: String, memberId: String): RequestResult {
        if (!isAdminOf(homeId)) return RequestResult.NotAnAdmin
        val request = database.joinRequestQueries.selectById(homeId, memberId).awaitAsOneOrNull()
            ?.takeIf { it.status == PendingStatus } ?: return RequestResult.NoSuchRequest
        database.joinRequestQueries.setStatus(DeclinedStatus, homeId, memberId)
        sendQuietly(request.agentId, deniedMessage(homeId, HomeJoinMessage.DeniedDeclined))
        return RequestResult.Done
    }

    /**
     * Someone says they opened an invite link of [request]'s Home. Believed only from the agent it names, and only
     * if this user is an admin of the Home. A member who is already in sends it again when our Invite was lost, so they
     * get it again. Otherwise the secret has to be valid: a wrong or unknown one is ignored, so a stranger learns nothing,
     * a known but unusable one is told why, and a good one waits for an admin to approve it.
     */
    private suspend fun receiveRequest(sender: String, request: HomeJoinMessage.Request) {
        val homeId = request.homeId
        if (sender != request.agentId || !isAdminOf(homeId)) return
        if (database.homeMemberQueries.selectByHome(homeId).awaitAsList().any { it.memberId == request.memberId }) {
            if (sender in rosters.devices(homeId)[request.memberId].orEmpty()) inviteMessage(homeId)?.let { sendQuietly(sender, it) }
            return
        }
        val hash = inviteSecretHash(request.secret)
        val status = rosters.inviteStatus(homeId, hash, request.memberId) ?: return
        denial(status)?.let { return sendQuietly(sender, deniedMessage(homeId, it)) }
        when (database.joinRequestQueries.selectById(homeId, request.memberId).awaitAsOneOrNull()?.status) {
            null -> database.joinRequestQueries.insertPending(
                homeId, request.memberId, request.name, sender, hash, nowMillis(),
            )
            DeclinedStatus -> sendQuietly(sender, deniedMessage(homeId, HomeJoinMessage.DeniedDeclined))
            ApprovedStatus -> inviteMessage(homeId)?.let { sendQuietly(sender, it) }
            else -> Unit
        }
    }

    /** An approved joiner answered Accepted: they join the roster, and the invite is spent on them. */
    private suspend fun completeApproved(sender: String, homeId: String) {
        val request = database.joinRequestQueries.selectApprovedByAgent(homeId, sender).awaitAsOneOrNull() ?: return
        rosters.admit(homeId, request.memberId, request.name, MemberRole.Member.storageValue, request.agentId)
        rosters.useInviteHash(homeId, request.secretHash, request.memberId)
        database.joinRequestQueries.delete(homeId, request.memberId)
        recorder?.membership(homeId, sender, "joined")
    }

    private suspend fun isAdminOf(homeId: String): Boolean {
        val me = profiles.current().id
        return database.homeMemberQueries.selectByHome(homeId).awaitAsList()
            .any { it.memberId == me && it.role == MemberRole.Admin.storageValue }
    }

    /** The [HomeJoinMessage.Invite] for [homeId] from this user, or null if this device has no such Home or user in it. */
    private suspend fun inviteMessage(homeId: String): ByteArray? {
        val home = database.homeQueries.selectById(homeId).awaitAsOneOrNull() ?: return null
        val me = profiles.current().id
        val mine = database.homeMemberQueries.selectByHome(homeId).awaitAsList().firstOrNull { it.memberId == me } ?: return null
        return HomeJoinMessage.encode(
            HomeJoinMessage.Invite(home.id, home.name, home.shape, home.createdAt, mine.memberId, mine.name),
        )
    }

    private fun deniedMessage(homeId: String, reason: String) = HomeJoinMessage.encode(HomeJoinMessage.Denied(homeId, reason))

    /** The reason to tell a joiner an invite won't work, or null when it will. */
    private fun denial(status: InviteStatus): String? = when (status) {
        InviteStatus.Valid -> null
        InviteStatus.Expired -> HomeJoinMessage.DeniedExpired
        InviteStatus.Revoked -> HomeJoinMessage.DeniedRevoked
        InviteStatus.Used -> HomeJoinMessage.DeniedUsed
    }

    /** Remembers [payload] for [agentId] until they acknowledge it, and tries it now. */
    private suspend fun send(agentId: String, homeId: String, kind: String, payload: ByteArray) {
        database.pendingMessageQueries.upsert(agentId, homeId, kind, payload, nowMillis())
        sendQuietly(agentId, payload)
    }

    private suspend fun acknowledge(agentId: String, homeId: String, kind: String) =
        sendQuietly(agentId, HomeJoinMessage.encode(HomeJoinMessage.Ack(homeId, kind)))

    /** A member says they have left: believed only from a device the roster lists for that member. */
    private suspend fun acceptLeft(sender: String, message: HomeJoinMessage.Left) {
        if (sender !in rosters.devices(message.homeId)[message.memberId].orEmpty()) return
        rosters.remove(message.homeId, message.memberId)
        recorder?.membership(message.homeId, sender, "left")
    }

    /** This user has been removed from a Home: believed only from a device the roster lists for one of its admins. */
    private suspend fun acceptRemoved(sender: String, message: HomeJoinMessage.Removed) {
        val homeId = message.homeId
        val admins = database.homeMemberQueries.selectByHome(homeId).awaitAsList()
            .filter { it.role == MemberRole.Admin.storageValue }.map { it.memberId }
        val devices = rosters.devices(homeId)
        if (admins.none { sender in devices[it].orEmpty() }) return
        // As leaving does: the Home goes from this device, and from the user's others as the registry syncs.
        database.homeQueries.deleteById(homeId)
        homes.deleted(homeId)
    }

    private suspend fun sendQuietly(agentId: String, payload: ByteArray) {
        runCatching {
            runCatching { client.connectAgent(agentId) }
            client.sendDirect(agentId, payload)
        }.onFailure { if (it is CancellationException) throw it }
    }

    /**
     * An Invite arrived. It is accepted only from an agent that an invite link we opened named (ADR-018), and only while
     * that request hasn't been refused. Returns who is joining, or null if the Invite is to be ignored.
     */
    private suspend fun joinerFor(sender: String, invite: HomeJoinMessage.Invite): Joiner? {
        val join = database.pendingJoinQueries.selectById(invite.homeId).awaitAsOneOrNull() ?: return null
        if (join.denial != null || sender !in join.agents()) return null
        val me = profiles.current()
        val agentId = client.agent().agentId ?: return null
        return Joiner(me.id, me.displayName, agentId)
    }

    private class Joiner(val memberId: String, val name: String, val agentId: String)

    private suspend fun acceptInvite(sender: String, invite: HomeJoinMessage.Invite) = lock.withLock {
        // A Home this device already has: if we are in it and this is who invited us, our Accepted may have been lost
        // and the admin is still resending, so say it again. Otherwise there is nothing to join.
        if (database.homeQueries.selectById(invite.homeId).awaitAsOneOrNull() != null) {
            val devices = rosters.devices(invite.homeId)
            if (sender in devices[invite.inviterMemberId].orEmpty() && devices[profiles.current().id].orEmpty().isNotEmpty()) {
                database.pendingJoinQueries.delete(invite.homeId)
                sendQuietly(sender, HomeJoinMessage.encode(HomeJoinMessage.Accepted(invite.homeId)))
            }
            return@withLock
        }
        val joiner = joinerFor(sender, invite) ?: return@withLock
        // The same person on another device: nothing to join.
        if (invite.inviterMemberId == joiner.memberId) return@withLock

        database.homeQueries.insert(invite.homeId, invite.homeName, invite.shape, invite.createdAt)
        // The admin seeded the Pantry; ours starts empty and fills from theirs (ADR-013).
        storeEmptyPantry(database, invite.homeId)
        storeEmptyShoppingList(database, invite.homeId)
        homes.saved(StoredHome(invite.homeId, invite.homeName, invite.shape, invite.createdAt))
        // A device only syncs a Home with the agents its roster lists, so it has to be told who invited it.
        rosters.admit(invite.homeId, invite.inviterMemberId, invite.inviterName, MemberRole.Admin.storageValue, sender)
        rosters.admit(invite.homeId, joiner.memberId, joiner.name, MemberRole.Member.storageValue, joiner.agentId)
        database.pendingJoinQueries.delete(invite.homeId)
        sendQuietly(sender, HomeJoinMessage.encode(HomeJoinMessage.Accepted(invite.homeId)))
        recorder?.membership(invite.homeId, sender, "joined")
    }
}
