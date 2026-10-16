package uk.veritaslabs.kits.store

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import android.content.Context

actual class DatabaseDriverFactory actual constructor(private val location: DatabaseLocation) {
    private fun context() = requireNotNull(DatabaseAndroid.context) {
        "VestaAndroid.install(context) must be called before the database is opened"
    }

    actual fun databasePath(): String = context().applicationContext.getDatabasePath(location.fileName).absolutePath

    actual fun databaseType(): AndroidxSqliteDatabaseType = AndroidxSqliteDatabaseType.FileProvider { databasePath() }
}

/** The Android context the database path comes from; the app installs it (`VestaAndroid.install`) before the database is opened. */
object DatabaseAndroid {
    @Volatile
    var context: Context? = null
        private set

    fun install(context: Context) {
        this.context = context.applicationContext
    }
}
