package uk.veritaslabs.vesta.compat

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import uk.veritaslabs.kits.blobs.BlobMessage
import uk.veritaslabs.kits.docs.SyncEnvelope
import uk.veritaslabs.vesta.devicepairing.PairingLink
import uk.veritaslabs.vesta.devicesync.DeviceSyncMessage
import uk.veritaslabs.kits.spaces.SpaceGroupMessage
import uk.veritaslabs.kits.spaces.SpaceJoinMessage
import uk.veritaslabs.kits.spaces.InviteLink

/**
 * The exact text on the wire and in stored rows, captured from the encoders as they were before the Kits extraction
 * (docs/plans/kits-extraction-implementation-plan.md, WP10). The literals are never regenerated: a rename that changes
 * one of them has broken every install and every peer still on the old version. Kotlin names may change; these may not.
 */
@OptIn(uk.veritaslabs.kits.KitsInternalApi::class)
class GoldenWireStringsTest {

    private fun base64(text: String) = Base64.Default.encode(text.encodeToByteArray())

    private class Case<T>(val message: T, val json: String)

    private val homeJoin = listOf(
        Case<SpaceJoinMessage>(
            SpaceJoinMessage.Invite("home-1", "The Johnsons", "family", 1700000000000L, "member-1", "Mark"),
            """{"kind":"homejoin.invite","homeId":"home-1","homeName":"The Johnsons","shape":"family","createdAt":1700000000000,"inviterMemberId":"member-1","inviterName":"Mark"}""",
        ),
        Case(SpaceJoinMessage.Accepted("home-1"), """{"kind":"homejoin.accepted","homeId":"home-1"}"""),
        Case(SpaceJoinMessage.Left("home-1", "member-2"), """{"kind":"homejoin.left","homeId":"home-1","memberId":"member-2"}"""),
        Case(SpaceJoinMessage.Removed("home-1"), """{"kind":"homejoin.removed","homeId":"home-1"}"""),
        Case(SpaceJoinMessage.Ack("home-1", "homejoin.invite"), """{"kind":"homejoin.ack","homeId":"home-1","of":"homejoin.invite"}"""),
        Case(
            SpaceJoinMessage.Request("home-1", "s3cret", "member-2", "Sam", "agent-2"),
            """{"kind":"homejoin.request","homeId":"home-1","secret":"s3cret","memberId":"member-2","name":"Sam","agentId":"agent-2"}""",
        ),
        Case(SpaceJoinMessage.Denied("home-1", "expired"), """{"kind":"homejoin.denied","homeId":"home-1","reason":"expired"}"""),
    )

    private val homeGroup = listOf(
        Case<SpaceGroupMessage>(SpaceGroupMessage.InviteRequest("home-1"), """{"kind":"homegroup.invite.request","homeId":"home-1"}"""),
        Case(
            SpaceGroupMessage.Invite("home-1", "group-1", "invite-blob"),
            """{"kind":"homegroup.invite","homeId":"home-1","groupId":"group-1","invite":"invite-blob"}""",
        ),
    )

    private val deviceSync = listOf(
        Case<DeviceSyncMessage>(DeviceSyncMessage.Invite("inv", "Phone"), """{"kind":"devicesync.invite","invite":"inv","name":"Phone"}"""),
        Case(DeviceSyncMessage.Hello("Phone"), """{"kind":"devicesync.hello","name":"Phone"}"""),
        Case(
            DeviceSyncMessage.Rotate("inv", "old", "Phone"),
            """{"kind":"devicesync.rotate","invite":"inv","oldGroupId":"old","name":"Phone"}""",
        ),
    )

    private val blob = Case<BlobMessage>(BlobMessage.Want("home-1", "abc123"), """{"kind":"blob.want","homeId":"home-1","hash":"abc123"}""")

