package uk.veritaslabs.vesta

import android.content.Context
import android.os.Build
import uk.veritaslabs.vesta.db.DatabaseAndroid
import uk.veritaslabs.vesta.x0x.X0xAndroid

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

@Suppress("DEPRECATION")
actual fun getAppInfo(): AppInfo {
    val context = VestaAndroid.context ?: return AppInfo("?", "?")
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        ?: return AppInfo("?", "?")
    return AppInfo(info.versionName ?: "?", info.versionCode.toString())
}

actual fun getDeviceName(): String =
    listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android device" }

/**
 * App-level init installed by `MainActivity` before the UI is created, so shared code can reach app-private
 * storage: the local SQLite database and the embedded x0x client each keep their own copy ([DatabaseAndroid],
 * [X0xAndroid]) so that neither depends on the app, and [getAppInfo] uses the context too.
 */
object VestaAndroid {
    val context: Context? get() = DatabaseAndroid.context

    fun install(context: Context) {
        DatabaseAndroid.install(context)
        X0xAndroid.install(context)
    }
}
