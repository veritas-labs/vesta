package uk.veritaslabs.kits.store

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

fun openInMemoryDatabase(): KitsDatabase {
    val driver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = AndroidxSqliteDatabaseType.Memory,
        schema = KitsDatabase.Schema,
        configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
    )
    return KitsDatabase(driver)
}
