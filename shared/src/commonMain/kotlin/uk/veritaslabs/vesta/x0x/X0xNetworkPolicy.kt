package uk.veritaslabs.vesta.x0x

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Which networks the embedded x0x daemon may use. Set from the Network settings and read by the
 * platform clients that own a daemon; a daemon that is not allowed on the current network is stopped
 * and reports offline until it is.
 */
object X0xNetworkPolicy {
    /** Whether x0x may run over a mobile data connection. Off by default. */
    val useMobileData = MutableStateFlow(false)
}
