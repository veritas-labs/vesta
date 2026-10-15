package uk.veritaslabs.vesta.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * Runs every migration, 1.sqm through 19.sqm, against a database file that was created at schema version 1,
 * as a phone that has not been updated for a long time would. Data loss here is unrecoverable (local-first,
 * no server), so this is the safety net behind docs/testing/database-migrations.md.
 */
class FullMigrationChainTest {

    /** The schema as it was at version 1: before any .sqm existed (reconstructed from git history). */
    private object VersionOneSchema : SqlSchema<QueryResult.AsyncValue<Unit>> {
        override val version = 1L

        override fun create(driver: SqlDriver) = QueryResult.AsyncValue {
            listOf(
                "CREATE TABLE Home (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, shape TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                "CREATE TABLE Profile (id TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, avatarResourceId TEXT, " +
                    "x0xInstanceName TEXT NOT NULL DEFAULT 'default')",
                "CREATE TABLE HomeMember (homeId TEXT NOT NULL REFERENCES Home(id) ON DELETE CASCADE, " +
                    "profileId TEXT NOT NULL REFERENCES Profile(id) ON DELETE CASCADE, role TEXT NOT NULL, " +
                    "joinedAt INTEGER NOT NULL, PRIMARY KEY (homeId, profileId))",
                "CREATE TABLE InviteCode (id TEXT NOT NULL PRIMARY KEY, homeId TEXT NOT NULL REFERENCES Home(id) ON DELETE CASCADE, " +
                    "code TEXT NOT NULL, createdAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL, revoked INTEGER NOT NULL DEFAULT 0)",
                "CREATE INDEX InviteCode_code ON InviteCode(code)",
            ).forEach { driver.execute(null, it, 0).await() }
            Unit
        }

        override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: app.cash.sqldelight.db.AfterVersion) =
            QueryResult.AsyncValue { }
    }

    private fun open(file: File, schema: SqlSchema<QueryResult.AsyncValue<Unit>>) = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = AndroidxSqliteDatabaseType.File(file.absolutePath),
        schema = schema,
        configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
    )

    private suspend fun SqlDriver.strings(sql: String): List<String> =
        executeQuery(
            null, sql, { cursor: SqlCursor ->
                QueryResult.AsyncValue {
                    buildList { while (cursor.next().await()) add(cursor.getString(0)!!) }
                }
            }, 0,
        ).await()

    private fun tempFile() = File.createTempFile("vesta-migration-chain", ".db").apply { deleteOnExit() }

    private val tablesAndIndexes = "SELECT name FROM sqlite_master WHERE type IN ('table', 'index') AND name NOT LIKE 'sqlite_%' ORDER BY name"

    @Test
    fun aVersionOneDatabaseReachesTheCurrentVersionWithItsDataAndEveryTable() = runBlocking {
        // Arrange
        val file = tempFile()
        val old = open(file, VersionOneSchema)
        old.execute(null, "INSERT INTO Home(id, name, shape, createdAt) VALUES ('h1', 'The Johnsons', 'family', 10)", 0).await()
        old.execute(null, "INSERT INTO Profile(id, displayName, avatarResourceId, x0xInstanceName) VALUES ('p1', 'Mark', 'avatar-3', 'mark')", 0).await()
        old.execute(null, "INSERT INTO HomeMember(homeId, profileId, role, joinedAt) VALUES ('h1', 'p1', 'admin', 11)", 0).await()
        old.execute(null, "INSERT INTO InviteCode(id, homeId, code, createdAt, expiresAt, revoked) VALUES ('i1', 'h1', 'ABC123', 12, 99, 0)", 0).await()
        old.close()
        val fresh = open(tempFile(), VestaDatabase.Schema)
        val freshTables = fresh.strings(tablesAndIndexes)
        fresh.close()

        // Act
        val migratedDriver = open(file, VestaDatabase.Schema)
        val migrated = VestaDatabase(migratedDriver)

        // Assert
        assertEquals(20L, VestaDatabase.Schema.version)
        assertEquals("20", migratedDriver.strings("PRAGMA user_version").single())
        assertEquals(freshTables, migratedDriver.strings(tablesAndIndexes))
        val home = migrated.homeQueries.selectById("h1").awaitAsOne()
        assertEquals("The Johnsons", home.name)
        assertEquals("family", home.shape)
        assertEquals(10L, home.createdAt)
        val profile = migrated.profileQueries.selectById("p1").awaitAsOne()
        assertEquals("Mark", profile.displayName)
        assertEquals("avatar-3", profile.avatarResourceId)
        assertEquals("mark", profile.x0xInstanceName)
        assertEquals(0L, profile.named)
        val invite = migrated.inviteCodeQueries.selectByHome("h1").awaitAsList().single()
        assertEquals("ABC123", invite.code)
        assertEquals(99L, invite.expiresAt)
        // 8.sqm drops and recreates HomeMember (nothing wrote the old one), so no row survives, by design.
        assertEquals(emptyList(), migrated.homeMemberQueries.selectByHome("h1").awaitAsList())
        migratedDriver.close()
    }
}
