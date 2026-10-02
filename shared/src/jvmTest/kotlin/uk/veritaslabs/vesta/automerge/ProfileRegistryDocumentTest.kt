package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.automerge.Document

class ProfileRegistryDocumentTest {

    @Test
    fun theOldestCompleteProfileIsCanonical() {
        // Arrange
        val doc = Document()
        putProfile(doc, "young", "Tablet", null, createdAt = 200L)
        putProfile(doc, "old", "Laptop", null, createdAt = 100L)

        // Act
        val canonical = doc.readProfiles().canonical()

        // Assert
        assertEquals("old", canonical?.id)
    }

    @Test
    fun theLowestIdBreaksATie() {
        // Arrange
        val doc = Document()
        putProfile(doc, "b", "Tablet", null, createdAt = 100L)
        putProfile(doc, "a", "Laptop", null, createdAt = 100L)

        // Act
        val canonical = doc.readProfiles().canonical()

        // Assert
        assertEquals("a", canonical?.id)
    }

    @Test
    fun noProfileIsCanonicalUntilItsFieldsHaveArrived() {
        // Arrange
        val profiles = listOf(RegistryProfile("p", name = "", avatar = null, createdAt = 0L))

        // Act
        val canonical = profiles.canonical()

        // Assert
        assertNull(canonical)
    }

    @Test
    fun puttingTheSameProfileTwiceChangesNothingTheSecondTime() {
        // Arrange
        val doc = Document()
        putProfile(doc, "p", "Laptop", null, createdAt = 100L)

        // Act
        val changed = putProfile(doc, "p", "Laptop", null, createdAt = 999L)

        // Assert
        assertEquals(false, changed)
        assertEquals(100L, doc.readProfiles().single().createdAt)
    }
}
