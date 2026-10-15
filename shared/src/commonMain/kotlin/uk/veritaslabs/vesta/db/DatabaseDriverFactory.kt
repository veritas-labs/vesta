package uk.veritaslabs.vesta.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

/** Resolves what's platform-specific about opening [VestaDatabase]: where the file lives — see
 * the per-platform actuals. */
expect class DatabaseDriverFactory() {
    /** The database file, for the checks that run before the driver opens it. */
    fun databasePath(): String

    fun databaseType(): AndroidxSqliteDatabaseType
}

/** Opens the local [VestaDatabase] on the same bundled SQLite build across every platform,
 * creating its schema on first run.
 * @throws DatabaseDowngradeException if the file was written by a newer version; it is left untouched.
 * An older file gets a safety copy beside it before it is migrated (see [DatabaseSafety]). */
fun createVestaDatabase(driverFactory: DatabaseDriverFactory): VestaDatabase {
    DatabaseSafety.prepare(driverFactory.databasePath(), VestaDatabase.Schema.version)
    val driver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = driverFactory.databaseType(),
        schema = VestaDatabase.Schema,
        configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
    )
    return VestaDatabase(driver)
}
