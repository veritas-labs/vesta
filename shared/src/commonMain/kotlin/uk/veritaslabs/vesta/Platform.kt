package uk.veritaslabs.vesta

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** This build of the app: its [version] name (e.g. "1.0") and [buildCode], the monotonically increasing build number. */
data class AppInfo(val version: String, val buildCode: String)

expect fun getAppInfo(): AppInfo
