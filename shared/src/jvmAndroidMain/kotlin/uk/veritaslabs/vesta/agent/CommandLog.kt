package uk.veritaslabs.vesta.agent

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import uk.veritaslabs.kits.store.KitsDatabase

/** Which agent commands have been processed and what they were answered with (ADR-009 behaviour rule 5). */
interface CommandLog {
    /** The reply JSON recorded for [senderId]'s [commandId], or null if it hasn't been processed. */
    suspend fun reply(senderId: String, commandId: String): String?

    suspend fun record(senderId: String, commandId: String, replyJson: String)
}

/** [CommandLog] persisted in the local, unsynced `AgentCommandLog` table. */
class DatabaseCommandLog(
    private val database: KitsDatabase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CommandLog {

    override suspend fun reply(senderId: String, commandId: String): String? =
        database.agentCommandLogQueries.selectReply(senderId, commandId).awaitAsOneOrNull()

    override suspend fun record(senderId: String, commandId: String, replyJson: String) {
        database.agentCommandLogQueries.insert(senderId, commandId, replyJson, nowMillis())
    }
}
