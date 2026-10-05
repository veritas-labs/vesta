package uk.veritaslabs.vesta.x0x

import kotlinx.serialization.json.JsonObject

/**
 * In-memory [X0xClient] for tests: no daemon. Only the direct-message surface does anything —
 * everything else throws, so a test that reaches for it fails loudly.
 */
class FakeX0xClient : X0xClient {

    /** One entry per [directEvents] call; the test drives the stream through it. */
    class OpenStream(val onMessage: (X0xDirectMessage) -> Unit, private val onClose: (Throwable?) -> Unit) : X0xEventStream {
        var closed = false
            private set

        fun emit(message: X0xDirectMessage) = onMessage(message)

        /** Simulates the server / connection ending the stream. */
        fun drop(error: Throwable? = null) = end(error)

        override fun close() = end(null)

        private fun end(error: Throwable?) {
            if (closed) return
            closed = true
            onClose(error)
        }
    }

    val openedStreams = mutableListOf<OpenStream>()
    val openAttempts = mutableListOf<Long>()

    /** When non-null, the next [directEvents] calls throw this instead of opening a stream. */
    var openFailure: Exception? = null

    class Sent(val agentId: String, val payload: ByteArray)

    val sent = mutableListOf<Sent>()

    /** When non-null, [sendDirect] throws this (after recording nothing). */
    var sendFailure: Exception? = null

    override val endpoint: String = "fake"

    override suspend fun directEvents(onMessage: (X0xDirectMessage) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream {
        openFailure?.let { throw it }
        return OpenStream(onMessage, onClose).also { openedStreams += it }
    }

    override suspend fun sendDirect(agentId: String, payload: ByteArray): JsonObject {
        sendFailure?.let { throw it }
        sent += Sent(agentId, payload)
        return JsonObject(emptyMap())
    }

    override suspend fun presenceEvents(onEvent: (X0xPresenceEvent) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream = X0xEventStream { }

    override suspend fun health(): X0xHealth? = null
    override suspend fun agent(): X0xAgent = TODO()
    override suspend fun createSession(): X0xSession = TODO()
    override suspend fun subscribe(topic: String): JsonObject = TODO()
    override suspend fun publish(topic: String, payload: ByteArray): JsonObject = TODO()
    override suspend fun gossipEvents(onMessage: (X0xGossipMessage) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream = TODO()
    override suspend fun connectAgent(agentId: String): JsonObject = TODO()
    override suspend fun listDirectConnections(): JsonObject = TODO()
    override suspend fun createStore(name: String, topic: String, policy: String): String = TODO()
    override suspend fun listStores(): JsonObject = TODO()
    override suspend fun listStoreKeys(storeId: String): List<X0xStoreKey> = TODO()
    override suspend fun getValue(storeId: String, key: String): ByteArray? = TODO()
    override suspend fun putValue(storeId: String, key: String, value: ByteArray, contentType: String) = TODO()
    override suspend fun deleteValue(storeId: String, key: String) = TODO()
    override suspend fun joinStore(storeId: String, expectedOwner: String) = TODO()
    override suspend fun createGroup(name: String, description: String, preset: String?): String = TODO()
    override suspend fun listGroups(): List<X0xGroupSummary> = TODO()
    override suspend fun createGroupInvite(groupId: String): String = TODO()
    override suspend fun joinGroup(invite: String): String = TODO()
    override suspend fun getGroupMembers(groupId: String): List<X0xGroupMember> = TODO()
    override suspend fun getGroupCreator(groupId: String): String? = TODO()
    override suspend fun setGroupMemberRole(groupId: String, agentId: String, role: String) = TODO()
    override suspend fun removeGroupMember(groupId: String, agentId: String) = TODO()
    override suspend fun leaveGroup(groupId: String) = TODO()
    override suspend fun sendGroupMessage(groupId: String, body: String, kind: String): JsonObject = TODO()
    override suspend fun getGroupMessages(groupId: String): List<X0xGroupPublicMessage> = TODO()
}
