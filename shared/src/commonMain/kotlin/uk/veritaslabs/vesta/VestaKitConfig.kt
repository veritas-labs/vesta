package uk.veritaslabs.vesta

import uk.veritaslabs.kits.sync.DeviceSyncConfig
import uk.veritaslabs.kits.spaces.SpacesConfig

/**
 * Everything Vesta chooses for the sharing and pairing code, in one place. The schemes, group names and the mailbox store
 * name are what installs already in use were made with, so they do not change.
 */
object VestaKitConfig {
    /** The app's name as the kits print it (the downgrade message: "The Vesta database is at version ..."). */
    const val appName = "Vesta"

    /** The User-Agent the kits' photo fetcher sends when the user adds a photo from a link. */
    const val userAgent = "Vesta"

    val home = SpacesConfig(
        inviteScheme = "vesta://invite",
        groupName = "Vesta Home",
        joinInviteLifetime = InviteLifetime.HomeJoin,
        hubEnrolmentLifetime = InviteLifetime.HubEnrolment,
        hubCommandsRefresh = InviteLifetime.HubCommandsRefresh,
        mailboxStoreName = "mailbox",
    )

    val deviceSync = DeviceSyncConfig(
        pairingScheme = "vesta://pair",
        groupName = "DeviceSync",
        pairingInviteLifetime = InviteLifetime.Pairing,
    )
}
