package uk.veritaslabs.vesta.autonomi

/**
 * Not embedded on Android: the `ant-android` bindings added about 38 MB of
 * native code per ABI and nothing used them yet (see
 * docs/plans/android-app-size-reduction.md, WP4). Re-add the dependency and an
 * in-process `ant-ffi` client here when Android needs Autonomi.
 */
private class UnsupportedAutonomiClient : AutonomiClient {
    override val endpoint: String = "not embedded on Android"
    override suspend fun connectionStatus(): AutonomiStatus? = null
}

actual fun createAutonomiClient(): AutonomiClient = UnsupportedAutonomiClient()
