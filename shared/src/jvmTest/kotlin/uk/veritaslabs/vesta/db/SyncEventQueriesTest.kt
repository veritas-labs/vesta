package uk.veritaslabs.vesta.db

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/** ADR-033: the Sync activity log and the per-device last-synced times. */
class SyncEventQueriesTest {

    private suspend fun VestaDatabase.event(homeId: String, lastAt: Long, kind: String = "received", module: String = "pantry", peer: String? = "peer-1") =
        syncEventQueries.insert(homeId, lastAt, lastAt, kind, module, peer, null, 1L)

    @Test
    fun pagesNewestFirstAndOnlyBeforeTheCursor() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.homeQueries.insert("h1", "The Johnsons", "family", 0)
        db.homeQueries.insert("h2", "Flat 4", "family", 0)
        listOf(100L, 300L, 200L, 400L).forEach { db.event("h1", it) }
        db.event("h2", 500L)

        // Act
        val firstPage = db.syncEventQueries.selectPage("h1", Long.MAX_VALUE, 3L).awaitAsList()
        val secondPage = db.syncEventQueries.selectPage("h1", firstPage.last().lastAt, 3L).awaitAsList()

        // Assert
        assertEquals(listOf(400L, 300L, 200L), firstPage.map { it.lastAt })
        assertEquals(listOf(100L), secondPage.map { it.lastAt })
    }

    @Test
    fun pagesOfOneKindLeaveTheOthersOut() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.homeQueries.insert("h1", "The Johnsons", "family", 0)
        db.event("h1", 100L, kind = "failed")
        db.event("h1", 200L, kind = "received")
        db.event("h1", 300L, kind = "failed")

        // Act
        val failed = db.syncEventQueries.selectPageOfKind("h1", "failed", Long.MAX_VALUE, 10L).awaitAsList()

        // Assert
        assertEquals(listOf(300L, 100L), failed.map { it.lastAt })
    }

    @Test
    fun findOpenReturnsTheLatestMatchingRowInsideTheWindowOnly() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.homeQueries.insert("h1", "The Johnsons", "family", 0)
        db.event("h1", 100L)
        db.event("h1", 200L)
        db.event("h1", 300L, module = "shopping")
        db.event("h1", 400L, kind = "sent")

        // Act
        val inside = db.syncEventQueries.findOpen("h1", "pantry", "peer-1", "received", 150L).awaitAsOneOrNull()
        val outside = db.syncEventQueries.findOpen("h1", "pantry", "peer-1", "received", 250L).awaitAsOneOrNull()
        val otherPeer = db.syncEventQueries.findOpen("h1", "pantry", "peer-2", "received", 0L).awaitAsOneOrNull()

        // Assert
        assertEquals(200L, inside?.lastAt)
        assertNull(outside)
        assertNull(otherPeer)
    }

    @Test
    fun findOpenMatchesANullPeerOnlyAgainstANullPeer() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.homeQueries.insert("h1", "The Johnsons", "family", 0)
        db.event("h1", 100L, kind = "membership", module = "members", peer = null)
        db.event("h1", 200L, kind = "membership", module = "members", peer = "peer-1")

        // Act
        val withoutPeer = db.syncEventQueries.findOpen("h1", "members", null, "membership", 0L).awaitAsOneOrNull()
        val withPeer = db.syncEventQueries.findOpen("h1", "members", "peer-1", "membership", 0L).awaitAsOneOrNull()

        // Assert
        assertEquals(100L, withoutPeer?.lastAt)
        assertNull(withoutPeer?.peerAgent)
        assertEquals(200L, withPeer?.lastAt)
    }

    @Test
    fun bumpMovesLastAtAndCountButKeepsTheFirstOccurrence() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.homeQueries.insert("h1", "The Johnsons", "family", 0)
        db.event("h1", 100L)
        val row = db.syncEventQueries.selectPage("h1", Long.MAX_VALUE, 1L).awaitAsList().single()

        // Act
        db.syncEventQueries.bump(900L, 5L, row.id)

        // Assert
        val bumped = db.syncEventQueries.selectPage("h1", Long.MAX_VALUE, 1L).awaitAsList().single()
        assertEquals(100L, bumped.at)
        assertEquals(900L, bumped.lastAt)
        assertEquals(5L, bumped.count)
    }

    @Test
    fun pruneOlderThanDeletesOnlyRowsWhoseLastAtIsBeforeTheCutoff() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.homeQueries.insert("h1", "The Johnsons", "family", 0)
        listOf(100L, 200L, 300L).forEach { db.event("h1", it) }

        // Act
        db.syncEventQueries.pruneOlderThan(200L)

        // Assert
        assertEquals(listOf(300L, 200L), db.syncEventQueries.selectPage("h1", Long.MAX_VALUE, 10L).awaitAsList().map { it.lastAt })
    }

    @Test
    fun deletingTheHomeCascadesToItsEventsAndDeleteForHomeClearsJustThatHome() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.homeQueries.insert("h1", "The Johnsons", "family", 0)
        db.homeQueries.insert("h2", "Flat 4", "family", 0)
        db.homeQueries.insert("h3", "Cabin", "family", 0)
        db.event("h1", 100L)
        db.event("h2", 100L)
        db.event("h3", 100L)

        // Act
        db.homeQueries.deleteById("h1")
        db.syncEventQueries.deleteForHome("h2")

        // Assert
        assertEquals(emptyList(), db.syncEventQueries.selectPage("h1", Long.MAX_VALUE, 10L).awaitAsList())
        assertEquals(emptyList(), db.syncEventQueries.selectPage("h2", Long.MAX_VALUE, 10L).awaitAsList())
        assertEquals(1, db.syncEventQueries.selectPage("h3", Long.MAX_VALUE, 10L).awaitAsList().size)
    }

    @Test
    fun peerSyncUpsertReplacesTheTimeForAnAgent() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.peerSyncQueries.upsert("peer-1", 100L)
        db.peerSyncQueries.upsert("peer-2", 150L)

        // Act
        db.peerSyncQueries.upsert("peer-1", 300L)

        // Assert
        assertEquals(300L, db.peerSyncQueries.selectByAgent("peer-1").awaitAsOneOrNull()?.lastSyncedAt)
        assertNull(db.peerSyncQueries.selectByAgent("peer-3").awaitAsOneOrNull())
        assertEquals(mapOf("peer-1" to 300L, "peer-2" to 150L), db.peerSyncQueries.selectAll().awaitAsList().associate { it.agentId to it.lastSyncedAt })
    }

    /** The schema as it was at version 19: everything except SyncEvent and PeerSync. */
    private object VersionNineteenSchema : SqlSchema<QueryResult.AsyncValue<Unit>> {
        override val version = 19L

        override fun create(driver: SqlDriver) = QueryResult.AsyncValue {
            VestaDatabase.Schema.create(driver).await()
            driver.execute(null, "DROP TABLE SyncEvent", 0).await()
            driver.execute(null, "DROP TABLE PeerSync", 0).await()
            Unit
        }

        override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: app.cash.sqldelight.db.AfterVersion) =
            QueryResult.AsyncValue { }
    }

    @Test
    fun migratingAVersionNineteenDatabaseAddsBothTables() = runBlocking {
        // Arrange
        val file = File.createTempFile("vesta-sync-event-migration", ".db").apply { deleteOnExit() }
        fun open(schema: SqlSchema<QueryResult.AsyncValue<Unit>>) = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.File(file.absolutePath),
            schema = schema,
            configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
        )
        val old = open(VersionNineteenSchema)
        VestaDatabase(old).homeQueries.insert("h1", "The Johnsons", "family", 0)
        old.close()

        // Act
        val migrated = VestaDatabase(open(VestaDatabase.Schema))
        migrated.syncEventQueries.insert("h1", 100L, 100L, "sent", "pantry", "peer-1", null, 1L)
        migrated.peerSyncQueries.upsert("peer-1", 100L)

        // Assert
        assertEquals(20L, VestaDatabase.Schema.version)
        assertNotNull(migrated.syncEventQueries.findOpen("h1", "pantry", "peer-1", "sent", 0L).awaitAsOneOrNull())
        assertEquals(100L, migrated.peerSyncQueries.selectByAgent("peer-1").awaitAsOneOrNull()?.lastSyncedAt)
    }
}
