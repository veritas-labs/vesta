package uk.veritaslabs.vesta

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.veritaslabs.vesta.files.FileImporter
import uk.veritaslabs.vesta.files.PickedFile

private const val MaxFileBytes = 8L * 1024 * 1024

/** Reads a file where the user chooses, through the system's open dialog, shown from a background thread for the reason [DesktopFileExporter] gives. */
class DesktopFileImporter(private val parent: Frame? = null) : FileImporter {
    override suspend fun pick(): PickedFile? = withContext(Dispatchers.IO) {
        val dialog = FileDialog(parent, "", FileDialog.LOAD).apply { isVisible = true }
        val chosen = dialog.file?.let { File(dialog.directory, it) }?.takeIf { it.isFile && it.length() <= MaxFileBytes } ?: return@withContext null
        runCatching { PickedFile(chosen.name, chosen.readBytes()) }.getOrNull()
    }
}
