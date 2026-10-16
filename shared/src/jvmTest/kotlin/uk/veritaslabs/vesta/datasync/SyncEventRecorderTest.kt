package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.vesta.syncactivity.VestaSyncModules
import uk.veritaslabs.vesta.getDeviceName
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.awaitAsList
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.automerge.HomeRegistryDocumentId
import uk.veritaslabs.vesta.automerge.ProfileDocumentId
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.db.SyncEvent
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.openInMemoryDatabase

class SyncEventRecorderTest {

    private val minute = 60_000L
    private var now = 1_000_000L

    private fun newDatabase(): KitsDatabase = openInMemoryDatabase().also {
        runBlocking { it.homeQueries.insert("home", "Home", "family", 0L) }
    }

    private fun recorder(database: KitsDatabase) = SyncEventRecorder(database, nowMillis = { now })

    private suspend fun rows(database: KitsDatabase): List<SyncEvent> =
        database.syncEventQueries.selectPage("home", Long.MAX_VALUE, 100).awaitAsList()

    @Test
    fun `the same event within five minutes folds into one row and counts`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)

        // Act
        recorder.received("home", VestaSyncModules.Pantry, "peer")
        now += minute
        recorder.received("home", VestaSyncModules.Pantry, "peer")
        now += minute
        recorder.received("home", VestaSyncModules.Pantry, "peer")
        recorder.flush()

        // Assert
        val row = rows(database).single()
        assertEquals(3L, row.count)
        assertEquals(1_000_000L, row.at)
        assertEquals(1_000_000L + 2 * minute, row.lastAt)
    }

    @Test
    fun `a flush folds into a row an earlier flush wrote`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)
        recorder.sent("home", VestaSyncModules.Tasks, "peer")
        recorder.flush()

        // Act
        now += 2 * minute
        recorder.sent("home", VestaSyncModules.Tasks, "peer")
        recorder.flush()

        // Assert
        assertEquals(2L, rows(database).single().count)
    }

    @Test
    fun `an event after the five minute window is a new row`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)
        recorder.received("home", VestaSyncModules.Pantry, "peer")
        recorder.flush()

        // Act
        now += 6 * minute
        recorder.received("home", VestaSyncModules.Pantry, "peer")
        recorder.flush()

        // Assert
        assertEquals(listOf(1L, 1L), rows(database).map { it.count })
    }

    @Test
    fun `a different peer, module or kind does not fold`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)

        // Act
        recorder.received("home", VestaSyncModules.Pantry, "peer")
        recorder.received("home", VestaSyncModules.Pantry, "other")
        recorder.received("home", VestaSyncModules.Shopping, "peer")
        recorder.sent("home", VestaSyncModules.Pantry, "peer")
        recorder.flush()

        // Assert
        assertEquals(4, rows(database).size)
    }

    @Test
    fun `failed, membership and mailbox events keep their kind and detail`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)

        // Act
        recorder.failed("home", VestaSyncModules.Pantry, "peer", "timed out")
        recorder.membership("home", "peer", "joined")
        recorder.mailbox("home", incoming = true, peer = null)
        recorder.mailbox("home", incoming = false, peer = "peer")
        recorder.flush()

        // Assert
        val byKind = rows(database).associateBy { it.kind }
        assertEquals("timed out", byKind.getValue(SyncEventKinds.Failed).detail)
        assertEquals(SyncModules.Members, byKind.getValue(SyncEventKinds.Membership).module)
        assertNull(byKind.getValue(SyncEventKinds.MailboxIn).peerAgent)
        assertEquals("peer", byKind.getValue(SyncEventKinds.MailboxOut).peerAgent)
    }

    @Test
    fun `the table has no content columns`() {
        // Arrange
        val expected = setOf("id", "homeId", "at", "lastAt", "kind", "module", "peerAgent", "detail", "count")

        // Act
        val columns = SyncEvent::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet()

        // Assert
        assertEquals(expected, columns)
    }

    @Test
    fun `a closed database does not make recording fail`() = runBlocking {
        // Arrange
        val driver = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.Memory,
            schema = KitsDatabase.Schema,
            configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
        )
        val recorder = recorder(KitsDatabase(driver))
        driver.close()

        // Act
        recorder.received("home", VestaSyncModules.Pantry, "peer")
        recorder.flush()
        recorder.lastSynced("peer")
        recorder.prune(now)
        recorder.forgetHome("home")
        val read = recorder.lastSyncedAt("other")

        // Assert
        assertNull(read)
    }

    @Test
    fun `a failed flush drops the buffered events`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)
        recorder.received("unknown-home", VestaSyncModules.Pantry, "peer")
        recorder.flush()

        // Act
        recorder.received("home", VestaSyncModules.Pantry, "peer")
        recorder.flush()

        // Assert
        assertEquals(1, rows(database).size)
    }

    @Test
    fun `one event that cannot be written does not drop the rest of the batch`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)
        recorder.received("home", VestaSyncModules.Pantry, "first")
        recorder.received("gone", VestaSyncModules.Pantry, "orphan")
        recorder.received("home", VestaSyncModules.Shopping, "last")

        // Act
        recorder.flush()

        // Assert
        assertEquals(setOf("first", "last"), rows(database).mapNotNull { it.peerAgent }.toSet())
    }

    @Test
    fun `events that differ only in detail do not fold`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)
        recorder.membership("home", "peer", "removed")
        recorder.flush()

        // Act
        recorder.membership("home", "peer", "acknowledged")
        recorder.membership("home", "peer", "acknowledged")
        recorder.flush()

        // Assert
        assertEquals(setOf("removed" to 1L, "acknowledged" to 2L), rows(database).map { it.detail to it.count }.toSet())
    }

    @Test
    fun `prune removes rows older than seven days and keeps newer ones`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)
        val day = 24 * 60 * minute
        now = 100 * day
        recorder.received("home", VestaSyncModules.Pantry, "old")
        recorder.flush()
        now += 6 * day
        recorder.received("home", VestaSyncModules.Pantry, "new")
        recorder.flush()

        // Act
        now += 2 * day
        recorder.prune(now)

        // Assert
        assertEquals(listOf("new"), rows(database).map { it.peerAgent })
    }

    @Test
    fun `forgetting a home removes its rows and buffered events`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)
        recorder.received("home", VestaSyncModules.Pantry, "peer")
        recorder.flush()
        recorder.sent("home", VestaSyncModules.Pantry, "peer")

        // Act
        recorder.forgetHome("home")
        recorder.flush()

        // Assert
        assertEquals(0, rows(database).size)
    }

    @Test
    fun `last synced is written at most once a minute per peer`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)
        recorder.lastSynced("peer")
        val first = now

        // Act
        now += 30_000L
        recorder.lastSynced("peer")
        val afterSecond = database.peerSyncQueries.selectAll().awaitAsList().single().lastSyncedAt
        now += minute
        recorder.lastSynced("peer")
        val afterThird = database.peerSyncQueries.selectAll().awaitAsList().single().lastSyncedAt

        // Assert
        assertEquals(first, afterSecond)
        assertEquals(now, afterThird)
    }

    @Test
    fun `last synced is read from memory first and from the table by a new recorder`() = runBlocking {
        // Arrange
        val database = newDatabase()
        val recorder = recorder(database)
        recorder.lastSynced("peer")
        val written = now
        now += 10_000L
        recorder.lastSynced("peer")

        // Act
        val inMemory = recorder.lastSyncedAt("peer")
        val fromTable = recorder(database).lastSyncedAt("peer")
        val unknown = recorder(database).lastSyncedAt("stranger")

        // Assert
        assertEquals(now, inMemory)
        assertEquals(written, fromTable)
        assertNull(unknown)
    }

    @Test
    fun `document ids map to modules and registry and profile map to none`() {
        // Arrange
        val expected = mapOf(
            "h:pantry" to VestaSyncModules.Pantry,
            "h:roster" to SyncModules.Members,
            "h:shopping-list" to VestaSyncModules.Shopping,
            "h:cookbook" to VestaSyncModules.Cookbook,
            "h:merchants" to VestaSyncModules.Merchants,
            "h:mealplan" to VestaSyncModules.MealPlan,
            "h:calendar" to VestaSyncModules.Calendar,
            "h:private-calendar" to VestaSyncModules.Calendar,
            "h:tasks" to VestaSyncModules.Tasks,
            "h:private-tasks" to VestaSyncModules.Tasks,
        )

        val database = newDatabase()
        val registry = TestHomeDocuments(database, HomeRosters(database, ProfileRepository(database, deviceName = ::getDeviceName), deviceName = ::getDeviceName), publishRosters = {}).registry

        // Act
        val actual = expected.keys.associateWith { registry.moduleOf(it) }

        // Assert
        assertEquals(expected, actual)
        assertNull(registry.moduleOf(HomeRegistryDocumentId))
        assertNull(registry.moduleOf(ProfileDocumentId))
    }
}
