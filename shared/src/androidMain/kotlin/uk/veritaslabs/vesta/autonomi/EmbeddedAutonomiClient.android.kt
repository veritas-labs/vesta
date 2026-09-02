package uk.veritaslabs.vesta.autonomi

import com.sun.jna.Library
import com.sun.jna.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.veritaslabs.vesta.x0x.VestaAndroid
import uniffi.ant_ffi.Client

/**
 * `ant-core::config::home_dir()` reads `$HOME` / `$USERPROFILE`, which
 * Android app processes don't have — every `connect*` call fails with
 * `ClientException.InitializationFailed` (`HomeDirNotFound`) without one.
 * Plants it once via libc `setenv`, same as ant-android's own demo app
 * (`AntFfiBootstrap.kt`) does for this SDK version.
 */
private interface LibC : Library {
    fun setenv(name: String, value: String, overwrite: Int): Int
    companion object {
        val INSTANCE: LibC = Native.load("c", LibC::class.java)
    }
}

private object AntFfiHome {
    @Volatile private var planted = false

    fun plantOnce(homeDir: String) {
        if (planted) return
        synchronized(this) {
            if (planted) return
            LibC.INSTANCE.setenv("HOME", homeDir, 1)
            planted = true
        }
    }
}

/**
 * Android client: an in-process `ant-ffi` [Client] connected directly to the
 * Autonomi production network via its vendored default bootstrap peers — no
 * daemon, no configuration. Read-only (no wallet attached), which is all a
 * connectivity indicator needs.
 */
private class EmbeddedAutonomiClient : AutonomiClient {

    private val mutex = Mutex()
    private var client: Client? = null

    override val endpoint: String
        get() = "embedded · mainnet (default bootstrap)"

    private suspend fun connectedClient(): Client? = mutex.withLock {
        client ?: run {
            val appDir = VestaAndroid.filesDir ?: return@withLock null
            AntFfiHome.plantOnce(appDir.absolutePath)
            withContext(Dispatchers.IO) {
                runCatching { Client.connectDefault() }.getOrNull()
            }
        }?.also { client = it }
    }

    override suspend fun connectionStatus(): AutonomiStatus? {
        connectedClient() ?: return null
        // The embedded client has no peer-count/version surface (unlike
        // x0x's /health) — reaching this point means the P2P node started
        // and joined the network.
        return AutonomiStatus(connected = true, network = "mainnet")
    }
}

actual fun createAutonomiClient(): AutonomiClient = EmbeddedAutonomiClient()
