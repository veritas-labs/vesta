package uk.veritaslabs.vesta.db

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
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.agent.AccessDecision
import uk.veritaslabs.vesta.agent.AssistantCapability
import uk.veritaslabs.vesta.agent.AssistantRepository
import uk.veritaslabs.vesta.agent.AssistantStatus

/** ADR-027: agents approved before assistants existed keep what they could do. */
class AssistantMigrationTest {

    /** The schema as it was at version 18: everything except the three Assistant tables. */
    private object VersionEighteenSchema : SqlSchema<QueryResult.AsyncValue<Unit>> {
        override val version = 18L

        override fun create(driver: SqlDriver) = QueryResult.AsyncValue {
            VestaDatabase.Schema.create(driver).await()
            driver.execute(null, "DROP TABLE AssistantActivity", 0).await()
            driver.execute(null, "DROP TABLE AssistantGrant", 0).await()
            driver.execute(null, "DROP TABLE Assistant", 0).await()
            Unit
        }

        override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: app.cash.sqldelight.db.AfterVersion) =
            QueryResult.AsyncValue { }
    }

    @Test
    fun anAllowedAgentBecomesAnActiveAssistantOnEveryExistingHomeAndAPendingOneStaysPending() = runBlocking {
        // Arrange
        val file = File.createTempFile("vesta-assistant-migration", ".db").apply { deleteOnExit() }
        fun open(schema: SqlSchema<QueryResult.AsyncValue<Unit>>) = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.File(file.absolutePath),
            schema = schema,
            configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
        )
        val old = open(VersionEighteenSchema)
        val oldDatabase = VestaDatabase(old)
        oldDatabase.homeQueries.insert("h1", "The Johnsons", "family", 0)
        oldDatabase.homeQueries.insert("h2", "Flat 4", "family", 0)
        old.execute(null, "INSERT INTO AgentAccess(agentId, status, updatedAt) VALUES ('allowed-agent', 'allowed', 5)", 0).await()
        old.execute(null, "INSERT INTO AgentAccess(agentId, status, updatedAt) VALUES ('pending-agent', 'pending', 6)", 0).await()
        old.close()

        // Act
        val migrated = VestaDatabase(open(VestaDatabase.Schema))
        val assistants = AssistantRepository(migrated)

        // Assert
        assertEquals(20L, VestaDatabase.Schema.version)
        assertEquals(
            mapOf("allowed-agent" to AssistantStatus.Active, "pending-agent" to AssistantStatus.Pending),
            migrated.assistantQueries.selectAll().awaitAsList().associate { it.agentId to (if (it.status == "active") AssistantStatus.Active else AssistantStatus.Pending) },
        )
        assertEquals(setOf("h1", "h2"), assistants.visibleHomes("allowed-agent"))
        assertEquals(AccessDecision.Allow, assistants.decide("allowed-agent", "h2", AssistantCapability.PantryAdd, "c1"))
        assertEquals(AccessDecision.NotPermitted, assistants.decide("pending-agent", "h1", AssistantCapability.PantryAdd, "c1"))
    }
}
