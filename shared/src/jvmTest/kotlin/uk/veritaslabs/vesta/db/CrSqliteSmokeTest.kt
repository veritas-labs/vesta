package uk.veritaslabs.vesta.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Guards the whole cr-sqlite wiring end to end: the extension is where it's
// expected to be, its entry point name is right, and it actually registers
// its SQL functions — not just that BundledSQLiteDriver.addExtension() didn't
// throw. crsql_site_id() returns a 16-byte UUID once cr-sqlite is loaded.
class CrSqliteSmokeTest {
    @Test
    fun crSqliteExtensionLoadsAndRegistersItsFunctions() {
        val extensionPath = requireNotNull(DatabaseDriverFactory().crSqliteExtensionPath()) {
            "expected a bundled cr-sqlite binary for this OS/arch"
        }
        val sqliteDriver = BundledSQLiteDriver()
        sqliteDriver.addExtension(extensionPath, "sqlite3_crsqlite_init")

        sqliteDriver.open(":memory:").use { connection ->
            connection.prepare("SELECT crsql_site_id()").use { statement ->
                assertTrue(statement.step())
                assertEquals(16, statement.getBlob(0).size)
            }
        }
    }
}
