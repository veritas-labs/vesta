package uk.veritaslabs.vesta

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import uk.veritaslabs.vesta.agent.AgentCommandService
import uk.veritaslabs.vesta.blobs.DeviceBlobRuntime
import uk.veritaslabs.vesta.blobs.SkiaPhotoEncoder
import uk.veritaslabs.vesta.datasync.DeviceDataSyncService

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Vesta",
    ) {
        // One service: the Pantry screen and agent commands must share each Home's Pantry source.
        val agentCommands = remember { AgentCommandService() }
        VestaApp(
            openPantry = { database, homeId -> agentCommands.pantry(database, homeId) },
            openShoppingList = { database, homeId -> agentCommands.shoppingList(database, homeId) },
            openCookbook = { database, homeId -> agentCommands.cookbook(database, homeId) },
            backgroundServices = { database -> agentCommands.run(database) },
            dataSync = { database, client, devices ->
                DeviceDataSyncService(
                    client, database, devices, agentCommands.pantrySources(database), agentCommands.shoppingListSources(database),
                    agentCommands.cookbookSources(database),
                )
            },
            blobRuntime = { database, client, sync ->
                DeviceBlobRuntime(
                    database, client, agentCommands.cookbookSources(database),
                    peersFor = { homeId -> sync?.blobPeers(homeId).orEmpty() },
                    directory = File(System.getProperty("user.home"), ".vesta/blobs"),
                    encoder = SkiaPhotoEncoder(),
                    transfersDir = x0xTransfersDir(),
                )
            },
            ownDeviceCanBeHub = true,
        )
    }
}

/** The standalone x0xd's `transfers/` folder, found where the client finds the daemon's data directory; null if there isn't one. */
private fun x0xTransfersDir(): File? {
    val home = System.getProperty("user.home")
    return listOfNotNull(
        System.getenv("X0X_DATA_DIR")?.let { File(it) },
        File(home, ".local/share/x0x"),
        File(home, "Library/Application Support/x0x"),
    ).map { File(it, "transfers") }.firstOrNull { it.isDirectory }
}
