package uk.veritaslabs.vesta.files

import androidx.compose.runtime.staticCompositionLocalOf

/** A file the user chose to open. */
class PickedFile(val name: String, val bytes: ByteArray)

/** Lets the user choose a file to read (an open dialog). */
fun interface FileImporter {
    /** The file the user chose, or null if they backed out or it couldn't be read. */
    suspend fun pick(): PickedFile?
}

/** The [FileImporter] of the running app; null where there is none (previews, iOS), in which case screens don't offer an import. */
val LocalFileImporter = staticCompositionLocalOf<FileImporter?> { null }
