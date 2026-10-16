package uk.veritaslabs.vesta.compat

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.awaitAsList
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.db.VestaDatabase

/**
 * Every table, column, index and the schema version, as literals captured before the Kits extraction
 * (docs/plans/kits-extraction-implementation-plan.md, WP10). `verifyMigrations` catches a schema that no longer matches
 * the checked-in snapshots; this says which name moved, readably, if a rename ever reaches one.
 */
class SchemaGuardTest {

    private val expectedColumns = mapOf(
        "AgentAccess" to "agentId,status,updatedAt",
        "AgentCommandLog" to "senderId,commandId,replyJson,processedAt",
        "AppSetting" to "key,value",
        "Assistant" to "agentId,name,addedAt,status",
        "AssistantActivity" to "id,agentId,homeId,capability,commandId,outcome,summary,at",
        "AssistantGrant" to "agentId,homeId,capability,policy,updatedAt",
        "AutomergeDocument" to "documentId,homeId,feature,bytes,updatedAt",
        "Blob" to "hash,size,mime,lastReferencedAt",
        "BlobWant" to "hash,homeId,size,firstWantedAt,lastAskedAt,attempts",
        "DevicePairingLink" to "id,publicKeyHex,createdAt,expiresAt,consumed",
        "DeviceSyncGroup" to "groupId",
        "Home" to "id,name,shape,createdAt",
        "HomeGroup" to "homeId,groupId,storeId",
        "HomeMember" to "homeId,memberId,name,avatarResourceId,role,joinedAt",
        "InviteCode" to "id,homeId,code,createdAt,expiresAt,revoked",
        "JoinRequest" to "homeId,memberId,name,agentId,secretHash,status,createdAt",
        "MailboxRead" to "homeId,writerAgentId,lastSequence",
        "MailboxSequence" to "homeId,nextSequence",
        "MailboxSnapshot" to "homeId,generation,coversThrough,createdAt,pruned",
        "MailboxWrite" to "homeId,documentId,heads",
        "PeerSync" to "agentId,lastSyncedAt",
        "PendingJoin" to "homeId,homeName,secret,adminAgents,expiresAt,denial,createdAt",
        "PendingMessage" to "recipient,homeId,kind,payload,createdAt",
        "Profile" to "id,displayName,avatarResourceId,x0xInstanceName,named",
        "SyncEvent" to "id,homeId,at,lastAt,kind,module,peerAgent,detail,count",
        "SyncedDevice" to "agentId,name,addedAt",
        "UserDocument" to "documentId,bytes,updatedAt",
    )

    private val expectedIndexes = listOf("AutomergeDocument_homeId", "InviteCode_code", "SyncEvent_homeId_lastAt")

    private fun freshDatabaseFile(): File {
        val file = File.createTempFile("schema-guard", ".db").apply { deleteOnExit() }
        val (database, driver) = openAsTheAppDoes(file)
        runBlocking { database.homeQueries.selectAll().awaitAsList() } // creates the schema
        driver.close()
        return file
    }

    private fun <T> query(file: File, sql: String, read: (androidx.sqlite.SQLiteStatement) -> T): List<T> =
        BundledSQLiteDriver().open(file.path).let { connection ->
            try {
                connection.prepare(sql).let { statement ->
                    try {
                        buildList { while (statement.step()) add(read(statement)) }
                    } finally {
                        statement.close()
                    }
                }
            } finally {
                connection.close()
            }
        }

    @Test
    fun everyTableHasExactlyTheColumnsItHadBeforeTheExtraction() {
        // Arrange
        val file = freshDatabaseFile()
        val tables = query(file, "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name") { it.getText(0) }

        // Act
        val actual = tables.associateWith { table -> query(file, "SELECT name FROM pragma_table_info('$table')") { it.getText(0) }.joinToString(",") }

        // Assert
        assertEquals(expectedColumns, actual)
    }

    @Test
    fun theIndexesAndTheSchemaVersionAreUnchanged() {
        // Arrange
        val file = freshDatabaseFile()

        // Act
        val indexes = query(file, "SELECT name FROM sqlite_master WHERE type = 'index' AND name NOT LIKE 'sqlite_%' ORDER BY name") { it.getText(0) }
        val version = query(file, "PRAGMA user_version") { it.getLong(0) }.single()

        // Assert
        assertEquals(expectedIndexes, indexes)
        assertEquals(20L, version)
        assertEquals(20L, VestaDatabase.Schema.version)
    }
}
