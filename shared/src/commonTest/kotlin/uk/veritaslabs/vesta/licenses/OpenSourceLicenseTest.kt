package uk.veritaslabs.vesta.licenses

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class OpenSourceLicenseTest {

    @Test
    fun parsesTheGeneratedFormat() {
        // Arrange
        val json = """[{"name":"org.automerge:automerge","version":"0.0.9","licenses":["MIT License"],"url":"https://example.org"}]"""

        // Act
        val licenses = parseOpenSourceLicenses(json)

        // Assert
        assertEquals(listOf(OpenSourceLicense("org.automerge:automerge", "0.0.9", listOf("MIT License"), "https://example.org")), licenses)
    }

    @Test
    fun ignoresFieldsItDoesNotKnow() {
        // Arrange
        val json = """[{"name":"a:b","version":"1","licenses":[],"extra":true}]"""

        // Act
        val licenses = parseOpenSourceLicenses(json)

        // Assert
        assertEquals(listOf(OpenSourceLicense("a:b", "1", emptyList())), licenses)
    }

    @Test
    fun theBundledListLoadsAndIncludesTheCoreLibraries() = runTest {
        // Arrange
        // (the list is the committed composeResources/files/licenses.json)

        // Act
        val names = loadOpenSourceLicenses().map { it.name }

        // Assert
        assertTrue("org.automerge:automerge" in names, "Automerge should be listed")
        assertTrue("crates.io:x0x" in names, "x0x should be listed")
    }
}
