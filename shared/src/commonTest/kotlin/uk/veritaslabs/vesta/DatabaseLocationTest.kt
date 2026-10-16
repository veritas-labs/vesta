package uk.veritaslabs.vesta

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Existing installs hold their database where today's build puts it, so the location Vesta passes to the store must
 * resolve to exactly these places (docs/plans/kits-extraction-implementation-plan.md, WP9). The desktop and Android
 * resolutions are in the platform tests; the iOS one is the same `inDirectory` call on the documents directory.
 */
class DatabaseLocationTest {

    @Test
    fun vestaKeepsItsDatabaseInTheDotVestaDirectoryUnderTheNameVestaDb() {
        // Arrange
        val location = VestaDatabaseLocation

        // Act
        val names = location.appDirName to location.fileName

        // Assert
        assertEquals(".vesta" to "vesta.db", names)
    }

    @Test
    fun theIosPathIsTheFileInsideTheDocumentsDirectory() {
        // Arrange
        val documents = "/var/mobile/Containers/Data/Application/ABC/Documents"

        // Act
        val path = VestaDatabaseLocation.inDirectory(documents)

        // Assert
        assertEquals("/var/mobile/Containers/Data/Application/ABC/Documents/vesta.db", path)
    }
}
