package uk.veritaslabs.vesta.kits

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sync engine's send order is the app's to give (`sendOrder`), and leaving it out would silently change which document a
 * peer receives first. Every place the apps build `DeviceDataSyncService` must pass `VestaHomeSources.sendOrder`.
 */
class SendOrderWiringTest {

    private fun appSources(): List<File> {
        val root = listOf(File("."), File("..")).first { File(it, "desktopApp").isDirectory }
        return listOf("desktopApp/src/main", "androidApp/src/main", "shared/src/commonMain", "shared/src/jvmAndroidMain")
            .map { File(root, it) }.filter { it.isDirectory }
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }
    }

    @Test
    fun everyProductionEngineIsGivenTheSendOrder() {
        // Arrange
        val files = appSources().filter { "DeviceDataSyncService(" in it.readText() && !it.readText().contains("class DeviceDataSyncService") }

        // Act
        val withoutOrder = files.filter { "sendOrder = VestaHomeSources.sendOrder" !in it.readText() }.map { it.name }

        // Assert
        assertTrue(files.size >= 2, "expected the desktop and Android wiring, found ${files.map { it.name }}")
        assertEquals(emptyList(), withoutOrder)
    }
}
