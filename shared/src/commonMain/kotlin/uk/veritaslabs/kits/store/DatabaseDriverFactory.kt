package uk.veritaslabs.kits.store

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

/** Resolves what's platform-specific about opening [KitsDatabase]: where the file lives — see
 * the per-platform actuals. */
expect class DatabaseDriverFactory(location: DatabaseLocation) {
    /** The database file, for the checks that run before the driver opens it. */
    fun databasePath(): String

    fun databaseType(): AndroidxSqliteDatabaseType
}

/** Opens the local [KitsDatabase] on the same bundled SQLite build across every platform,
 * creating its schema on first run.
 * @throws DatabaseDowngradeException if the file was written by a newer version; it is left untouched.
 * An older file gets a safety copy beside it before it is migrated (see [DatabaseSafety]). */
fun createVestaDatabase(driverFactory: DatabaseDriverFactory): KitsDatabase {
    DatabaseSafety.prepare(driverFactory.databasePath(), KitsDatabase.Schema.version)
    val driver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = driverFactory.databaseType(),
        schema = KitsDatabase.Schema,
        configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
    )
    return KitsDatabase(driver)
}
