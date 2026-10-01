package uk.veritaslabs.vesta

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** This build of the app: its [version] name (e.g. "1.0") and [buildCode], the monotonically increasing build number. */
data class AppInfo(val version: String, val buildCode: String)

expect fun getAppInfo(): AppInfo

/** A human-friendly name for this device, e.g. "Pixel 9" or the computer's host name; shown to the user's other devices. */
expect fun getDeviceName(): String
