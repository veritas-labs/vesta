package uk.veritaslabs.vesta

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import uk.veritaslabs.vesta.agent.AgentCommandService
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
            backgroundServices = { database -> agentCommands.run(database) },
            dataSync = { database, client, devices ->
                DeviceDataSyncService(
                    client, database, devices, agentCommands.pantrySources(database), agentCommands.shoppingListSources(database),
                )
            },
        )
    }
}
