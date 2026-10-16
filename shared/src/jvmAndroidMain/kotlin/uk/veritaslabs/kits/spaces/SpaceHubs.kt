package uk.veritaslabs.kits.spaces

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.x0x.X0xApiException
import uk.veritaslabs.kits.x0x.X0xClient
import uk.veritaslabs.kits.x0x.X0xGroupMember

private const val AdminRole = "admin"
private const val ActiveState = "active"
private const val KeyPackagePending = "member_key_package_pending"

/**
 * [SpaceHub] over the Space's x0x group ([SpaceGroups]) and its roster (docs/adr/ADR-020, slice 6, and its several-hubs amendment).
 * The hubs are recorded in the roster, so every member's device learns them, and [SpaceGroups] keeps them x0x admins. A hub is
 * usually an agent in the group that acts for nobody; it may also be a device of an admin member.
 */
internal class SpaceHubs(
    private val client: X0xClient,
    private val database: KitsDatabase,
    private val rosters: SpaceRosters,
    private val profiles: ProfileRepository,
    private val removeRetryDelay: Duration = 3.seconds,
    private val removeAttempts: Int = 5,
    private val config: SpacesConfig = SpacesConfig(),
) : SpaceHub {

    override suspend fun enrolment(spaceId: String): HubEnrolment? {
        val group = readyGroup(spaceId) ?: return null
        if (!isAdminOfSpace(spaceId) || !isAdminOfGroup(group)) return null
        val invite = client.createGroupInvite(group, config.hubEnrolmentLifetime.inWholeSeconds)
        return HubEnrolment(group, "x0x group join $invite\nx0x group store create $group ${config.mailboxStoreName}")
    }

    override suspend fun hubs(spaceId: String): List<HubState> {
        val hubs = rosters.hubs(spaceId).sorted()
        if (hubs.isEmpty()) return emptyList()
        val active = localGroup(spaceId)?.let { group ->
            runCatching { client.getGroupMembers(group) }.getOrNull()?.filter { it.state == ActiveState }?.map { it.agentId }?.toSet()
        }.orEmpty()
        val devices = deviceAgents(spaceId)
        return hubs.map { HubState(it, inGroup = it in active, isDevice = it in devices) }
    }

    override suspend fun candidates(spaceId: String): List<String> {
        val group = localGroup(spaceId) ?: return emptyList()
        val members = runCatching { client.getGroupMembers(group) }.getOrNull() ?: return emptyList()
        val known = deviceAgents(spaceId) + rosters.hubs(spaceId) + setOfNotNull(selfId())
        return members.filter { it.state == ActiveState && it.agentId !in known }.map { it.agentId }
    }

    override suspend fun ownDeviceCandidates(spaceId: String): List<String> {
        val group = localGroup(spaceId) ?: return emptyList()
        val members = runCatching { client.getGroupMembers(group) }.getOrNull() ?: return emptyList()
        val me = profiles.current().id
        val own = rosters.members(spaceId).firstOrNull { it.memberId == me && !it.removed }?.devices.orEmpty()
        val hubs = rosters.hubs(spaceId)
        return members.filter { it.state == ActiveState && it.agentId in own && it.agentId !in hubs }.map { it.agentId }.sorted()
    }

    override suspend fun useAsHub(spaceId: String, agentId: String): HubResult {
        val group = localGroup(spaceId) ?: return HubResult.Failed
        if (!isAdminOfSpace(spaceId)) return HubResult.NotAnAdmin
        // Being a hub makes the agent an x0x admin, so a device of a member who is not an admin of the Space can't be one.
        val owner = rosters.members(spaceId).firstOrNull { agentId in it.devices }
        if (owner != null && (owner.removed || owner.role != MemberRole.Admin.storageValue)) return HubResult.NotAnAdminDevice
        val active = try {
            client.getGroupMembers(group).any { it.agentId == agentId && it.state == ActiveState }
        } catch (e: X0xApiException) {
            return HubResult.Failed
        }
        if (!active) return HubResult.NotInGroup
        rosters.addHub(spaceId, agentId)
        // Any x0x admin keeps the hubs admins each round; doing it now just saves the wait.
        attempt { client.setGroupMemberRole(group, agentId, AdminRole) }
        return HubResult.Done
    }

    override suspend fun removeHub(spaceId: String, agentId: String): HubResult {
        if (agentId !in rosters.hubs(spaceId)) return HubResult.Done
        if (!isAdminOfSpace(spaceId)) return HubResult.NotAnAdmin
        val group = localGroup(spaceId)
        // A device of a member stays in the group, which is still its member's; only a machine that is nobody's is taken out.
        if (group != null && agentId !in deviceAgents(spaceId)) {
            val members = try {
                client.getGroupMembers(group)
            } catch (e: X0xApiException) {
                return HubResult.Failed
            }
            if (members.any { it.agentId == agentId && it.state == ActiveState }) {
                if (!removeFromGroup(group, agentId)) return HubResult.Failed
            }
        }
        rosters.removeHub(spaceId, agentId)
        return HubResult.Done
    }

    /** The agents that act for a member of the Space who is still in it. */
    private suspend fun deviceAgents(spaceId: String): Set<String> =
        rosters.members(spaceId).filterNot { it.removed }.flatMap { it.devices }.toSet()

    /** Removes [agentId], retrying while x0x is still fetching its key package; false when it could not be removed. */
    private suspend fun removeFromGroup(group: String, agentId: String): Boolean {
        var attemptsLeft = removeAttempts
        while (true) {
            try {
                client.removeGroupMember(group, agentId)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: X0xApiException) {
                if (e.message != KeyPackagePending || --attemptsLeft <= 0) return false
                delay(removeRetryDelay)
            }
        }
    }

    private suspend fun localGroup(spaceId: String): String? = database.homeGroupQueries.selectByHome(spaceId).awaitAsOneOrNull()?.groupId

    /** The Space's group, once this device is in it and has the mailbox open. */
    private suspend fun readyGroup(spaceId: String): String? =
        database.homeGroupQueries.selectByHome(spaceId).awaitAsOneOrNull()?.takeIf { it.storeId != null }?.groupId

    private suspend fun isAdminOfSpace(spaceId: String): Boolean {
        val me = profiles.current().id
        return rosters.members(spaceId).any { it.memberId == me && !it.removed && it.role == MemberRole.Admin.storageValue }
    }

    private suspend fun isAdminOfGroup(group: String): Boolean {
        val self = selfId() ?: return false
        val members: List<X0xGroupMember> = runCatching { client.getGroupMembers(group) }.getOrNull() ?: return false
        return members.firstOrNull { it.agentId == self }?.role == AdminRole
    }

    private suspend fun selfId(): String? = runCatching { client.agent().agentId }.getOrNull()

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
