package uk.veritaslabs.vesta.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

// Stores under the app's sandboxed NSDocumentDirectory — private storage by
// construction, no extra configuration needed.
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(VestaDatabase.Schema, "vesta.db")
}
