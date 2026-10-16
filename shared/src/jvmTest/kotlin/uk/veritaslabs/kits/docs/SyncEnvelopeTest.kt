@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.kits.docs

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncEnvelopeTest {

    @Test
    fun roundTripsTheDocumentIdAndMessage() {
        // Arrange
        val envelope = SyncEnvelope("home-1:pantry", byteArrayOf(1, 2, 3))

        // Act
        val decoded = SyncEnvelope.decode(envelope.encode())

        // Assert
        assertEquals("home-1:pantry", decoded?.documentId)
        assertContentEquals(byteArrayOf(1, 2, 3), decoded?.message)
    }

    @Test
    fun rejectsBytesThatAreTooShortForTheirLengthPrefix() {
        // Arrange
        val bytes = byteArrayOf(10, 1, 2)

        // Act
        val decoded = SyncEnvelope.decode(bytes)

        // Assert
        assertNull(decoded)
    }

    @Test
    fun rejectsEmptyInput() {
        // Arrange / Act
        val decoded = SyncEnvelope.decode(byteArrayOf())

        // Assert
        assertNull(decoded)
    }
}
