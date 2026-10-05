package uk.veritaslabs.vesta.devicesync

import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonObject
import uk.veritaslabs.vesta.x0x.FakeX0xClient
import uk.veritaslabs.vesta.x0x.X0xAgent
import uk.veritaslabs.vesta.x0x.X0xApiException
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage
import uk.veritaslabs.vesta.x0x.X0xEventStream
import uk.veritaslabs.vesta.x0x.X0xGroupMember
import uk.veritaslabs.vesta.x0x.X0xPresenceEvent

/** A tiny in-memory x0x mesh: nodes can create groups, invite and join, and send each other direct messages. */
class FakeX0xNetwork {

    class Group(val creator: String) {
        val members = linkedMapOf<String, X0xGroupMember>()
    }

    val groups = mutableMapOf<String, Group>()
    private val nodes = mutableMapOf<String, Node>()
    private var nextGroup = 0

    /** Agents whose direct messages are silently dropped, as if offline. */
    val offline = mutableSetOf<String>()

    /** Agents that don't yet see themselves in a group's roster, as while their admission is being committed. */
    val lagging = mutableSetOf<String>()

    /** Takes [agentId] online: its direct messages arrive again, and every other node sees it come online. */
    fun comeOnline(agentId: String) {
        offline -= agentId
        nodes.values.filter { it.agentId != agentId }.forEach { it.onPresence(X0xPresenceEvent("online", agentId)) }
    }

    fun node(agentId: String): Node = Node(agentId).also { nodes[agentId] = it }

    inner class Node(val agentId: String) : X0xClient by FakeX0xClient() {

        /** Called for every direct message delivered to this node. */
        var onDirect: (X0xDirectMessage) -> Unit = {}

        val sentTo = mutableListOf<String>()

        /** Called for every presence event while this node's presence stream is open. */
        var onPresence: (X0xPresenceEvent) -> Unit = {}

        override suspend fun presenceEvents(onEvent: (X0xPresenceEvent) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream {
            onPresence = onEvent
            return X0xEventStream { onPresence = {}; onClose(null) }
        }

        override suspend fun agent() = X0xAgent(agentId = agentId)

        override suspend fun connectAgent(agentId: String) = JsonObject(emptyMap())

        override suspend fun sendDirect(agentId: String, payload: ByteArray): JsonObject {
            sentTo += agentId
            val target = nodes[agentId]
            if (target != null && agentId !in offline) {
                target.onDirect(
                    X0xDirectMessage(sender = this.agentId, payload = Base64.Default.encode(payload), verified = true, trustDecision = "unknown"),
                )
            }
            return JsonObject(emptyMap())
        }

        override suspend fun createGroup(name: String, description: String, preset: String?): String {
            val id = "group-${nextGroup++}"
            groups[id] = Group(agentId).apply { members[agentId] = X0xGroupMember(agentId, role = "admin", state = "active") }
            return id
        }

        override suspend fun createGroupInvite(groupId: String): String {
            requireAdmin(groupId)
            return "invite:$groupId"
        }

        override suspend fun joinGroup(invite: String): String {
            val id = invite.removePrefix("invite:")
            val group = groups[id] ?: throw X0xApiException(404, "group not found")
            group.members[agentId] = X0xGroupMember(agentId, role = "member", state = "active")
            return id
        }

        override suspend fun getGroupMembers(groupId: String): List<X0xGroupMember> {
            val group = groups[groupId]?.takeIf { agentId in it.members } ?: throw X0xApiException(404, "group not found")
            return group.members.values.filter { !(it.agentId == agentId && agentId in lagging) }
        }

        override suspend fun getGroupCreator(groupId: String): String? = groups[groupId]?.creator

        override suspend fun setGroupMemberRole(groupId: String, agentId: String, role: String) {
            requireAdmin(groupId)
            val group = groups.getValue(groupId)
            group.members[agentId] = group.members.getValue(agentId).copy(role = role)
        }

        override suspend fun removeGroupMember(groupId: String, agentId: String) {
            requireAdmin(groupId)
            val group = groups.getValue(groupId)
            // As the real daemon: only the creator itself can take the creator out.
            if (agentId == group.creator && this.agentId != group.creator) {
                throw X0xApiException(409, "member_key_package_pending")
            }
            group.members.remove(agentId)
        }

        override suspend fun leaveGroup(groupId: String) {
            groups[groupId]?.members?.remove(agentId)
        }

        private fun requireAdmin(groupId: String) {
            val me = groups[groupId]?.members?.get(agentId)
            if (me?.role != "admin") throw X0xApiException(403, "admin role required")
        }
    }
}
