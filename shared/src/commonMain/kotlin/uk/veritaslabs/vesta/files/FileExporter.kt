package uk.veritaslabs.vesta.files

import androidx.compose.runtime.staticCompositionLocalOf

/** Hands a file to the user, who chooses where it goes (a save dialog). */
fun interface FileExporter {
    /** Offers [bytes] as [fileName] of type [mimeType]; true once it is saved, false if the user backed out or it couldn't be written. */
    suspend fun export(fileName: String, mimeType: String, bytes: ByteArray): Boolean
}

/** The [FileExporter] of the running app; null where there is none (previews, iOS), in which case screens don't offer an export. */
val LocalFileExporter = staticCompositionLocalOf<FileExporter?> { null }
