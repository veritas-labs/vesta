package uk.veritaslabs.vesta.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

/** Resolves what's platform-specific about opening [VestaDatabase]: where the file lives — see
 * the per-platform actuals. */
expect class DatabaseDriverFactory() {
    fun databaseType(): AndroidxSqliteDatabaseType
}

/** Opens the local [VestaDatabase] on the same bundled SQLite build across every platform,
 * creating its schema on first run. */
fun createVestaDatabase(driverFactory: DatabaseDriverFactory): VestaDatabase {
    val driver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = driverFactory.databaseType(),
        schema = VestaDatabase.Schema,
        configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
    )
    return VestaDatabase(driver)
}
