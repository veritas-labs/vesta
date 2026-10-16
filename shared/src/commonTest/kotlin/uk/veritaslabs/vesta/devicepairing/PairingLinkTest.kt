package uk.veritaslabs.vesta.devicepairing

import uk.veritaslabs.kits.sync.PairingLink
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingLinkTest {

    @Test
    fun encodeThenDecodeRoundTrips() {
        // Arrange
        val link = PairingLink(agentId = "aa11bb22cc33dd44", expiresAt = 2_000L)

        // Act
        val decoded = PairingLink.decode(link.encode())

        // Assert
        assertEquals(link, decoded)
    }

    @Test
    fun encodeProducesVestaPairUri() {
        // Arrange
        val link = PairingLink(agentId = "aa11bb22cc33dd44", expiresAt = 2_000L)

        // Act
        val encoded = link.encode()

        // Assert
        assertTrue(encoded.startsWith("vesta://pair/"))
    }

    @Test
    fun decodeRejectsUnrecognisedScheme() {
        // Arrange
        val raw = "not-a-pairing-link"

        // Act
        val decoded = PairingLink.decode(raw)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun decodeRejectsUndecodableToken() {
        // Arrange
        val raw = "vesta://pair/not-valid-base64!!"

        // Act
        val decoded = PairingLink.decode(raw)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun decodeRejectsMissingPublicKey() {
        // Arrange
        val raw = "vesta://pair/${Base64.UrlSafe.encode(":2000".encodeToByteArray())}"

        // Act
        val decoded = PairingLink.decode(raw)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun decodeRejectsMissingExpiry() {
        // Arrange
        val raw = "vesta://pair/${Base64.UrlSafe.encode("aa11bb22cc33dd44".encodeToByteArray())}"

        // Act
        val decoded = PairingLink.decode(raw)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun decodeRejectsNonNumericExpiry() {
        // Arrange
        val raw = "vesta://pair/${Base64.UrlSafe.encode("aa11bb22cc33dd44:not-a-number".encodeToByteArray())}"

        // Act
        val decoded = PairingLink.decode(raw)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun isExpiredHonoursExpiryBoundary() {
        // Arrange
        val link = PairingLink(agentId = "aa11bb22cc33dd44", expiresAt = 2_000L)

        // Act
        val expiredAtBoundary = link.isExpired(2_000L)
        val stillValidBeforeBoundary = link.isExpired(1_999L)

        // Assert
        assertTrue(expiredAtBoundary)
        assertFalse(stillValidBeforeBoundary)
    }
}
