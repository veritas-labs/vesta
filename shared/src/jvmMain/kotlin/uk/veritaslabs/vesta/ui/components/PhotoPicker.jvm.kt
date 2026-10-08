package uk.veritaslabs.vesta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.veritaslabs.vesta.blobs.PhotoSource

private val PhotoExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

@Composable
actual fun rememberPhotoPicker(onPicked: (PhotoSource) -> Unit): PhotoPicker {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onPicked)
    return remember {
        PhotoPicker {
            scope.launch {
                // The dialog blocks its thread until it is closed.
                val path = withContext(Dispatchers.IO) {
                    val dialog = FileDialog(null as Frame?, "Choose a photo", FileDialog.LOAD).apply {
                        setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in PhotoExtensions }
                        isVisible = true
                    }
                    dialog.file?.let { dialog.directory + it }
                }
                path?.let { latest.value(PhotoSource.FilePath(it)) }
            }
        }
    }
}
