package uk.veritaslabs.vesta.db

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
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

    // crsqlite.xcframework must be embedded into the iosApp target in Xcode (see
    // README's "x0x networking" section for the equivalent manual step for
    // libx0x_ffi.a) — this resolves the binary Xcode embeds at that path.
    actual fun crSqliteExtensionPath(): String? {
        val path = NSBundle.mainBundle.pathForResource(
            name = "Frameworks/crsqlite.framework/crsqlite",
            ofType = null,
        ) ?: return null
        return path.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
    }
}
