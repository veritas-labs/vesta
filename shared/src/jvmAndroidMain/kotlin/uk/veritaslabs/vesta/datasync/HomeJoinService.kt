package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import uk.veritaslabs.vesta.automerge.storeEmptyPantry
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.home.StoredHome
import uk.veritaslabs.vesta.automerge.InviteStatus
import uk.veritaslabs.vesta.automerge.inviteSecretHash
import uk.veritaslabs.vesta.homejoin.HomeJoinMessage
import uk.veritaslabs.vesta.homejoin.HomeJoining
import uk.veritaslabs.vesta.homejoin.InviteException
import uk.veritaslabs.vesta.homejoin.InviteFailure
import uk.veritaslabs.vesta.homejoin.JoinLink
import uk.veritaslabs.vesta.homejoin.JoinRequest
import uk.veritaslabs.vesta.homejoin.JoinedHome
import uk.veritaslabs.vesta.homejoin.RequestResult
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.MemberRole
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

private val LinkLifetime = 5 * 60 * 1000L
private const val PendingStatus = "pending"
private const val ApprovedStatus = "approved"
private const val DeclinedStatus = "declined"

/**
 * Joining a Home with a link (docs/adr/ADR-015-joining-a-home.md), entirely over x0x direct messages: no x0x group is
 * involved, because a Home's sync is already limited to the devices its roster lists (ADR-014) and every direct message
 * is end-to-end encrypted to its recipient.
 *
 * - **Joiner** ([openLink]): shows a [JoinLink]. While it is showing and unexpired, the first [HomeJoinMessage.Invite]
 *   from a verified sender creates the Home here, puts the inviter and this user in its roster, and answers
 *   [HomeJoinMessage.Accepted].
 * - **Admin** ([invite]): sends the invitation to the link's agent, every [resendInterval] until it answers (a message
 *   to a peer that isn't listening yet can vanish), then admits that person to the Home's roster.
 */
