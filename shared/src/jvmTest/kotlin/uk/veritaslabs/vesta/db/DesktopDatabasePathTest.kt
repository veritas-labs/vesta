package uk.veritaslabs.vesta.db

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.kits.store.DatabaseDriverFactory
import uk.veritaslabs.kits.store.DatabaseLocation
import uk.veritaslabs.vesta.VestaDatabaseLocation

class DesktopDatabasePathTest {

    @Test
    fun vestaOnTheDesktopStillOpensDotVestaSlashVestaDbUnderTheUsersHome() {
        // Arrange
        val factory = DatabaseDriverFactory(VestaDatabaseLocation)

        // Act
        val path = factory.databasePath()

        // Assert
        assertEquals(File(System.getProperty("user.home"), ".vesta/vesta.db").absolutePath, path)
    }

    @Test
    fun anotherAppNamesItsOwnDirectoryAndFileAndGetsThem() {
        // Arrange
        val home = File(System.getProperty("user.home"))
        val factory = DatabaseDriverFactory(DatabaseLocation(appDirName = ".vesta", fileName = "other.db"))

        // Act
        val path = factory.databasePath()

        // Assert
        assertEquals(File(home, ".vesta/other.db").absolutePath, path)
    }
}
