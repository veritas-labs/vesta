package uk.veritaslabs.vesta.homejoin

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InviteLinkTest {

    private fun link(agents: List<String> = listOf("agent-a", "agent-b")) =
        InviteLink(homeId = "home-1", secret = "s3cr3t", expiresAt = 1_000L, adminAgentIds = agents, homeName = "Flat 4B: top floor")

    @Test
    fun aLinkSurvivesEncodingAndDecoding() {
        // Arrange
        val link = link()

        // Act
        val decoded = InviteLink.decode(link.encode())

        // Assert
        assertEquals(link, decoded)
    }

    @Test
    fun surroundingWhitespaceFromPastingIsIgnored() {
        // Arrange
        val link = link()

        // Act
        val decoded = InviteLink.decode("  ${link.encode()}\n")

        // Assert
        assertEquals(link, decoded)
    }

    @Test
    fun aLinkOfAnotherKindIsNotAnInviteLink() {
        // Arrange
        val pairingLink = "vesta://pair/YWJjOjEwMA"

        // Act
        val decoded = InviteLink.decode(pairingLink)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun garbageIsNotALink() {
        // Arrange
        val raw = "vesta://invite/not-base64-!!"

        // Act
        val decoded = InviteLink.decode(raw)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun aLinkWithNoAdminAgentsIsRejected() {
        // Arrange
        val raw = "vesta://invite/" + Base64.UrlSafe.encode("home-1:s3cr3t:1000::Flat".encodeToByteArray())

        // Act
        val decoded = InviteLink.decode(raw)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun aLinkWithTooManyAdminAgentsIsRejected() {
        // Arrange
        val agents = (1..InviteLink.MaxAdminAgents + 1).map { "agent-$it" }

        // Act
        val decoded = InviteLink.decode(link(agents).encode())

        // Assert
        assertNull(decoded)
    }

    @Test
    fun aLinkWithABlankSecretIsRejected() {
        // Arrange
        val raw = "vesta://invite/" + Base64.UrlSafe.encode("home-1::1000:agent-a:Flat".encodeToByteArray())

        // Act
        val decoded = InviteLink.decode(raw)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun aLinkExpiresAtItsExpiryTime() {
        // Arrange
        val link = link()

        // Act
        val before = link.isExpired(999L)
        val at = link.isExpired(1_000L)

        // Assert
        assertFalse(before)
        assertTrue(at)
    }
}
