package uk.veritaslabs.vesta.x0x

import kotlin.io.encoding.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Implements every [X0xClient] method except [X0xClient.endpoint] in terms of one abstract seam,
 * [resolveDaemon] — so the per-platform subclasses ([EmbeddedX0xClient][uk.veritaslabs.vesta.x0x],
 * `StandaloneX0xClient`) only need to know how to find their daemon's base URL and bearer token;
 * every REST call, JSON envelope and error path is implemented once here, ported from Colo's
 * `town.colo.x0x.X0xClient`.
 */
abstract class AbstractX0xClient : X0xClient {

    private val json = Json { ignoreUnknownKeys = true }

    /** The daemon's base URL (e.g. `http://127.0.0.1:12700`) and bearer token, or null when not reachable/discovered. */
    protected abstract suspend fun resolveDaemon(): Pair<String, String?>?

    override suspend fun health(): X0xHealth? {
        val (baseUrl, token) = resolveDaemon() ?: return null
        val result = runCatching { httpRequest("GET", "$baseUrl/health", token, null) }.getOrNull() ?: return null
        if (result.status !in 200..299) return null
        return X0xHealth.parse(result.body)
    }

    // --- Core ---

    override suspend fun agent(): X0xAgent = json.decodeFromJsonElement(get("/agent"))

    override suspend fun createSession(): X0xSession = json.decodeFromJsonElement(post("/auth/session"))

    // --- Gossip pub/sub ---

    override suspend fun subscribe(topic: String): JsonObject =
        post("/subscribe", buildJsonObject { put("topic", topic) })

    override suspend fun publish(topic: String, payload: ByteArray): JsonObject =
        post(
            "/publish",
            buildJsonObject {
                put("topic", topic)
                put("payload", Base64.Default.encode(payload))
            },
        )

