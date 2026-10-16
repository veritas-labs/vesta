package uk.veritaslabs.vesta

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import uk.veritaslabs.vesta.automerge.CookbookBlobReferences
import uk.veritaslabs.vesta.automerge.homeIdsOf
import uk.veritaslabs.vesta.agent.AgentCommandService
import uk.veritaslabs.kits.blobs.DeviceBlobRuntime
import uk.veritaslabs.kits.blobs.SkiaPhotoEncoder
import uk.veritaslabs.vesta.automerge.VestaHomeSeeding
import uk.veritaslabs.kits.sync.DeviceDataSyncService
import uk.veritaslabs.vesta.recipes.HttpRecipeImporter

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
            openMerchants = { database, homeId -> agentCommands.merchants(database, homeId) },
            openMealPlan = { database, homeId -> agentCommands.mealPlan(database, homeId) },
            openCalendar = { database, homeId -> agentCommands.calendar(database, homeId) },
            openPrivateCalendar = { database, homeId -> agentCommands.privateCalendar(database, homeId) },
            openTasks = { database, homeId -> agentCommands.tasks(database, homeId) },
            openPrivateTasks = { database, homeId -> agentCommands.privateTasks(database, homeId) },
            backgroundServices = { database -> agentCommands.run(database) },
            assistantsFor = agentCommands::assistants,
            dataSync = { database, client, devices ->
                agentCommands.homeSources(database).let { sources ->
                    DeviceDataSyncService(client, database, devices, ::getDeviceName, sources.documentTypes(), spacesConfig = VestaKitConfig.home, seeding = VestaHomeSeeding(sources, database), sendOrder = VestaHomeSources.sendOrder)
                }
            },
            blobRuntime = { database, client, sync ->
                DeviceBlobRuntime(
                    database, client, CookbookBlobReferences(agentCommands.cookbookSources(database)), homeIdsOf(database),
                    peersFor = { homeId -> sync?.blobPeers(homeId).orEmpty() },
                    directory = File(System.getProperty("user.home"), ".vesta/blobs"),
                    encoder = SkiaPhotoEncoder(),
                    transfersDir = x0xTransfersDir(),
                )
            },
            recipeImporter = HttpRecipeImporter(),
            fileExporter = remember { DesktopFileExporter(window) },
            fileImporter = remember { DesktopFileImporter(window) },
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
