package uk.veritaslabs.vesta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import uk.veritaslabs.vesta.automerge.openPantryDocumentSource
import uk.veritaslabs.vesta.x0x.VestaAndroid

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // App-private storage for the embedded x0x daemon and the local database.
        VestaAndroid.install(this)

        setContent {
            VestaApp(openPantry = { database, homeId -> openPantryDocumentSource(database, homeId) })
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}