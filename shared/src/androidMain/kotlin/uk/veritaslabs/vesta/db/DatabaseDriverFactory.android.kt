package uk.veritaslabs.vesta.db

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import java.io.File
import uk.veritaslabs.vesta.x0x.VestaAndroid

actual class DatabaseDriverFactory {
    private fun context() = requireNotNull(VestaAndroid.context) {
        "VestaAndroid.install(context) must be called before the database is opened"
    }

    actual fun databaseType(): AndroidxSqliteDatabaseType {
        val appContext = context().applicationContext
        return AndroidxSqliteDatabaseType.FileProvider {
            appContext.getDatabasePath("vesta.db").absolutePath
        }
    }

    // cr-sqlite only publishes a prebuilt Android binary for arm64-v8a (see
    // androidApp/src/main/jniLibs/arm64-v8a/libcrsqlite.so) — armeabi-v7a/x86_64
    // devices and emulators simply run without it until cr-sqlite ships more ABIs.
    actual fun crSqliteExtensionPath(): String? {
        val path = "${context().applicationInfo.nativeLibraryDir}/libcrsqlite.so"
        return path.takeIf { File(it).exists() }
    }
}
