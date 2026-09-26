package uk.veritaslabs.vesta.db

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import java.io.File

// Desktop has no app-sandboxed storage dir, so it gets its own directory
// under the user's home, matching how other desktop apps keep local state.
private val appDir = File(System.getProperty("user.home"), ".vesta").apply { mkdirs() }

actual class DatabaseDriverFactory {
    actual fun databaseType(): AndroidxSqliteDatabaseType =
        AndroidxSqliteDatabaseType.File(File(appDir, "vesta.db").absolutePath)
}
