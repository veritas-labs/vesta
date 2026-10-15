package uk.veritaslabs.vesta.devicesync

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uk.veritaslabs.vesta.homegroup.HomeGroupMessage

/** Telling "a message of this family that this version doesn't know" from "not one of this family at all" (ADR-034, WP4). */
class UnrecognisedKindTest {

    private fun payload(text: String) = Base64.Default.encode(text.encodeToByteArray())

    @Test
    fun everyKindThisVersionEncodesIsKnown() {
        // Arrange
        val deviceSync = listOf(
            DeviceSyncMessage.Invite("i", "n"), DeviceSyncMessage.Hello("n"), DeviceSyncMessage.Rotate("i", "g", "n"),
        ).map { DeviceSyncMessage.encode(it).let { bytes -> Base64.Default.encode(bytes) } }
        val homeGroup = listOf(HomeGroupMessage.InviteRequest("h"), HomeGroupMessage.Invite("h", "g", "i"))
            .map { Base64.Default.encode(HomeGroupMessage.encode(it)) }

        // Act
        val unrecognised = deviceSync.map { DeviceSyncMessage.unrecognisedKind(it) } + homeGroup.map { HomeGroupMessage.unrecognisedKind(it) }

        // Assert
        assertEquals(List(5) { null }, unrecognised)
    }

    @Test
    fun anUnknownKindWithTheFamilyPrefixIsReportedByThatFamilyOnly() {
        // Arrange
        val device = payload("""{"kind":"devicesync.teleport"}""")
        val group = payload("""{"kind":"homegroup.teleport","homeId":"h1"}""")

        // Act
        val results = listOf(
            DeviceSyncMessage.unrecognisedKind(device), HomeGroupMessage.unrecognisedKind(device),
            DeviceSyncMessage.unrecognisedKind(group), HomeGroupMessage.unrecognisedKind(group),
        )

        // Assert
        assertEquals(listOf("devicesync.teleport", null, null, "homegroup.teleport"), results)
        assertEquals("h1", HomeGroupMessage.stringField(group, "homeId"))
    }

    @Test
    fun foreignOrMalformedPayloadsAreNeverUnrecognisedKinds() {
        // Arrange
        val foreign = listOf(
            payload("""{"kind":"pantry.add"}"""), payload("""{"kind":5}"""), payload("""{"x":"devicesync.a"}"""), payload("nope"), payload("[]"), "%%%", "",
        )

        // Act
        val results = foreign.flatMap { listOf(DeviceSyncMessage.unrecognisedKind(it), HomeGroupMessage.unrecognisedKind(it)) } +
            listOf(DeviceSyncMessage.unrecognisedKind(null), HomeGroupMessage.unrecognisedKind(null))

        // Assert
        results.forEach { assertNull(it) }
    }
}
