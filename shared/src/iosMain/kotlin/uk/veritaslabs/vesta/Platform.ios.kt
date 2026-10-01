package uk.veritaslabs.vesta

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getAppInfo(): AppInfo {
    val info = NSBundle.mainBundle.infoDictionary
    return AppInfo(
        version = info?.get("CFBundleShortVersionString") as? String ?: "?",
        buildCode = info?.get("CFBundleVersion") as? String ?: "?",
    )
}
