package uk.veritaslabs.vesta.homejoin

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeJoinMessageTest {

    private fun payload(message: HomeJoinMessage) = Base64.Default.encode(HomeJoinMessage.encode(message))

    @Test
    fun anInviteSurvivesEncodingAndDecoding() {
        // Arrange
        val invite = HomeJoinMessage.Invite("home-1", "Flat 4B", "flatshare", 42L, "member-1", "Mark")

        // Act
        val decoded = HomeJoinMessage.decode(payload(invite))

        // Assert
        assertEquals(invite, decoded)
    }

    @Test
    fun anAcceptedMessageSurvivesEncodingAndDecoding() {
        // Arrange
        val accepted = HomeJoinMessage.Accepted("home-1")

        // Act
        val decoded = HomeJoinMessage.decode(payload(accepted))

        // Assert
        assertEquals(accepted, decoded)
    }

    @Test
    fun anAckMessageSurvivesEncodingAndDecoding() {
        // Arrange
        val ack = HomeJoinMessage.Ack("home-1", HomeJoinMessage.RemovedKind)

        // Act
        val decoded = HomeJoinMessage.decode(payload(ack))

        // Assert
        assertEquals(ack, decoded)
    }

    @Test
    fun aRequestMessageSurvivesEncodingAndDecoding() {
        // Arrange
        val request = HomeJoinMessage.Request("home-1", "s3cr3t", "member-2", "Sam", "agent-sam")

        // Act
        val decoded = HomeJoinMessage.decode(payload(request))

        // Assert
        assertEquals(request, decoded)
    }

    @Test
    fun aDeniedMessageSurvivesEncodingAndDecoding() {
        // Arrange
        val denied = HomeJoinMessage.Denied("home-1", HomeJoinMessage.DeniedExpired)

        // Act
        val decoded = HomeJoinMessage.decode(payload(denied))

        // Assert
        assertEquals(denied, decoded)
    }

    @Test
    fun otherMessagesOnTheStreamAreNotHomeJoinMessages() {
        // Arrange
        val deviceSyncHello = Base64.Default.encode("""{"kind":"devicesync.hello","name":"Laptop"}""".encodeToByteArray())

        // Act
        val decoded = HomeJoinMessage.decode(deviceSyncHello)

        // Assert
        assertNull(decoded)
    }
}
