package uk.veritaslabs.vesta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.io.File
import uk.veritaslabs.vesta.automerge.VestaHomeSources
import uk.veritaslabs.vesta.home.TasksDeepLink
import uk.veritaslabs.vesta.automerge.CookbookBlobReferences
import uk.veritaslabs.vesta.automerge.homeIdsOf
import uk.veritaslabs.vesta.agent.AgentCommandService
import uk.veritaslabs.kits.blobs.AndroidPhotoEncoder
import uk.veritaslabs.kits.blobs.DeviceBlobRuntime
import uk.veritaslabs.vesta.automerge.VestaHomeSeeding
import uk.veritaslabs.kits.sync.DeviceDataSyncService
import uk.veritaslabs.vesta.recipes.HttpRecipeImporter

class MainActivity : ComponentActivity() {
    // The activity is singleTop, so a tap on the digest while the app is open arrives here rather than in a new instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTasksIfAsked(intent)
    }

    private fun openTasksIfAsked(intent: Intent?) {
        intent?.getStringExtra(ExtraTasksHomeId)?.let { TasksDeepLink.request(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // App-private storage for the embedded x0x daemon and the local database.
        VestaAndroid.install(this)

        // The morning digest (ADR-040): arm today's or tomorrow's 07:00 alarm, and on Android 13+ ask once for permission to show it.
        MorningDigest.arm(this)
        openTasksIfAsked(intent)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

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
                    agentCommands.homeSources(database).let { sources ->
                    DeviceDataSyncService(client, database, devices, ::getDeviceName, sources.documentTypes(), spacesConfig = VestaKitConfig.home, seeding = VestaHomeSeeding(sources, database), sendOrder = VestaHomeSources.sendOrder)
                }
                },
                blobRuntime = { database, client, sync ->
                    DeviceBlobRuntime(
                        database, client, CookbookBlobReferences(agentCommands.cookbookSources(database)), homeIdsOf(database),
                        peersFor = { homeId -> sync?.blobPeers(homeId).orEmpty() },
                        directory = File(filesDir, "blobs"),
                        encoder = AndroidPhotoEncoder(),
                        userAgent = VestaKitConfig.userAgent,
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