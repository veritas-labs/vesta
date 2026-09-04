package uk.veritaslabs.vesta.db

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import java.io.File

// Desktop has no app-sandboxed storage dir, so it gets its own directory
// under the user's home, matching how other desktop apps keep local state.
private val appDir = File(System.getProperty("user.home"), ".vesta").apply { mkdirs() }

actual class DatabaseDriverFactory {
    actual fun databaseType(): AndroidxSqliteDatabaseType =
        AndroidxSqliteDatabaseType.File(File(appDir, "vesta.db").absolutePath)

    // Bundled per-OS/arch under shared/src/jvmMain/resources/crsqlite/ (vendored
    // from cr-sqlite's GitHub releases) — extracted once per launch since the
    // JVM can only dlopen a real file, not a classpath resource.
    actual fun crSqliteExtensionPath(): String? {
        val (resourceName, fileName) = crSqliteResourceForCurrentPlatform() ?: return null
        val resource = DatabaseDriverFactory::class.java.getResourceAsStream(
            "/crsqlite/$resourceName/$fileName",
        ) ?: return null
        val extracted = File(appDir, fileName)
        resource.use { input -> extracted.outputStream().use { input.copyTo(it) } }
        return extracted.absolutePath
    }
}

private fun crSqliteResourceForCurrentPlatform(): Pair<String, String>? {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val isArm64 = arch == "aarch64" || arch == "arm64"
    return when {
        os.contains("linux") && isArm64 -> "linux-aarch64" to "libcrsqlite.so"
        os.contains("linux") -> "linux-x86-64" to "libcrsqlite.so"
        os.contains("mac") && isArm64 -> "darwin-aarch64" to "libcrsqlite.dylib"
        os.contains("mac") -> "darwin-x86-64" to "libcrsqlite.dylib"
        os.contains("win") -> "windows-x86-64" to "crsqlite.dll"
        else -> null
    }
}
