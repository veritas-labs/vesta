package uk.veritaslabs.kits.store

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import java.io.File


// Desktop has no app-sandboxed storage dir, so it gets its own directory
// under the user's home, matching how other desktop apps keep local state.
actual class DatabaseDriverFactory actual constructor(private val location: DatabaseLocation) {
    private val appDir = File(System.getProperty("user.home"), location.appDirName).apply { mkdirs() }

    actual fun databasePath(): String = File(appDir, location.fileName).absolutePath

    actual fun databaseType(): AndroidxSqliteDatabaseType = AndroidxSqliteDatabaseType.File(databasePath())
}
