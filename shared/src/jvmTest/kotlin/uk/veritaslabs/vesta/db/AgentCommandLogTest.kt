package uk.veritaslabs.vesta.db

import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.openInMemoryDatabase
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.agent.DatabaseCommandLog

class AgentCommandLogTest {

    @Test
    fun recordsAReplyPerSenderAndCommand() = runBlocking {
        // Arrange
        val log = DatabaseCommandLog(openInMemoryDatabase())

        // Act
        log.record("agent-1", "c1", """{"a":1}""")
        log.record("agent-1", "c1", """{"ignored":true}""")

        // Assert
        assertEquals("""{"a":1}""", log.reply("agent-1", "c1"))
        assertNull(log.reply("agent-2", "c1"))
        assertNull(log.reply("agent-1", "c2"))
    }

    /** The schema as it was at version 2: everything except AgentCommandLog, AgentAccess, SyncedDevice, DeviceSyncGroup, UserDocument, PendingMessage, JoinRequest, PendingJoin and Profile.named. */
    private object VersionTwoSchema : SqlSchema<QueryResult.AsyncValue<Unit>> {
        override val version = 2L

        override fun create(driver: SqlDriver) = QueryResult.AsyncValue {
            KitsDatabase.Schema.create(driver).await()
            driver.execute(null, "DROP TABLE AgentCommandLog", 0).await()
            driver.execute(null, "DROP TABLE AgentAccess", 0).await()
            driver.execute(null, "DROP TABLE SyncedDevice", 0).await()
            driver.execute(null, "DROP TABLE DeviceSyncGroup", 0).await()
            driver.execute(null, "DROP TABLE UserDocument", 0).await()
            driver.execute(null, "DROP TABLE PendingMessage", 0).await()
            // Added by migration 9, which can't be repeated, unlike the CREATE TABLE IF NOT EXISTS ones.
            driver.execute(null, "ALTER TABLE Profile DROP COLUMN named", 0).await()
            Unit
        }

        override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: app.cash.sqldelight.db.AfterVersion) =
            QueryResult.AsyncValue { }
    }

    @Test
    fun migratingAVersionTwoDatabaseAddsTheTable() = runBlocking {
        // Arrange
        val file = File.createTempFile("vesta-migration", ".db").apply { deleteOnExit() }
        fun open(schema: SqlSchema<QueryResult.AsyncValue<Unit>>) = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.File(file.absolutePath),
            schema = schema,
            configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
        )
        val old = open(VersionTwoSchema)
        KitsDatabase(old).homeQueries.selectAll().awaitAsList() // forces creation
        old.close()

        // Act
        val migrated = KitsDatabase(open(KitsDatabase.Schema))
        val log = DatabaseCommandLog(migrated)
        log.record("agent-1", "c1", "reply")

        // Assert
        assertEquals(20L, KitsDatabase.Schema.version)
        assertEquals("reply", log.reply("agent-1", "c1"))
        assertEquals(emptyList(), migrated.blobQueries.selectAllBlobs().awaitAsList())
        assertEquals(emptyList(), migrated.blobQueries.selectAllWants().awaitAsList())
    }
}
