package uk.veritaslabs.vesta.homejoin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinLinkTest {

    @Test
    fun aLinkSurvivesEncodingAndDecoding() {
        // Arrange
        val link = JoinLink(agentId = "abc123", memberId = "member-1", expiresAt = 1_000L, name = "Sam: the tablet")

        // Act
        val decoded = JoinLink.decode(link.encode())

        // Assert
        assertEquals(link, decoded)
    }

    @Test
    fun aPairingLinkIsNotAJoinLink() {
        // Arrange
        val pairingLink = "vesta://pair/YWJjOjEwMA"

        // Act
        val decoded = JoinLink.decode(pairingLink)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun garbageIsNotALink() {
        // Arrange
        val raw = "vesta://join/not-base64-!!"

        // Act
        val decoded = JoinLink.decode(raw)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun surroundingWhitespaceFromPastingIsIgnored() {
        // Arrange
        val link = JoinLink("abc", "m", 5L, "Sam")

        // Act
        val decoded = JoinLink.decode("  ${link.encode()}\n")

        // Assert
        assertEquals(link, decoded)
    }

    @Test
    fun aLinkExpiresAtItsExpiryTime() {
        // Arrange
        val link = JoinLink("abc", "m", expiresAt = 100L, name = "Sam")

        // Act
        val before = link.isExpired(99L)
        val at = link.isExpired(100L)

        // Assert
        assertFalse(before)
        assertTrue(at)
    }
}
