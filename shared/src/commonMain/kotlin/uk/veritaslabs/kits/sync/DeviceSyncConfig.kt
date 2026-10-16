package uk.veritaslabs.kits.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * What an app chooses about pairing its devices: the pairing link scheme, the x0x group the paired devices share, and how
 * long the invite sent to a pairing device stays valid. The defaults are the values Vesta has always used.
 */
data class DeviceSyncConfig(
    /** Where a pairing link starts (`<scheme>/<token>`). */
    val pairingScheme: String = DefaultPairingScheme,
    /** The name given to the x0x group the user's devices share. Groups are never looked up by it. */
    val groupName: String = "DeviceSync",
    /** How long the x0x group invite sent to a pairing device stays valid. */
    val pairingInviteLifetime: Duration = 15.minutes,
) {
    companion object {
        const val DefaultPairingScheme = "vesta://pair"
    }
}
