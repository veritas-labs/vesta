package uk.veritaslabs.vesta.db

import app.cash.sqldelight.db.SqlDriver

/** Creates the platform [SqlDriver] backing [VestaDatabase] — see the per-platform actuals. */
expect class DatabaseDriverFactory() {
    fun createDriver(): SqlDriver
}

/** Opens the local [VestaDatabase], creating its schema on first run. */
fun createVestaDatabase(driverFactory: DatabaseDriverFactory): VestaDatabase {
    val driver = driverFactory.createDriver()
    // SQLite disables foreign-key enforcement per connection by default.
    driver.execute(identifier = null, sql = "PRAGMA foreign_keys=ON", parameters = 0)
    return VestaDatabase(driver)
}
