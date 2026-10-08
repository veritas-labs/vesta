package uk.veritaslabs.vesta.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.veritaslabs.vesta.blobs.PhotoLimits
import uk.veritaslabs.vesta.blobs.PhotoSource

@Composable
actual fun rememberPhotoPicker(onPicked: (PhotoSource) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            scope.launch {
                // Read at most one byte over the limit, so an oversized photo is refused by the encoder without being held in memory.
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readNBytes((PhotoLimits.MaxInputBytes + 1).toInt()) }
                    }.getOrNull()
                }
                bytes?.let { latest.value(PhotoSource.Bytes(it)) }
            }
        }
    }
    return remember(launcher) { PhotoPicker { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}
