package uk.veritaslabs.vesta.homejoin

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

import uk.veritaslabs.kits.spaces.SpaceJoinMessage
class HomeJoinMessageTest {

    private fun payload(message: SpaceJoinMessage) = Base64.Default.encode(SpaceJoinMessage.encode(message))

    @Test
    fun anInviteSurvivesEncodingAndDecoding() {
        // Arrange
        val invite = SpaceJoinMessage.Invite("home-1", "Flat 4B", "flatshare", 42L, "member-1", "Mark")

        // Act
        val decoded = SpaceJoinMessage.decode(payload(invite))

        // Assert
        assertEquals(invite, decoded)
    }

    @Test
    fun anAcceptedMessageSurvivesEncodingAndDecoding() {
        // Arrange
        val accepted = SpaceJoinMessage.Accepted("home-1")

        // Act
        val decoded = SpaceJoinMessage.decode(payload(accepted))

        // Assert
        assertEquals(accepted, decoded)
    }

    @Test
    fun anAckMessageSurvivesEncodingAndDecoding() {
        // Arrange
        val ack = SpaceJoinMessage.Ack("home-1", SpaceJoinMessage.RemovedKind)

        // Act
        val decoded = SpaceJoinMessage.decode(payload(ack))

        // Assert
        assertEquals(ack, decoded)
    }

    @Test
    fun aRequestMessageSurvivesEncodingAndDecoding() {
        // Arrange
        val request = SpaceJoinMessage.Request("home-1", "s3cr3t", "member-2", "Sam", "agent-sam")

        // Act
        val decoded = SpaceJoinMessage.decode(payload(request))

        // Assert
        assertEquals(request, decoded)
    }

    @Test
    fun aDeniedMessageSurvivesEncodingAndDecoding() {
        // Arrange
        val denied = SpaceJoinMessage.Denied("home-1", SpaceJoinMessage.DeniedExpired)

        // Act
        val decoded = SpaceJoinMessage.decode(payload(denied))

        // Assert
        assertEquals(denied, decoded)
    }

    @Test
    fun otherMessagesOnTheStreamAreNotHomeJoinMessages() {
        // Arrange
        val deviceSyncHello = Base64.Default.encode("""{"kind":"devicesync.hello","name":"Laptop"}""".encodeToByteArray())

        // Act
        val decoded = SpaceJoinMessage.decode(deviceSyncHello)

        // Assert
        assertNull(decoded)
    }
}
