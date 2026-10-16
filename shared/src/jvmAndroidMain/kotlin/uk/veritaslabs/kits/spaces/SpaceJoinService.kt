package uk.veritaslabs.kits.spaces

import uk.veritaslabs.kits.docs.SyncEventRecorder

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
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.x0x.X0xClient
import uk.veritaslabs.kits.x0x.X0xDirectMessage

private const val PendingStatus = "pending"
private const val ApprovedStatus = "approved"
private const val DeclinedStatus = "declined"

/**
 * Joining a Space with an invite link (docs/adr/ADR-018-invite-links.md, ADR-019), entirely over x0x direct messages: no x0x
 * group is involved, because a Space's sync is already limited to the devices its roster lists (ADR-014) and every direct
 * message is end-to-end encrypted to its recipient.
 *
 * - **Admin**: makes invite links ([createInviteLink]), is asked to let people in ([SpaceJoinMessage.Request]), and
 *   [approve]s or [decline]s each, sending an Invite until the person answers Accepted and joins the roster.
 * - **Joiner** ([redeemInvite]): asks the admins a link names, every sync round, until one of them sends the Invite, which
 *   creates the Space here, puts the inviter and this user in its roster, and is answered with Accepted.
 */
internal class SpaceJoinService(
    private val client: X0xClient,
    private val database: KitsDatabase,
    private val rosters: SpaceRosters,
    private val spaces: SpaceChangeListener,
    private val profiles: ProfileRepository,
    private val seeding: SpaceSeeding,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val messageLifetime: Duration = 14.days,
    private val recorder: SyncEventRecorder? = null,
    private val config: SpacesConfig = SpacesConfig(),
) : SpaceJoining {

    // Serialises the work an Invite does, so two copies of one can't both create the Space.
    private val lock = Mutex()

    /** Handles a direct message if it is a Space-join message; returns whether it was one, so the caller can stop there. */
    suspend fun handle(message: X0xDirectMessage): Boolean {
        val decoded = SpaceJoinMessage.decode(message.payload) ?: return false
        if (message.verified != true) return true
        val sender = message.sender ?: return true
        when (decoded) {
            is SpaceJoinMessage.Invite -> acceptInvite(sender, decoded)
            is SpaceJoinMessage.Accepted -> completeApproved(sender, decoded.spaceId)
            is SpaceJoinMessage.Left -> {
                acceptLeft(sender, decoded)
                acknowledge(sender, decoded.spaceId, SpaceJoinMessage.LeftKind)
            }
            is SpaceJoinMessage.Removed -> {
                acceptRemoved(sender, decoded)
                acknowledge(sender, decoded.spaceId, SpaceJoinMessage.RemovedKind)
            }
            is SpaceJoinMessage.Ack -> {
                database.pendingMessageQueries.delete(sender, decoded.spaceId, decoded.of)
                recorder?.membership(decoded.spaceId, sender, "acknowledged")
            }
            is SpaceJoinMessage.Request -> receiveRequest(sender, decoded)
            is SpaceJoinMessage.Denied -> receiveDenied(sender, decoded)
        }
        return true
    }

    /**
     * An admin removes [memberId] from [spaceId]: they are tombstoned in the roster, which stops the Space syncing with
     * their devices, and those devices are told so they can drop the Space (best effort: one that is offline isn't).
     */
    suspend fun removeMember(spaceId: String, memberId: String) {
        val devices = rosters.devices(spaceId)[memberId].orEmpty()
        rosters.remove(spaceId, memberId)
        val message = SpaceJoinMessage.encode(SpaceJoinMessage.Removed(spaceId))
        devices.forEach {
            send(it, spaceId, SpaceJoinMessage.RemovedKind, message)
            recorder?.membership(spaceId, it, "removed")
        }
    }

    /** The current user, [memberId], leaves [spaceId]: the devices of the other members are told (best effort). */
    suspend fun announceLeft(spaceId: String, memberId: String) {
        val others = rosters.devices(spaceId).filterKeys { it != memberId }.values.flatten().toSet()
        val message = SpaceJoinMessage.encode(SpaceJoinMessage.Left(spaceId, memberId))
        others.forEach { send(it, spaceId, SpaceJoinMessage.LeftKind, message) }
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
        if (database.homeQueries.selectById(link.spaceId).awaitAsOneOrNull() != null) return RedeemResult.AlreadyInSpace
        database.pendingJoinQueries.upsert(link.spaceId, link.spaceName, link.secret, link.adminAgentIds.joinToString(","), link.expiresAt, now)
        database.pendingJoinQueries.selectById(link.spaceId).awaitAsOneOrNull()?.let { sendJoinRequest(it.homeId, it.secret, it.agents()) }
        return RedeemResult.Started
    }

    override suspend fun pendingJoins(): List<PendingJoin> =
        database.pendingJoinQueries.selectAll().awaitAsList().map { PendingJoin(it.homeId, it.homeName, it.expiresAt, it.denial) }

    override fun observeJoinRequests(spaceId: String): Flow<List<JoinRequest>> =
        database.joinRequestQueries.selectUndecidedOrApprovedByHome(spaceId).asFlow().map { query ->
            query.awaitAsList().map { JoinRequest(it.homeId, it.memberId, it.name, it.createdAt, approved = it.status == ApprovedStatus) }
        }

    override fun observePendingJoins(): Flow<List<PendingJoin>> =
        database.pendingJoinQueries.selectAll().asFlow().map { query ->
            query.awaitAsList().map { PendingJoin(it.homeId, it.homeName, it.expiresAt, it.denial) }
        }

    override suspend fun createInviteLink(spaceId: String): InviteLink? {
        val ownAgent = runCatching { client.agent().agentId }.getOrNull()
        return rosters.createInvite(spaceId, profiles.current().id, config.inviteLinkLifetime.inWholeMilliseconds, ownAgentId = ownAgent)
    }

    override suspend fun inviteLinks(spaceId: String): List<ActiveInvite> {
        if (!isAdminOf(spaceId)) return emptyList()
        val now = nowMillis()
        return rosters.invites(spaceId)
            .filter { !it.revoked && now < it.expiresAt && it.usedBy.size < it.maxUses }
            .map { ActiveInvite(it.secretHash, it.expiresAt, it.usedBy.size, it.maxUses) }
    }

    override suspend fun revokeInviteLink(spaceId: String, id: String) {
        if (isAdminOf(spaceId)) rosters.revokeInvite(spaceId, id)
    }

    override suspend fun cancelPendingJoin(spaceId: String) {
        database.pendingJoinQueries.delete(spaceId)
    }

    /**
     * Asks the admins of every invite link this user is waiting on again, unless it has expired or was refused. A pending
     * join is kept after its link expires for as long as an admin might still resend an approved Invite, then forgotten,
     * as is one whose Space has arrived some other way (from another of the user's devices).
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

    private suspend fun sendJoinRequest(spaceId: String, secret: String, adminAgents: List<String>) {
        val me = profiles.current()
        val agentId = runCatching { client.agent().agentId }.getOrNull() ?: return
        val message = SpaceJoinMessage.encode(SpaceJoinMessage.Request(spaceId, secret, me.id, me.displayName, agentId))
        adminAgents.forEach { sendQuietly(it, message) }
    }

    /** An admin refused a request of ours: remembered, believed only from an agent the invite link named. */
    private suspend fun receiveDenied(sender: String, denied: SpaceJoinMessage.Denied) {
        val join = database.pendingJoinQueries.selectById(denied.spaceId).awaitAsOneOrNull() ?: return
        if (sender in join.agents()) database.pendingJoinQueries.setDenial(denied.reason, denied.spaceId)
    }

    private fun uk.veritaslabs.vesta.db.PendingJoin.agents() = adminAgents.split(",").filter { it.isNotBlank() }

    override suspend fun joinRequests(spaceId: String): List<JoinRequest> =
        database.joinRequestQueries.selectUndecidedOrApprovedByHome(spaceId).awaitAsList().map {
            JoinRequest(it.homeId, it.memberId, it.name, it.createdAt, approved = it.status == ApprovedStatus)
        }

    override suspend fun approve(spaceId: String, memberId: String): RequestResult {
        if (!isAdminOf(spaceId)) return RequestResult.NotAnAdmin
        val request = database.joinRequestQueries.selectById(spaceId, memberId).awaitAsOneOrNull()
            ?.takeIf { it.status != DeclinedStatus } ?: return RequestResult.NoSuchRequest
        val status = rosters.inviteStatus(spaceId, request.secretHash, memberId)
        if (status != InviteStatus.Valid) {
            // Revoked, spent or expired while it waited, or gone from the roster.
            database.joinRequestQueries.delete(spaceId, memberId)
            status?.let { denial(it) }?.let { sendQuietly(request.agentId, deniedMessage(spaceId, it)) }
            return RequestResult.InviteNoLongerValid
        }
        database.joinRequestQueries.setStatus(ApprovedStatus, spaceId, memberId)
        inviteMessage(spaceId)?.let { sendQuietly(request.agentId, it) }
        return RequestResult.Done
    }

    override suspend fun decline(spaceId: String, memberId: String): RequestResult {
        if (!isAdminOf(spaceId)) return RequestResult.NotAnAdmin
        val request = database.joinRequestQueries.selectById(spaceId, memberId).awaitAsOneOrNull()
            ?.takeIf { it.status == PendingStatus } ?: return RequestResult.NoSuchRequest
        database.joinRequestQueries.setStatus(DeclinedStatus, spaceId, memberId)
        sendQuietly(request.agentId, deniedMessage(spaceId, SpaceJoinMessage.DeniedDeclined))
        return RequestResult.Done
    }

    /**
     * Someone says they opened an invite link of [request]'s Space. Believed only from the agent it names, and only
     * if this user is an admin of the Space. A member who is already in sends it again when our Invite was lost, so they
     * get it again. Otherwise the secret has to be valid: a wrong or unknown one is ignored, so a stranger learns nothing,
     * a known but unusable one is told why, and a good one waits for an admin to approve it.
     */
    private suspend fun receiveRequest(sender: String, request: SpaceJoinMessage.Request) {
        val spaceId = request.spaceId
        if (sender != request.agentId || !isAdminOf(spaceId)) return
        if (database.homeMemberQueries.selectByHome(spaceId).awaitAsList().any { it.memberId == request.memberId }) {
            if (sender in rosters.devices(spaceId)[request.memberId].orEmpty()) inviteMessage(spaceId)?.let { sendQuietly(sender, it) }
            return
        }
        val hash = inviteSecretHash(request.secret)
        val status = rosters.inviteStatus(spaceId, hash, request.memberId) ?: return
        denial(status)?.let { return sendQuietly(sender, deniedMessage(spaceId, it)) }
        when (database.joinRequestQueries.selectById(spaceId, request.memberId).awaitAsOneOrNull()?.status) {
            null -> database.joinRequestQueries.insertPending(
                spaceId, request.memberId, request.name, sender, hash, nowMillis(),
            )
            DeclinedStatus -> sendQuietly(sender, deniedMessage(spaceId, SpaceJoinMessage.DeniedDeclined))
            ApprovedStatus -> inviteMessage(spaceId)?.let { sendQuietly(sender, it) }
            else -> Unit
        }
    }

    /** An approved joiner answered Accepted: they join the roster, and the invite is spent on them. */
    private suspend fun completeApproved(sender: String, spaceId: String) {
        val request = database.joinRequestQueries.selectApprovedByAgent(spaceId, sender).awaitAsOneOrNull() ?: return
        rosters.admit(spaceId, request.memberId, request.name, MemberRole.Member.storageValue, request.agentId)
        rosters.useInviteHash(spaceId, request.secretHash, request.memberId)
        database.joinRequestQueries.delete(spaceId, request.memberId)
        recorder?.membership(spaceId, sender, "joined")
    }

    private suspend fun isAdminOf(spaceId: String): Boolean {
        val me = profiles.current().id
        return database.homeMemberQueries.selectByHome(spaceId).awaitAsList()
            .any { it.memberId == me && it.role == MemberRole.Admin.storageValue }
    }

    /** The [SpaceJoinMessage.Invite] for [spaceId] from this user, or null if this device has no such Space or user in it. */
    private suspend fun inviteMessage(spaceId: String): ByteArray? {
        val space = database.homeQueries.selectById(spaceId).awaitAsOneOrNull() ?: return null
        val me = profiles.current().id
        val mine = database.homeMemberQueries.selectByHome(spaceId).awaitAsList().firstOrNull { it.memberId == me } ?: return null
        return SpaceJoinMessage.encode(
            SpaceJoinMessage.Invite(space.id, space.name, space.shape, space.createdAt, mine.memberId, mine.name),
        )
    }

    private fun deniedMessage(spaceId: String, reason: String) = SpaceJoinMessage.encode(SpaceJoinMessage.Denied(spaceId, reason))

    /** The reason to tell a joiner an invite won't work, or null when it will. */
    private fun denial(status: InviteStatus): String? = when (status) {
        InviteStatus.Valid -> null
        InviteStatus.Expired -> SpaceJoinMessage.DeniedExpired
        InviteStatus.Revoked -> SpaceJoinMessage.DeniedRevoked
        InviteStatus.Used -> SpaceJoinMessage.DeniedUsed
    }

    /** Remembers [payload] for [agentId] until they acknowledge it, and tries it now. */
    private suspend fun send(agentId: String, spaceId: String, kind: String, payload: ByteArray) {
        database.pendingMessageQueries.upsert(agentId, spaceId, kind, payload, nowMillis())
        sendQuietly(agentId, payload)
    }

    private suspend fun acknowledge(agentId: String, spaceId: String, kind: String) =
        sendQuietly(agentId, SpaceJoinMessage.encode(SpaceJoinMessage.Ack(spaceId, kind)))

    /** A member says they have left: believed only from a device the roster lists for that member. */
    private suspend fun acceptLeft(sender: String, message: SpaceJoinMessage.Left) {
        if (sender !in rosters.devices(message.spaceId)[message.memberId].orEmpty()) return
        rosters.remove(message.spaceId, message.memberId)
        recorder?.membership(message.spaceId, sender, "left")
    }

    /** This user has been removed from a Space: believed only from a device the roster lists for one of its admins. */
    private suspend fun acceptRemoved(sender: String, message: SpaceJoinMessage.Removed) {
        val spaceId = message.spaceId
        val admins = database.homeMemberQueries.selectByHome(spaceId).awaitAsList()
            .filter { it.role == MemberRole.Admin.storageValue }.map { it.memberId }
        val devices = rosters.devices(spaceId)
        if (admins.none { sender in devices[it].orEmpty() }) return
        // As leaving does: the Space goes from this device, and from the user's others as the registry syncs.
        database.homeQueries.deleteById(spaceId)
        spaces.deleted(spaceId)
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
    private suspend fun joinerFor(sender: String, invite: SpaceJoinMessage.Invite): Joiner? {
        val join = database.pendingJoinQueries.selectById(invite.spaceId).awaitAsOneOrNull() ?: return null
        if (join.denial != null || sender !in join.agents()) return null
        val me = profiles.current()
        val agentId = client.agent().agentId ?: return null
        return Joiner(me.id, me.displayName, agentId)
    }

    private class Joiner(val memberId: String, val name: String, val agentId: String)

    private suspend fun acceptInvite(sender: String, invite: SpaceJoinMessage.Invite) = lock.withLock {
        // A Space this device already has: if we are in it and this is who invited us, our Accepted may have been lost
        // and the admin is still resending, so say it again. Otherwise there is nothing to join.
        if (database.homeQueries.selectById(invite.spaceId).awaitAsOneOrNull() != null) {
            val devices = rosters.devices(invite.spaceId)
            if (sender in devices[invite.inviterMemberId].orEmpty() && devices[profiles.current().id].orEmpty().isNotEmpty()) {
                database.pendingJoinQueries.delete(invite.spaceId)
                sendQuietly(sender, SpaceJoinMessage.encode(SpaceJoinMessage.Accepted(invite.spaceId)))
            }
            return@withLock
        }
        val joiner = joinerFor(sender, invite) ?: return@withLock
        // The same person on another device: nothing to join.
        if (invite.inviterMemberId == joiner.memberId) return@withLock

        database.homeQueries.insert(invite.spaceId, invite.spaceName, invite.kind, invite.createdAt)
        // The admin seeded the Pantry; ours starts empty and fills from theirs (ADR-013).
        seeding.placeholdersForArrived(invite.spaceId)
        spaces.saved(StoredSpace(invite.spaceId, invite.spaceName, invite.kind, invite.createdAt))
        // A device only syncs a Space with the agents its roster lists, so it has to be told who invited it.
        rosters.admit(invite.spaceId, invite.inviterMemberId, invite.inviterName, MemberRole.Admin.storageValue, sender)
        rosters.admit(invite.spaceId, joiner.memberId, joiner.name, MemberRole.Member.storageValue, joiner.agentId)
        database.pendingJoinQueries.delete(invite.spaceId)
        sendQuietly(sender, SpaceJoinMessage.encode(SpaceJoinMessage.Accepted(invite.spaceId)))
        recorder?.membership(invite.spaceId, sender, "joined")
    }
}
