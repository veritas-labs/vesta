package uk.veritaslabs.vesta.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

fun openInMemoryDatabase(): VestaDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    driver.execute(identifier = null, sql = "PRAGMA foreign_keys=ON", parameters = 0)
    VestaDatabase.Schema.create(driver)
    return VestaDatabase(driver)
}
