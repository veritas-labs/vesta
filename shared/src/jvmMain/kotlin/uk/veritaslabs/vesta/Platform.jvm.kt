package uk.veritaslabs.vesta

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

/** Set by the desktop app's build (`jvmArgs`); a packaged app also carries `jpackage.app-version`. */
actual fun getAppInfo(): AppInfo = AppInfo(
    version = System.getProperty("vesta.version") ?: System.getProperty("jpackage.app-version") ?: "dev",
    buildCode = System.getProperty("vesta.build") ?: "dev",
)