internal class HomeJoinService(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val rosters: HomeRosters,
    private val homes: HomeChangeListener,
    private val profiles: ProfileRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val resendInterval: Duration = 10.seconds,
    private val timeout: Duration = 90.seconds,
    private val messageLifetime: Duration = 14.days,
) : HomeJoining {

    private val _joined = MutableSharedFlow<JoinedHome>(extraBufferCapacity = 4)
    override val joined: SharedFlow<JoinedHome> = _joined.asSharedFlow()

    // The link this device is showing; guarded by [lock] together with the work an invitation does.
    private val lock = Mutex()
    private var offered: JoinLink? = null
    private val pending = ConcurrentHashMap<Pair<String, String>, CompletableDeferred<Unit>>()

    override suspend fun openLink(): JoinLink {
        val me = profiles.current()
        val link = JoinLink(
            agentId = checkNotNull(client.agent().agentId) { "x0x did not report this device's agent id" },
            memberId = me.id,
            expiresAt = nowMillis() + LinkLifetime,
            name = me.displayName,
        )
        lock.withLock { offered = link }
        return link
    }

    override fun cancelLink() {
        offered = null
    }

    override suspend fun invite(homeId: String, link: JoinLink): Result<Unit> = try {
        Result.success(sendInvite(homeId, link))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun sendInvite(homeId: String, link: JoinLink) {
        if (link.isExpired(nowMillis())) throw InviteException(InviteFailure.LinkExpired)
        val home = database.homeQueries.selectById(homeId).awaitAsOneOrNull() ?: throw InviteException(InviteFailure.NoSuchHome)
        val me = profiles.current().id
        val members = database.homeMemberQueries.selectByHome(homeId).awaitAsList()
        val mine = members.firstOrNull { it.memberId == me }
        if (mine == null || mine.role != MemberRole.Admin.storageValue) throw InviteException(InviteFailure.NotAnAdmin)
        if (members.any { it.memberId == link.memberId }) throw InviteException(InviteFailure.AlreadyAMember)

        val message = HomeJoinMessage.encode(
            HomeJoinMessage.Invite(home.id, home.name, home.shape, home.createdAt, mine.memberId, mine.name),
        )
        val accepted = CompletableDeferred<Unit>()
        val key = homeId to link.agentId
        pending[key] = accepted
        try {
            try {
                withTimeout(timeout) {
                    while (!accepted.isCompleted) {
                        runCatching {
                            runCatching { client.connectAgent(link.agentId) }
                            client.sendDirect(link.agentId, message)
                        }.onFailure { if (it is CancellationException) throw it }
                        withTimeoutOrNull(resendInterval) { accepted.await() }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (!accepted.isCompleted) throw InviteException(InviteFailure.NoResponse)
            }
        } finally {
            pending.remove(key)
        }
        rosters.admit(homeId, link.memberId, link.name, MemberRole.Member.storageValue, link.agentId)
    }

    /** Handles a direct message if it is a Home-join message; returns whether it was one, so the caller can stop there. */
    suspend fun handle(message: X0xDirectMessage): Boolean {
        val decoded = HomeJoinMessage.decode(message.payload) ?: return false
        if (message.verified != true) return true
        val sender = message.sender ?: return true
        when (decoded) {
            is HomeJoinMessage.Invite -> acceptInvite(sender, decoded)
            is HomeJoinMessage.Accepted ->
                if (pending[decoded.homeId to sender]?.complete(Unit) == null) completeApproved(sender, decoded.homeId)
            is HomeJoinMessage.Left -> {
                acceptLeft(sender, decoded)
                acknowledge(sender, decoded.homeId, HomeJoinMessage.LeftKind)
            }
            is HomeJoinMessage.Removed -> {
                acceptRemoved(sender, decoded)
                acknowledge(sender, decoded.homeId, HomeJoinMessage.RemovedKind)
            }
            is HomeJoinMessage.Ack -> database.pendingMessageQueries.delete(sender, decoded.homeId, decoded.of)
            is HomeJoinMessage.Request -> receiveRequest(sender, decoded)
            // A joiner's side of invite links (ADR-018) is not handled yet: slice 4.
            is HomeJoinMessage.Denied -> Unit
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
        devices.forEach { send(it, homeId, HomeJoinMessage.RemovedKind, message) }
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
    }

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

    private suspend fun acceptInvite(sender: String, invite: HomeJoinMessage.Invite) = lock.withLock {
        // A Home this device already has: if we are in it and this is who invited us, our Accepted may have been lost
        // and the admin is still resending, so say it again. Otherwise there is nothing to join.
        if (database.homeQueries.selectById(invite.homeId).awaitAsOneOrNull() != null) {
            val devices = rosters.devices(invite.homeId)
            if (sender in devices[invite.inviterMemberId].orEmpty() && devices[profiles.current().id].orEmpty().isNotEmpty()) {
                sendQuietly(sender, HomeJoinMessage.encode(HomeJoinMessage.Accepted(invite.homeId)))
            }
            return@withLock
        }
        val link = offered ?: return@withLock
        if (link.isExpired(nowMillis())) {
            offered = null
            return@withLock
        }
        // The same person on another device: nothing to join.
        if (invite.inviterMemberId == link.memberId) return@withLock

        database.homeQueries.insert(invite.homeId, invite.homeName, invite.shape, invite.createdAt)
        // The admin seeded the Pantry; ours starts empty and fills from theirs (ADR-013).
        storeEmptyPantry(database, invite.homeId)
        homes.saved(StoredHome(invite.homeId, invite.homeName, invite.shape, invite.createdAt))
        // A device only syncs a Home with the agents its roster lists, so it has to be told who invited it.
        rosters.admit(invite.homeId, invite.inviterMemberId, invite.inviterName, MemberRole.Admin.storageValue, sender)
        rosters.admit(invite.homeId, link.memberId, link.name, MemberRole.Member.storageValue, link.agentId)
        offered = null
        runCatching {
            runCatching { client.connectAgent(sender) }
            client.sendDirect(sender, HomeJoinMessage.encode(HomeJoinMessage.Accepted(invite.homeId)))
        }.onFailure { if (it is CancellationException) throw it }
        _joined.tryEmit(JoinedHome(invite.homeId, invite.homeName))
    }
}
