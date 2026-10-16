package uk.veritaslabs.kits.store

import java.io.File

internal actual object DatabaseFiles {
    actual fun exists(path: String): Boolean = File(path).isFile

    actual fun siblings(path: String): List<String> =
        File(path).absoluteFile.parentFile?.listFiles()?.map { it.path }.orEmpty()

    actual fun delete(path: String) {
        File(path).delete()
    }
}
