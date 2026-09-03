package uk.veritaslabs.vesta.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

// Desktop has no app-sandboxed storage dir, so it gets its own directory
// under the user's home, matching how other desktop apps keep local state.
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val appDir = File(System.getProperty("user.home"), ".vesta").apply { mkdirs() }
        val dbFile = File(appDir, "vesta.db")
        val isNewDatabase = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (isNewDatabase) {
            VestaDatabase.Schema.create(driver)
        }
        return driver
    }
}
