package uk.veritaslabs.vesta.compat

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.async.coroutines.awaitAsList
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.kits.spaces.SpaceRegistryDocumentId
import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.vesta.datasync.HomeRosters
import uk.veritaslabs.vesta.datasync.TestHomeDocuments
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.homejoin.HomeJoinMessage
import uk.veritaslabs.kits.spaces.ProfileRepository

/**
 * The one-off that produced `src/jvmTest/resources/compat/vesta-v20.db` (docs/plans/kits-extraction-implementation-plan.md,
 * WP10), from the code as it was BEFORE any Kits extraction rename. It does nothing unless `WRITE_COMPAT_FIXTURE` is set,
 * and the committed file must never be regenerated: it is the stand-in for a database an existing install already holds.
 * `git diff --exit-code` on the resource after a test run proves it was not.
 */
class WriteExistingInstallFixture {

    @Test
    fun writesTheVersionTwentyDatabaseOfAnExistingInstallWhenAsked() = runBlocking {
        // Arrange
        if (System.getenv("WRITE_COMPAT_FIXTURE").isNullOrEmpty()) return@runBlocking
        val target = File("src/jvmTest/resources/compat/vesta-v20.db").absoluteFile
        val work = File.createTempFile("vesta-v20", ".db")
        val (database, driver) = openAsTheAppDoes(work)
        val profiles = ProfileRepository(database, deviceName = { "Mark's desktop" })
        val rosters = HomeRosters(database, profiles, nowMillis = { 1_700_000_100_000L }, deviceName = { "Mark's desktop" })
        val homes = HomeRepository(database, profiles = profiles, nowMillis = { 1_700_000_000_000L })

        // Act
        val me = profiles.current()
        val homeId = homes.create("The Johnsons", "family")
        rosters.publish(homeId, "agent-1", emptySet())
        rosters.admit(homeId, "member-2", "Sam", "member", "agent-2")
        rosters.admit(homeId, "member-3", "Alex", "member", "agent-3")
        rosters.remove(homeId, "member-3")
        rosters.createInvite(homeId, me.id, ttlMillis = 86_400_000L, maxUses = 2, ownAgentId = "agent-1")
        rosters.addHub(homeId, "hub-agent")
        rosters.setGroup(homeId, "group-1")
        rosters.markGone("agent-9")
        val documents = TestHomeDocuments(database, rosters, publishRosters = {}, sources = VestaHomeSources(database))
        documents.registry.documentIds(homeId).forEach { documents.registry.open(homeId, it) }
        VestaHomeSources(database).pantries.forHome(homeId)?.addPrimary("Fixture drinks")
        database.userDocumentQueries.upsert(SpaceRegistryDocumentId, Document().save(), 1_700_000_000_000L)
        database.pendingMessageQueries.upsert("agent-2", homeId, HomeJoinMessage.LeftKind, HomeJoinMessage.encode(HomeJoinMessage.Left(homeId, me.id)), 1_700_000_000_000L)
        database.mailboxQueries.upsertHeads(homeId, "$homeId:pantry", ByteArray(32) { it.toByte() })
        database.homeGroupQueries.upsert(homeId, "group-1", null)
        database.homeQueries.selectAll().awaitAsList()
        driver.close()
        fillEveryEmptyTable(work, homeId)
        singleFile(work)

        // Assert
        target.parentFile.mkdirs()
        work.copyTo(target, overwrite = true)
    }

    /** A row of placeholder values in each table nothing above touched, so every table of the schema is represented. */
    private fun fillEveryEmptyTable(file: File, homeId: String) {
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            val tables = connection.strings("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")
            tables.filter { connection.strings("SELECT count(*) FROM \"$it\"").single() == "0" }.forEach { table ->
                val columns = connection.rows("SELECT name, type FROM pragma_table_info('$table')")
                val values = columns.joinToString(",") { (name, type) ->
                    when {
                        name == "homeId" -> "'$homeId'"
                        type == "INTEGER" -> "1"
                        type == "BLOB" -> "x'0102'"
                        else -> "'fixture-$name'"
                    }
                }
                connection.execSQL("INSERT INTO \"$table\" VALUES ($values)")
            }
        } finally {
            connection.close()
        }
    }

    /** Folds the write-ahead log into the file so the fixture is one self-contained file. */
    private fun singleFile(file: File) {
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            connection.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            connection.execSQL("PRAGMA journal_mode = DELETE")
        } finally {
            connection.close()
        }
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
    }

    private fun SQLiteConnection.strings(sql: String): List<String> = prepare(sql).let { s ->
        try { buildList { while (s.step()) add(s.getText(0)) } } finally { s.close() }
    }

    private fun SQLiteConnection.rows(sql: String): List<Pair<String, String>> = prepare(sql).let { s ->
        try { buildList { while (s.step()) add(s.getText(0) to s.getText(1)) } } finally { s.close() }
    }
}
