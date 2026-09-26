package uk.veritaslabs.vesta.db

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

// Stores under the app's sandboxed NSDocumentDirectory — private storage by
// construction, no extra configuration needed.
actual class DatabaseDriverFactory {
    actual fun databaseType(): AndroidxSqliteDatabaseType {
        val documentDirectory = NSSearchPathForDirectoriesInDomains(
            directory = NSDocumentDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).first() as String
        return AndroidxSqliteDatabaseType.File("$documentDirectory/vesta.db")
    }
}
