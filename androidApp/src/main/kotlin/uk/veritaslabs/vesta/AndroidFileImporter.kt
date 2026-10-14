package uk.veritaslabs.vesta

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.veritaslabs.vesta.files.FileImporter
import uk.veritaslabs.vesta.files.PickedFile

private const val MaxFileBytes = 8 * 1024 * 1024

/** Reads a file where the user chooses, through the system's document picker. Created while the activity is being created, like [AndroidFileExporter]. */
class AndroidFileImporter(private val activity: ComponentActivity) : FileImporter {
    private var pending: CompletableDeferred<Uri?>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { pending?.complete(it) }

    override suspend fun pick(): PickedFile? {
        val result = CompletableDeferred<Uri?>().also { pending = it }
        launcher.launch(arrayOf("*/*"))
        val uri = result.await() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                if (bytes.size > MaxFileBytes) null else PickedFile(uri.lastPathSegment ?: "calendar.ics", bytes)
            }.getOrNull()
        }
    }
}
