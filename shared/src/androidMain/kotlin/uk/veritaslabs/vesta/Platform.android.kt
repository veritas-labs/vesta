package uk.veritaslabs.vesta

import android.os.Build
import uk.veritaslabs.vesta.x0x.VestaAndroid

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
