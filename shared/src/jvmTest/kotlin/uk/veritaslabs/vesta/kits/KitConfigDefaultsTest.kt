package uk.veritaslabs.vesta.kits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import uk.veritaslabs.vesta.InviteLifetime
import uk.veritaslabs.vesta.VestaKitConfig
import uk.veritaslabs.kits.store.DatabaseDowngradeException
import uk.veritaslabs.kits.sync.PairingLink
import uk.veritaslabs.kits.spaces.InviteLink

/** The values existing installs were made with: Vesta's choices in [VestaKitConfig] must reproduce them exactly (the kits default none of them). */
class KitConfigDefaultsTest {

    @Test
    fun vestasHomeConfigIsTheValuesVestaHasAlwaysUsed() {
        // Arrange
        val config = VestaKitConfig.home

        // Act
        val values = listOf(
            config.inviteScheme, config.groupName, config.mailboxStoreName,
            config.inviteLinkLifetime, config.joinInviteLifetime, config.hubEnrolmentLifetime, config.hubCommandsRefresh,
        )

        // Assert
        assertEquals(listOf("vesta://invite", "Vesta Home", "mailbox", 7.days, 1.hours, 1.hours, 30.minutes), values)
    }

    @Test
    fun vestasDeviceSyncConfigIsTheValuesVestaHasAlwaysUsed() {
        // Arrange
        val config = VestaKitConfig.deviceSync

        // Act
        val values = listOf(config.pairingScheme, config.groupName, config.pairingInviteLifetime)

        // Assert
        assertEquals(listOf("vesta://pair", "DeviceSync", 15.minutes), values)
    }

    @Test
    fun vestasNameAndUserAgentAreWhatTheKitsPrintAndSend() {
        // Arrange / Act
        val values = listOf(VestaKitConfig.appName, VestaKitConfig.userAgent)

        // Assert
        assertEquals(listOf("Vesta", "Vesta"), values)
    }

    @Test
    fun vestasChoicesMatchTheInviteLifetimes() {
        // Arrange
        val lifetimes = listOf(InviteLifetime.HomeJoin, InviteLifetime.HubEnrolment, InviteLifetime.HubCommandsRefresh, InviteLifetime.Pairing)

        // Act
        val chosen = listOf(
            VestaKitConfig.home.joinInviteLifetime, VestaKitConfig.home.hubEnrolmentLifetime,
            VestaKitConfig.home.hubCommandsRefresh, VestaKitConfig.deviceSync.pairingInviteLifetime,
        )

        // Assert
        assertEquals(lifetimes, chosen)
    }

    @Test
    fun anInviteLinkIsEncodedExactlyAsItAlwaysWas() {
        // Arrange
        val link = InviteLink("home-1", "sec", 100L, listOf("agent-a", "agent-b"), "Flat: 4B")

        // Act
        val encoded = link.encode(VestaKitConfig.home.inviteScheme)

        // Assert
        assertEquals("vesta://invite/aG9tZS0xOnNlYzoxMDA6YWdlbnQtYSxhZ2VudC1iOkZsYXQ6IDRC", encoded)
        assertEquals(link, InviteLink.decode(encoded, VestaKitConfig.home.inviteScheme))
    }

    @Test
    fun aPairingLinkIsEncodedExactlyAsItAlwaysWas() {
        // Arrange
        val link = PairingLink("agent-x", 200L, "Mark")

        // Act
        val encoded = link.encode(VestaKitConfig.deviceSync.pairingScheme)

        // Assert
        assertEquals("vesta://pair/YWdlbnQteDoyMDA6TWFyaw==", encoded)
        assertEquals(link, PairingLink.decode(encoded, VestaKitConfig.deviceSync.pairingScheme))
    }

    @Test
    fun aLinkWithAnotherSchemeIsNotRecognised() {
        // Arrange
        val encoded = InviteLink("home-1", "sec", 100L, listOf("agent-a"), "Flat").encode("other://invite")

        // Act
        val decoded = InviteLink.decode(encoded, VestaKitConfig.home.inviteScheme)

        // Assert
        assertNull(decoded)
        assertNotNull(InviteLink.decode(encoded, "other://invite"))
    }

    @Test
    fun theDowngradeMessageNamesTheAppItIsGiven() {
        // Arrange
        val vesta = DatabaseDowngradeException(storedVersion = 21, supportedVersion = 20, appName = VestaKitConfig.appName)
        val other = DatabaseDowngradeException(storedVersion = 21, supportedVersion = 20, appName = "Sample")

        // Act
        val messages = vesta.message to other.message

        // Assert
        assertEquals(
            "The Vesta database is at version 21 but this app only understands up to version 20. Update Vesta; the data has not been changed.",
            messages.first,
        )
        assertEquals(
            "The Sample database is at version 21 but this app only understands up to version 20. Update Sample; the data has not been changed.",
            messages.second,
        )
    }
}
