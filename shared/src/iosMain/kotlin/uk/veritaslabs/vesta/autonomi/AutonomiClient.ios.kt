package uk.veritaslabs.vesta.autonomi

/**
 * Not yet supported on iOS. Unlike x0x — fronted by a Rust FFI crate this
 * project owns (`ffi/x0x-ffi`) and can cinterop against directly — Autonomi's
 * iOS bindings (`ant-swift`) are Swift-native with no C ABI, so there's
 * nothing for Kotlin/Native cinterop to bind against. Supporting iOS would
 * need either a Rust FFI shim of our own (mirroring `ffi/x0x-ffi`) or wiring
 * `ant-swift` entirely on the Xcode side, outside this shared module.
 */
private class UnsupportedAutonomiClient : AutonomiClient {
    override val endpoint: String = "not yet supported on iOS"
    override suspend fun connectionStatus(): AutonomiStatus? = null
}

actual fun createAutonomiClient(): AutonomiClient = UnsupportedAutonomiClient()
