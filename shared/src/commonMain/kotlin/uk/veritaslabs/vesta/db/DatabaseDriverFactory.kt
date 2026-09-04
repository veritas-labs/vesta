package uk.veritaslabs.vesta.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

/** cr-sqlite's exported init symbol — see https://vlcn.io/docs/cr-sqlite/installation. */
private const val CRSQLITE_ENTRY_POINT = "sqlite3_crsqlite_init"

/**
 * Resolves what's platform-specific about opening [VestaDatabase]: where the file lives, and
 * where the bundled cr-sqlite extension binary is for this platform/ABI (if any) — see the
 * per-platform actuals.
 */
expect class DatabaseDriverFactory() {
    fun databaseType(): AndroidxSqliteDatabaseType

    /**
     * Absolute path to the bundled cr-sqlite loadable extension for this platform/ABI, or null
     * if none is packaged (e.g. an Android ABI cr-sqlite doesn't publish prebuilt binaries for).
     */
    fun crSqliteExtensionPath(): String?
}

/** Opens the local [VestaDatabase] on the same bundled SQLite build across every platform,
 * creating its schema on first run and loading cr-sqlite if it's available. */
fun createVestaDatabase(driverFactory: DatabaseDriverFactory): VestaDatabase {
    val sqliteDriver = BundledSQLiteDriver()
    driverFactory.crSqliteExtensionPath()?.let { path ->
        sqliteDriver.addExtension(path, CRSQLITE_ENTRY_POINT)
    }
    val driver = AndroidxSqliteDriver(
        driver = sqliteDriver,
        databaseType = driverFactory.databaseType(),
        schema = VestaDatabase.Schema,
        configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
    )
    return VestaDatabase(driver)
}
