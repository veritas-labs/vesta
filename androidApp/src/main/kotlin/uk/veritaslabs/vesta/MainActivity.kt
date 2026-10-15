package uk.veritaslabs.vesta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.io.File
import uk.veritaslabs.vesta.agent.AgentCommandService
import uk.veritaslabs.vesta.blobs.AndroidPhotoEncoder
import uk.veritaslabs.vesta.blobs.DeviceBlobRuntime
import uk.veritaslabs.vesta.datasync.DeviceDataSyncService
import uk.veritaslabs.vesta.recipes.HttpRecipeImporter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // App-private storage for the embedded x0x daemon and the local database.
        VestaAndroid.install(this)

        // The Pantry screen and device sync must share each Home's Pantry source, so both go through this service.
        // TODO(ADR-009): agent commands are wired on desktop only. To enable them here, also pass
        // backgroundServices = { database -> agentCommands.run(database) } as desktopApp's main.kt does
        // (and decide how the allowlist is configured on a phone, where there is no environment variable).
        val agentCommands = AgentCommandService()
        val fileExporter = AndroidFileExporter(this)
        val fileImporter = AndroidFileImporter(this)
        setContent {
            VestaApp(
                openPantry = { database, homeId -> agentCommands.pantry(database, homeId) },
                assistantsFor = agentCommands::assistants,
                openShoppingList = { database, homeId -> agentCommands.shoppingList(database, homeId) },
                openCookbook = { database, homeId -> agentCommands.cookbook(database, homeId) },
                openMerchants = { database, homeId -> agentCommands.merchants(database, homeId) },
                openMealPlan = { database, homeId -> agentCommands.mealPlan(database, homeId) },
                openCalendar = { database, homeId -> agentCommands.calendar(database, homeId) },
                openPrivateCalendar = { database, homeId -> agentCommands.privateCalendar(database, homeId) },
                openTasks = { database, homeId -> agentCommands.tasks(database, homeId) },
                openPrivateTasks = { database, homeId -> agentCommands.privateTasks(database, homeId) },
                dataSync = { database, client, devices ->
                    DeviceDataSyncService(client, database, devices, ::getDeviceName, agentCommands.homeSources(database))
                },
                blobRuntime = { database, client, sync ->
                    DeviceBlobRuntime(
                        database, client, agentCommands.cookbookSources(database),
                        peersFor = { homeId -> sync?.blobPeers(homeId).orEmpty() },
                        directory = File(filesDir, "blobs"),
                        encoder = AndroidPhotoEncoder(),
                        transfersDir = File(filesDir, "x0x/transfers"), // the embedded daemon's data dir is filesDir/x0x; unverified that it lands files here
                    )
                },
                recipeImporter = HttpRecipeImporter(),
                fileExporter = fileExporter,
                fileImporter = fileImporter,
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}