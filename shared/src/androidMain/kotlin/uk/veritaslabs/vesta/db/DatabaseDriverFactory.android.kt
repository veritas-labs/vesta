package uk.veritaslabs.vesta.db

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import uk.veritaslabs.vesta.x0x.VestaAndroid

actual class DatabaseDriverFactory {
    private fun context() = requireNotNull(VestaAndroid.context) {
        "VestaAndroid.install(context) must be called before the database is opened"
    }

    actual fun databasePath(): String = context().applicationContext.getDatabasePath("vesta.db").absolutePath

    actual fun databaseType(): AndroidxSqliteDatabaseType = AndroidxSqliteDatabaseType.FileProvider { databasePath() }
}
