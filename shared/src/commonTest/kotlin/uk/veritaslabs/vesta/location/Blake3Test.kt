package uk.veritaslabs.vesta.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Blake3Test {

    /** Reference hashes from the Python `blake3` package for the official test-vector input (byte i is i mod 251). */
    private val vectors = mapOf(
        0 to "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262",
        1 to "2d3adedff11b61f14c886e35afa036736dcd87a74d27b5c1510225d0f592e213",
        63 to "e9bc37a594daad83be9470df7f7b3798297c3d834ce80ba85d6e207627b7db7b",
        64 to "4eed7141ea4a5cd4b788606bd23f46e212af9cacebacdc7d1f4c6dc7f2511b98",
        65 to "de1e5fa0be70df6d2be8fffd0e99ceaa8eb6e8c93a63f2d8d1c30ecb6b263dee",
        1023 to "10108970eeda3eb932baac1428c7a2163b0e924c9a9e25b35bba72b28f70bd11",
        1024 to "42214739f095a406f3fc83deb889744ac00df831c10daa55189b5d121c855af7",
        1025 to "d00278ae47eb27b34faecf67b4fe263f82d5412916c1ffd97c8cb7fb814b8444",
        2048 to "e776b6028c7cd22a4d0ba182a8bf62205d2ef576467e838ed6f2529b85fba24a",
        2049 to "5f4d72f40d7a5f82b15ca2b2e44b1de3c2ef86c426c95c1af0b6879522563030",
        3072 to "b98cb0ff3623be03326b373de6b9095218513e64f1ee2edd2525c7ad1e5cffd2",
        4096 to "015094013f57a5277b59d8475c0501042c0b642e531b0a1c8f58d2163229e969",
        7168 to "61da957ec2499a95d6b8023e2b0e604ec7f6b50e80a9678b89d2628e99ada77a",
        31744 to "62b6960e1a44bcc1eb1a611a8d6235b6b4b78f32e7abc4fb4c6cdcce94895c47",
        100000 to "d93c23eedaf165a7e0be908ba86f1a7a520d568d2d13cde787c8580c5c72cc54",
    )

    private fun input(length: Int) = ByteArray(length) { (it % 251).toByte() }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun matchesTheReferenceHashesAcrossBlockChunkAndTreeBoundaries() {
        vectors.forEach { (length, expected) ->
            // Arrange
            val bytes = input(length)

            // Act
            val actual = hex(Blake3.hash(bytes))

            // Assert
            assertEquals(expected, actual, "length $length")
        }
    }

    @Test
    fun theVerifierAcceptsTheRightAddressInAnyCase() {
        // Arrange
        val bytes = input(2049)
        val address = vectors.getValue(2049)

        // Act / Assert
        assertTrue(Blake3ChunkVerifier.matches(address, bytes))
        assertTrue(Blake3ChunkVerifier.matches(address.uppercase(), bytes))
    }

    @Test
    fun theVerifierRejectsChangedBytesAndAnotherAddress() {
        // Arrange
        val bytes = input(2049)
        val changed = bytes.copyOf().also { it[100] = (it[100] + 1).toByte() }

        // Act
        val changedMatches = Blake3ChunkVerifier.matches(vectors.getValue(2049), changed)
        val otherMatches = Blake3ChunkVerifier.matches(vectors.getValue(2048), bytes)

        // Assert
        assertFalse(changedMatches)
        assertFalse(otherMatches)
    }
}
