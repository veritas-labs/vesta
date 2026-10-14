package uk.veritaslabs.vesta

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.veritaslabs.vesta.files.FileExporter

/**
 * Saves a file where the user chooses, through the system's "create document" screen. Must be created while the activity is being
 * created (before it starts), as an activity result launcher has to be registered then.
 */
class AndroidFileExporter(private val activity: ComponentActivity) : FileExporter {
    private var pending: CompletableDeferred<Uri?>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { pending?.complete(it) }

    override suspend fun export(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        val result = CompletableDeferred<Uri?>().also { pending = it }
        launcher.launch(fileName)
        val uri = result.await() ?: return false
        return withContext(Dispatchers.IO) {
            runCatching { activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)
        }
    }
}
