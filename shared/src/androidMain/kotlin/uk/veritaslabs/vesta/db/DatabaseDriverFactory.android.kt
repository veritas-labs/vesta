package uk.veritaslabs.vesta.db

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import android.content.Context

actual class DatabaseDriverFactory {
    private fun context() = requireNotNull(DatabaseAndroid.context) {
        "VestaAndroid.install(context) must be called before the database is opened"
    }

    actual fun databasePath(): String = context().applicationContext.getDatabasePath("vesta.db").absolutePath

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
