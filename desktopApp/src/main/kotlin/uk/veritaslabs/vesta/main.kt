package uk.veritaslabs.vesta

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import uk.veritaslabs.vesta.automerge.openPantryDocumentSource

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Vesta",
    ) {
        VestaApp(openPantry = { database, homeId -> openPantryDocumentSource(database, homeId) })
    }
}