    override suspend fun gossipEvents(onMessage: (X0xGossipMessage) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream =
        events("/events", onClose) { event ->
            val root = json.parseToJsonElement(event).jsonObject
            if (root["type"]?.jsonPrimitive?.content == "message") {
                val data = root["data"]?.jsonObject ?: return@events
                onMessage(json.decodeFromJsonElement(data))
            }
        }

    // --- Direct messaging ---

    override suspend fun connectAgent(agentId: String): JsonObject =
        post("/agents/connect", buildJsonObject { put("agent_id", agentId) })

    override suspend fun sendDirect(agentId: String, payload: ByteArray): JsonObject =
        post(
            "/direct/send",
            buildJsonObject {
                put("agent_id", agentId)
                put("payload", Base64.Default.encode(payload))
            },
        )

    override suspend fun listDirectConnections(): JsonObject = get("/direct/connections")

    override suspend fun directEvents(onMessage: (X0xDirectMessage) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream =
        events("/direct/events", onClose) { event ->
            onMessage(json.decodeFromString(X0xDirectMessage.serializer(), event))
        }

    override suspend fun presenceEvents(onEvent: (X0xPresenceEvent) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream =
        events("/presence/events", onClose) { event ->
            onEvent(json.decodeFromString(X0xPresenceEvent.serializer(), event))
        }

    // --- Store (replicated KV CRDT) ---

    override suspend fun createStore(name: String, topic: String, policy: String): String =
        post(
            "/stores",
            buildJsonObject { put("name", name); put("topic", topic); put("policy", policy) },
        )["id"]!!.jsonPrimitive.content

    override suspend fun listStores(): JsonObject = get("/stores")

    override suspend fun listStoreKeys(storeId: String): List<X0xStoreKey> {
        val keys = get("/stores/${encodePathSegment(storeId)}/keys")["keys"]?.jsonArray ?: return emptyList()
        return keys.map { json.decodeFromJsonElement(X0xStoreKey.serializer(), it) }
    }

    override suspend fun getValue(storeId: String, key: String): ByteArray? {
        val result = call("GET", storeValuePath(storeId, key), allowNotFound = true) ?: return null
        val encoded = result["value"]?.jsonPrimitive?.content ?: return null
        return Base64.Default.decode(encoded)
    }

    override suspend fun putValue(storeId: String, key: String, value: ByteArray, contentType: String) {
        put(
            storeValuePath(storeId, key),
            buildJsonObject {
                put("value", Base64.Default.encode(value))
                put("content_type", contentType)
            },
        )
    }

    override suspend fun deleteValue(storeId: String, key: String) {
        delete(storeValuePath(storeId, key))
    }

    override suspend fun joinStore(storeId: String, expectedOwner: String) {
        post("/stores/${encodePathSegment(storeId)}/join", buildJsonObject { put("expected_owner", expectedOwner) })
    }

    override suspend fun openGroupStore(groupId: String, name: String): String =
        post("/groups/$groupId/stores", buildJsonObject { put("name", name) })["id"]!!.jsonPrimitive.content

    // --- Named groups ---

    override suspend fun createGroup(name: String, description: String, preset: String?): String =
        post(
            "/groups",
            buildJsonObject {
                put("name", name)
                put("description", description)
                if (preset != null) put("preset", preset)
            },
        )["group_id"]!!.jsonPrimitive.content

    override suspend fun listGroups(): List<X0xGroupSummary> {
        val groups = get("/groups")["groups"]?.jsonArray ?: return emptyList()
        return groups.map { json.decodeFromJsonElement(X0xGroupSummary.serializer(), it) }
    }

    override suspend fun createGroupInvite(groupId: String, expirySecs: Long?, intendedJoiner: String?): String =
        post(
            "/groups/$groupId/invite",
            buildJsonObject {
                expirySecs?.let { put("expiry_secs", it) }
                intendedJoiner?.let { put("intended_joiner", it) }
            },
        )["invite_link"]!!.jsonPrimitive.content

    override suspend fun joinGroup(invite: String): String =
        post("/groups/join", buildJsonObject { put("invite", invite) })["group_id"]!!.jsonPrimitive.content

    override suspend fun getGroupMembers(groupId: String): List<X0xGroupMember> {
        val members = get("/groups/$groupId/members")["members"]?.jsonArray ?: return emptyList()
        return members.map { json.decodeFromJsonElement(X0xGroupMember.serializer(), it) }
    }

    override suspend fun getGroupCreator(groupId: String): String? =
        get("/groups/$groupId/state")["genesis"]?.jsonObject?.get("creator_agent_id")?.jsonPrimitive?.content

    override suspend fun setGroupMemberRole(groupId: String, agentId: String, role: String) {
        call("PATCH", "/groups/$groupId/members/$agentId/role", buildJsonObject { put("role", role) })
    }

    override suspend fun removeGroupMember(groupId: String, agentId: String) {
        delete("/groups/$groupId/members/$agentId")
    }

    override suspend fun leaveGroup(groupId: String) {
        delete("/groups/$groupId")
    }

    override suspend fun sendGroupMessage(groupId: String, body: String, kind: String): JsonObject =
        post("/groups/$groupId/send", buildJsonObject { put("body", body); put("kind", kind) })

    override suspend fun getGroupMessages(groupId: String): List<X0xGroupPublicMessage> {
        val messages = get("/groups/$groupId/messages")["messages"]?.jsonArray ?: return emptyList()
        return messages.map { json.decodeFromJsonElement(X0xGroupPublicMessage.serializer(), it) }
    }

    // --- Internals ---

    private suspend fun get(path: String): JsonObject = call("GET", path)!!
    private suspend fun post(path: String, body: JsonObject = buildJsonObject { }): JsonObject = call("POST", path, body)!!
    private suspend fun put(path: String, body: JsonObject = buildJsonObject { }): JsonObject = call("PUT", path, body)!!
    private suspend fun delete(path: String): JsonObject = call("DELETE", path)!!

    private suspend fun call(
        method: String,
        path: String,
        body: JsonObject? = null,
        allowNotFound: Boolean = false,
    ): JsonObject? {
        val (baseUrl, token) = resolveDaemon() ?: throw X0xApiException(0, "x0x daemon not reachable")
        val result = httpRequest(method, "$baseUrl$path", token, body?.let { json.encodeToString(JsonObject.serializer(), it) })
        if (result.status == 404 && allowNotFound) return null
        if (result.status >= 400) {
            val apiError = runCatching {
                json.parseToJsonElement(result.body.ifBlank { "{}" }).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull()
            throw X0xApiException(result.status, apiError ?: "x0x API error (HTTP ${result.status})")
        }
        return json.parseToJsonElement(result.body.ifBlank { "{}" }).jsonObject
    }

    private suspend fun events(path: String, onClose: (Throwable?) -> Unit, onEvent: (String) -> Unit): X0xEventStream {
        val (baseUrl, token) = resolveDaemon() ?: throw X0xApiException(0, "x0x daemon not reachable")
        return openSseStream("$baseUrl$path", token, onEvent, onClose)
    }
}

/** A store id such as `x0x/group/<gid>/kv/<hash>` holds `/`, so it is one path segment only once encoded. */
internal fun storeValuePath(storeId: String, key: String): String =
    "/stores/${encodePathSegment(storeId)}/${encodePathSegment(key)}"

/** Percent-encodes every byte of [segment] outside the RFC 3986 unreserved set, so `/` becomes `%2F`. */
internal fun encodePathSegment(segment: String): String = buildString {
    for (byte in segment.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
            append(ch)
        } else {
            append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
        }
    }
}
