package uk.veritaslabs.vesta.licenses

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
