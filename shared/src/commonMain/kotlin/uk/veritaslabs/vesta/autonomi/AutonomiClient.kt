package uk.veritaslabs.vesta.autonomi

/**
 * Client for the Autonomi decentralized network. One implementation per
 * platform:
 *
 * * Android — an embedded, in-process `ant-ffi` client (UniFFI bindings)
 *   connected directly to the network, no daemon;
 * * Desktop (JVM) — talks to a standalone `antd` REST daemon (see
 *   [WithAutonomi/ant-sdk](https://github.com/WithAutonomi/ant-sdk));
 * * iOS — not yet supported (Autonomi's iOS SDK is Swift-native, with no C
 *   ABI to cinterop against the way x0x's Rust FFI crate is).
 */
interface AutonomiClient {

    /** What this client talks to, for display, e.g. `antd · 127.0.0.1:8082`. */
    val endpoint: String

    /** Current connection status, or null when unreachable/not yet connected. */
    suspend fun connectionStatus(): AutonomiStatus?
}

expect fun createAutonomiClient(): AutonomiClient
