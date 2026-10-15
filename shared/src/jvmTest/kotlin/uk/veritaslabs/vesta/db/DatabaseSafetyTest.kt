package uk.veritaslabs.vesta.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.awaitAsList
import androidx.sqlite.execSQL
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DatabaseSafetyTest {

    private fun tempDir(): File = Files.createTempDirectory("vesta-db-safety").toFile().apply { deleteOnExit() }

    /** A small database file at [version] holding one row, in WAL mode like the app's, closed cleanly. */
    private fun databaseAt(dir: File, version: Long, name: String = "vesta.db"): File {
        val file = File(dir, name)
        BundledSQLiteDriver().open(file.path).use { connection ->
            connection.execSQL("PRAGMA journal_mode = WAL")
            connection.execSQL("CREATE TABLE Thing (name TEXT NOT NULL)")
            connection.execSQL("INSERT INTO Thing VALUES ('milk')")
            connection.execSQL("PRAGMA user_version = $version")
        }
        return file
    }

    private fun versionOf(file: File): Long =
        BundledSQLiteDriver().open(file.path).use { connection ->
            connection.prepare("PRAGMA user_version").use { it.step(); it.getLong(0) }
        }

    private fun backupsIn(dir: File): List<File> =
        dir.listFiles().orEmpty().filter { it.name.endsWith(".bak") }.sortedBy { it.name }

    @Test
    fun aDatabaseNewerThanTheSchemaIsRefusedAndTheFileIsLeftByteForByteAsItWas() {
        // Arrange
        val dir = tempDir()
        val file = databaseAt(dir, version = 25)
        val before = file.readBytes()
        val filesBefore = dir.list().orEmpty().sorted()

        // Act
        val error = assertFailsWith<DatabaseDowngradeException> { DatabaseSafety.prepare(file.path, schemaVersion = 20) }

        // Assert
        assertEquals(25, error.storedVersion)
        assertEquals(20, error.supportedVersion)
        assertContentEquals(before, file.readBytes())
        assertEquals(25, versionOf(file))
        assertTrue(backupsIn(dir).isEmpty())
        assertEquals(filesBefore.filterNot { it.endsWith("-wal") || it.endsWith("-shm") }, dir.list().orEmpty().sorted().filterNot { it.endsWith("-wal") || it.endsWith("-shm") })
    }

    /** What the driver does by itself with a newer file: it neither fails nor migrates, which is why the guard exists. */
    @Test
    fun withoutTheGuardTheDriverOpensANewerDatabaseSilentlyAndLeavesItsVersionAlone() = runBlocking {
        // Arrange
        val dir = tempDir()
        val file = File(dir, "vesta.db")
        fun driver() = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.File(file.path),
            schema = VestaDatabase.Schema,
            configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
        )
        driver().use { VestaDatabase(it).homeQueries.selectAll().awaitAsList() }
        BundledSQLiteDriver().open(file.path).use { it.execSQL("PRAGMA user_version = ${VestaDatabase.Schema.version + 1}") }

        // Act
        val homes = driver().use { VestaDatabase(it).homeQueries.selectAll().awaitAsList() }

        // Assert
        assertTrue(homes.isEmpty())
        assertEquals(VestaDatabase.Schema.version + 1, versionOf(file))
    }

    @Test
    fun anOlderDatabaseIsCopiedOnceBeforeItIsMigratedAndTheCopyHasTheOldVersionAndData() {
        // Arrange
        val dir = tempDir()
        val file = databaseAt(dir, version = 18)

        // Act
        DatabaseSafety.prepare(file.path, schemaVersion = 20, nowMillis = { 1000 })

        // Assert
        val backup = backupsIn(dir).single()
        assertEquals("vesta.db.pre-v18-1000.bak", backup.name)
        assertEquals(18, versionOf(backup))
        BundledSQLiteDriver().open(backup.path).use { connection ->
            connection.prepare("SELECT name FROM Thing").use { statement ->
                assertTrue(statement.step())
                assertEquals("milk", statement.getText(0))
            }
        }
        assertEquals(18, versionOf(file))
    }

    @Test
    fun openingADatabaseAlreadyAtTheSchemaVersionMakesNoCopy() {
        // Arrange
        val dir = tempDir()
        val file = databaseAt(dir, version = 20)

        // Act
        DatabaseSafety.prepare(file.path, schemaVersion = 20)
        DatabaseSafety.prepare(file.path, schemaVersion = 20)

        // Assert
        assertTrue(backupsIn(dir).isEmpty())
    }

    @Test
    fun aMissingOrEmptyDatabaseIsNotCopied() {
        // Arrange
        val dir = tempDir()
        val missing = File(dir, "vesta.db")
        val fresh = databaseAt(dir, version = 0, name = "fresh.db")

        // Act
        DatabaseSafety.prepare(missing.path, schemaVersion = 20)
        DatabaseSafety.prepare(fresh.path, schemaVersion = 20)

        // Assert
        assertTrue(backupsIn(dir).isEmpty())
        assertTrue(!missing.exists())
    }

    @Test
    fun onlyTheNewestTwoCopiesAreKeptAndOtherFilesAreLeftAlone() {
        // Arrange
        val dir = tempDir()
        val file = databaseAt(dir, version = 10)
        val unrelated = File(dir, "vesta.db.pre-v9-5.txt").apply { writeText("keep") }
        var clock = 0L

        // Act
        listOf(10L, 12L, 15L, 18L).forEach { version ->
            BundledSQLiteDriver().open(file.path).use { it.execSQL("PRAGMA user_version = $version") }
            clock += 1000
            DatabaseSafety.prepare(file.path, schemaVersion = 20, nowMillis = { clock })
        }

        // Assert
        assertEquals(listOf("vesta.db.pre-v15-3000.bak", "vesta.db.pre-v18-4000.bak"), backupsIn(dir).map { it.name })
        assertTrue(unrelated.exists())
    }

    @Test
    fun retentionOrdersByTimestampNotByTextSoTenDigitsBeatNine() {
        // Arrange
        val dir = tempDir()
        val file = databaseAt(dir, version = 10)
        File(dir, "vesta.db.pre-v9-999.bak").writeText("old")
        File(dir, "vesta.db.pre-v9-1000.bak").writeText("old")

        // Act
        DatabaseSafety.prepare(file.path, schemaVersion = 20, nowMillis = { 2000 })

        // Assert
        assertEquals(listOf("vesta.db.pre-v10-2000.bak", "vesta.db.pre-v9-1000.bak"), backupsIn(dir).map { it.name })
    }
}
