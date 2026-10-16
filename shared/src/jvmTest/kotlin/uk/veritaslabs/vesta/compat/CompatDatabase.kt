package uk.veritaslabs.vesta.compat

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import java.io.File
import uk.veritaslabs.vesta.db.DatabaseSafety
import uk.veritaslabs.vesta.db.VestaDatabase

/**
 * Opens [file] as the app does: the same downgrade guard, backup and driver configuration as `createVestaDatabase`,
 * which cannot take a path on the desktop (its factory is fixed to `~/.vesta/vesta.db`).
 */
internal fun openAsTheAppDoes(file: File): Pair<VestaDatabase, AndroidxSqliteDriver> {
    DatabaseSafety.prepare(file.absolutePath, VestaDatabase.Schema.version)
    val driver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = AndroidxSqliteDatabaseType.File(file.absolutePath),
        schema = VestaDatabase.Schema,
        configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
    )
    return VestaDatabase(driver) to driver
}