    @Test
    fun everyHomeJoinMessageEncodesToItsLiteralJsonAndTheLiteralDecodesBack() {
        // Arrange
        val cases = homeJoin

        // Act
        val encoded = cases.map { SpaceJoinMessage.encode(it.message).decodeToString() }
        val decoded = cases.map { SpaceJoinMessage.decode(base64(it.json)) }

        // Assert
        assertEquals(cases.map { it.json }, encoded)
        assertEquals(cases.map { it.message }, decoded)
    }

    @Test
    fun everyHomeGroupMessageEncodesToItsLiteralJsonAndTheLiteralDecodesBack() {
        // Arrange
        val cases = homeGroup

        // Act
        val encoded = cases.map { SpaceGroupMessage.encode(it.message).decodeToString() }
        val decoded = cases.map { SpaceGroupMessage.decode(base64(it.json)) }

        // Assert
        assertEquals(cases.map { it.json }, encoded)
        assertEquals(cases.map { it.message }, decoded)
        assertEquals("home-1", SpaceGroupMessage.stringField(base64(cases[0].json), "homeId"))
    }

    @Test
    fun everyDeviceSyncMessageEncodesToItsLiteralJsonAndTheLiteralDecodesBack() {
        // Arrange
        val cases = deviceSync

        // Act
        val encoded = cases.map { DeviceSyncMessage.encode(it.message).decodeToString() }
        val decoded = cases.map { DeviceSyncMessage.decode(base64(it.json)) }

        // Assert
        assertEquals(cases.map { it.json }, encoded)
        assertEquals(cases.map { it.message }, decoded)
    }

    @Test
    fun theBlobWantMessageEncodesToItsLiteralJsonAndTheLiteralDecodesBack() {
        // Arrange
        val case = blob

        // Act
        val encoded = BlobMessage.encode(case.message).decodeToString()
        val decoded = BlobMessage.decode(base64(case.json))

        // Assert
        assertEquals(case.json, encoded)
        assertEquals(case.message, decoded)
    }

    @Test
    fun theInviteLinkEncodesToItsLiteralStringAndTheLiteralDecodesBack() {
        // Arrange
        val link = InviteLink("home-1", "s3cret", 1700000000000L, listOf("agent-1", "agent-2"), "The: Johnsons")
        val literal = "vesta://invite/aG9tZS0xOnMzY3JldDoxNzAwMDAwMDAwMDAwOmFnZW50LTEsYWdlbnQtMjpUaGU6IEpvaG5zb25z"

        // Act
        val encoded = link.encode()
        val decoded = InviteLink.decode(literal)

        // Assert
        assertEquals(literal, encoded)
        assertEquals(link, decoded)
    }

    @Test
    fun thePairingLinkEncodesToItsLiteralStringWithAndWithoutADeviceName() {
        // Arrange
        val named = PairingLink("agent-1", 1700000000000L, "Mark: Phone")
        val namedLiteral = "vesta://pair/YWdlbnQtMToxNzAwMDAwMDAwMDAwOk1hcms6IFBob25l"
        val bare = PairingLink("agent-1", 1700000000000L)
        val bareLiteral = "vesta://pair/YWdlbnQtMToxNzAwMDAwMDAwMDAw"

        // Act
        val encoded = listOf(named.encode(), bare.encode())
        val decoded = listOf(PairingLink.decode(namedLiteral), PairingLink.decode(bareLiteral))

        // Assert
        assertEquals(listOf(namedLiteral, bareLiteral), encoded)
        assertEquals(listOf(named, bare), decoded)
    }

    @Test
    fun theSyncEnvelopeIsTheLengthOfTheIdThenTheIdThenTheMessage() {
        // Arrange
        val envelope = SyncEnvelope("home-1:pantry", byteArrayOf(1, 2, 3))
        val literal = byteArrayOf(13, 104, 111, 109, 101, 45, 49, 58, 112, 97, 110, 116, 114, 121, 1, 2, 3)

        // Act
        val encoded = envelope.encode()
        val decoded = SyncEnvelope.decode(literal)

        // Assert
        assertContentEquals(literal, encoded)
        assertEquals("home-1:pantry", decoded?.documentId)
        assertContentEquals(byteArrayOf(1, 2, 3), decoded?.message)
    }
}
