package uk.veritaslabs.vesta.agent

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.veritaslabs.vesta.db.VestaDatabase

/** What command handling needs to know about who may command the app. */
interface AgentAccess {
    suspend fun isAllowed(agentId: String): Boolean

    /** Notes that [agentId] wants to command the app and is waiting for the user's approval. Idempotent. */
    suspend fun requestApproval(agentId: String)
}

/** An agent waiting for the user to approve or deny it. */
data class PendingAgent(val agentId: String, val requestedAt: Long)

/**
 * [AgentAccess] backed by the local `AgentAccess` table, plus [preApproved] agents that are always allowed
 * (the `VESTA_AGENT_ALLOWLIST` environment variable, for headless setups) without a table row.
 *
 * The x0x daemon's trust decision is a separate, earlier gate: only agents the daemon already verified and
 * trusts ever get here. This is the app-specific permission to actually change Vesta's data.
 */
class AgentAccessRepository(
    private val database: VestaDatabase,
    private val preApproved: AgentAllowlist = FixedAgentAllowlist(),
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AgentAccess {

    /** Agents awaiting a decision, oldest first, re-emitted whenever the table changes. */
    val pending: Flow<List<PendingAgent>> = database.agentAccessQueries.selectPending()
        .asFlow()
        .map { query -> query.awaitAsList().map { PendingAgent(it.agentId, it.updatedAt) } }

    override suspend fun isAllowed(agentId: String): Boolean =
        preApproved.allows(agentId) || database.agentAccessQueries.selectStatus(agentId).awaitAsOneOrNull() == ALLOWED

    override suspend fun requestApproval(agentId: String) {
        database.agentAccessQueries.insertPending(agentId, nowMillis())
    }

    /** Lets [agentId] command the app from now on. */
    suspend fun allow(agentId: String) {
        database.agentAccessQueries.upsertAllowed(agentId, nowMillis())
    }

    /** Dismisses [agentId]'s pending request; it is asked about again if it sends another command. */
    suspend fun deny(agentId: String) {
        database.agentAccessQueries.deletePending(agentId)
    }

    private companion object {
        const val ALLOWED = "allowed"
    }
}
