package uk.veritaslabs.vesta

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.veritaslabs.vesta.files.FileExporter

/** Saves a file where the user chooses, through the system's save dialog. */
class DesktopFileExporter(private val parent: Frame? = null) : FileExporter {
    override suspend fun export(fileName: String, mimeType: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        // The dialog is modal and blocks until it is closed, running its own event loop. Opened from the click handler's coroutine
        // that loop re-entered Compose's dispatcher and crashed it, so it is shown from a background thread, which AWT allows and
        // which leaves the UI thread free to keep drawing.
        val dialog = FileDialog(parent, fileName, FileDialog.SAVE).apply {
            file = fileName
            isVisible = true
        }
        val chosen = dialog.file?.let { File(dialog.directory, it) } ?: return@withContext false
        runCatching { chosen.writeBytes(bytes) }.isSuccess
    }
}
