package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import uk.veritaslabs.vesta.home.HomeConfig
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.homegroup.HomeHub
import uk.veritaslabs.vesta.homegroup.HubEnrolment
import uk.veritaslabs.vesta.homegroup.HubResult
import uk.veritaslabs.vesta.homegroup.HubState
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.home.MemberRole
import uk.veritaslabs.vesta.x0x.X0xApiException
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xGroupMember

private const val AdminRole = "admin"
private const val ActiveState = "active"
private const val KeyPackagePending = "member_key_package_pending"

/**
 * [HomeHub] over the Home's x0x group ([HomeGroups]) and its roster (docs/adr/ADR-020, slice 6, and its several-hubs amendment).
 * The hubs are recorded in the roster, so every member's device learns them, and [HomeGroups] keeps them x0x admins. A hub is
 * usually an agent in the group that acts for nobody; it may also be a device of an admin member.
 */
internal class HomeHubs(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val rosters: HomeRosters,
    private val profiles: ProfileRepository,
    private val removeRetryDelay: Duration = 3.seconds,
    private val removeAttempts: Int = 5,
    private val config: HomeConfig = HomeConfig(),
) : HomeHub {

    override suspend fun enrolment(homeId: String): HubEnrolment? {
        val group = readyGroup(homeId) ?: return null
        if (!isAdminOfHome(homeId) || !isAdminOfGroup(group)) return null
        val invite = client.createGroupInvite(group, config.hubEnrolmentLifetime.inWholeSeconds)
        return HubEnrolment(group, "x0x group join $invite\nx0x group store create $group ${config.mailboxStoreName}")
    }

    override suspend fun hubs(homeId: String): List<HubState> {
        val hubs = rosters.hubs(homeId).sorted()
        if (hubs.isEmpty()) return emptyList()
        val active = localGroup(homeId)?.let { group ->
            runCatching { client.getGroupMembers(group) }.getOrNull()?.filter { it.state == ActiveState }?.map { it.agentId }?.toSet()
        }.orEmpty()
        val devices = deviceAgents(homeId)
        return hubs.map { HubState(it, inGroup = it in active, isDevice = it in devices) }
    }

    override suspend fun candidates(homeId: String): List<String> {
        val group = localGroup(homeId) ?: return emptyList()
        val members = runCatching { client.getGroupMembers(group) }.getOrNull() ?: return emptyList()
        val known = deviceAgents(homeId) + rosters.hubs(homeId) + setOfNotNull(selfId())
        return members.filter { it.state == ActiveState && it.agentId !in known }.map { it.agentId }
    }

    override suspend fun ownDeviceCandidates(homeId: String): List<String> {
        val group = localGroup(homeId) ?: return emptyList()
        val members = runCatching { client.getGroupMembers(group) }.getOrNull() ?: return emptyList()
        val me = profiles.current().id
        val own = rosters.members(homeId).firstOrNull { it.memberId == me && !it.removed }?.devices.orEmpty()
        val hubs = rosters.hubs(homeId)
        return members.filter { it.state == ActiveState && it.agentId in own && it.agentId !in hubs }.map { it.agentId }.sorted()
    }

    override suspend fun useAsHub(homeId: String, agentId: String): HubResult {
        val group = localGroup(homeId) ?: return HubResult.Failed
        if (!isAdminOfHome(homeId)) return HubResult.NotAnAdmin
        // Being a hub makes the agent an x0x admin, so a device of a member who is not an admin of the Home can't be one.
        val owner = rosters.members(homeId).firstOrNull { agentId in it.devices }
        if (owner != null && (owner.removed || owner.role != MemberRole.Admin.storageValue)) return HubResult.NotAnAdminDevice
        val active = try {
            client.getGroupMembers(group).any { it.agentId == agentId && it.state == ActiveState }
        } catch (e: X0xApiException) {
            return HubResult.Failed
        }
        if (!active) return HubResult.NotInGroup
        rosters.addHub(homeId, agentId)
        // Any x0x admin keeps the hubs admins each round; doing it now just saves the wait.
        attempt { client.setGroupMemberRole(group, agentId, AdminRole) }
        return HubResult.Done
    }

    override suspend fun removeHub(homeId: String, agentId: String): HubResult {
        if (agentId !in rosters.hubs(homeId)) return HubResult.Done
        if (!isAdminOfHome(homeId)) return HubResult.NotAnAdmin
        val group = localGroup(homeId)
        // A device of a member stays in the group, which is still its member's; only a machine that is nobody's is taken out.
        if (group != null && agentId !in deviceAgents(homeId)) {
            val members = try {
                client.getGroupMembers(group)
            } catch (e: X0xApiException) {
                return HubResult.Failed
            }
            if (members.any { it.agentId == agentId && it.state == ActiveState }) {
                if (!removeFromGroup(group, agentId)) return HubResult.Failed
            }
        }
        rosters.removeHub(homeId, agentId)
        return HubResult.Done
    }

    /** The agents that act for a member of the Home who is still in it. */
    private suspend fun deviceAgents(homeId: String): Set<String> =
        rosters.members(homeId).filterNot { it.removed }.flatMap { it.devices }.toSet()

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

    private suspend fun localGroup(homeId: String): String? = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.groupId

    /** The Home's group, once this device is in it and has the mailbox open. */
    private suspend fun readyGroup(homeId: String): String? =
        database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.takeIf { it.storeId != null }?.groupId

    private suspend fun isAdminOfHome(homeId: String): Boolean {
        val me = profiles.current().id
        return rosters.members(homeId).any { it.memberId == me && !it.removed && it.role == MemberRole.Admin.storageValue }
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
