package uk.veritaslabs.vesta.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import uk.veritaslabs.vesta.x0x.VestaAndroid

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val context = requireNotNull(VestaAndroid.context) {
            "VestaAndroid.install(context) must be called before the database is opened"
        }
        return AndroidSqliteDriver(VestaDatabase.Schema, context, "vesta.db")
    }
}
