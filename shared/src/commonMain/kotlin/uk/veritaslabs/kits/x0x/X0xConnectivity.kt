package uk.veritaslabs.kits.x0x

/** Whether this device currently has a working link to the x0x network. */
sealed interface X0xConnectivity {
    data object Connecting : X0xConnectivity
    data class Connected(val peers: Int) : X0xConnectivity
    data object Offline : X0xConnectivity
}

/**
 * Whether this device is reaching the x0x network, as far as anyone can send it a message: not when the daemon is
 * offline or has no peers. Still connecting counts as reaching it, so nothing flashes a warning at startup.
 */
val X0xConnectivity.isReachingNetwork: Boolean
    get() = when (this) {
        X0xConnectivity.Connecting -> true
        X0xConnectivity.Offline -> false
        is X0xConnectivity.Connected -> peers > 0
    }
