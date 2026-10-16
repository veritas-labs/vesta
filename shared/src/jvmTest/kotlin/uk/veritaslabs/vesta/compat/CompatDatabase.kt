package uk.veritaslabs.vesta.compat

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import java.io.File
import uk.veritaslabs.kits.KitsInternalApi
import uk.veritaslabs.kits.store.DatabaseSafety
import uk.veritaslabs.kits.store.KitsDatabase

/**
 * Opens [file] as the app does: the same downgrade guard, backup and driver configuration as `createVestaDatabase`,
 * which cannot take a path on the desktop (its factory is fixed to `~/.vesta/vesta.db`).
 */
@OptIn(KitsInternalApi::class)
internal fun openAsTheAppDoes(file: File): Pair<KitsDatabase, AndroidxSqliteDriver> {
    DatabaseSafety.prepare(file.absolutePath, KitsDatabase.Schema.version)
    val driver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = AndroidxSqliteDatabaseType.File(file.absolutePath),
        schema = KitsDatabase.Schema,
        configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
    )
    return KitsDatabase(driver) to driver
}
