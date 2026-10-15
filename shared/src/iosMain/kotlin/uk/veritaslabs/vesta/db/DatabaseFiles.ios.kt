package uk.veritaslabs.vesta.db

import platform.Foundation.NSFileManager

internal actual object DatabaseFiles {
    actual fun exists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

    actual fun siblings(path: String): List<String> {
        val directory = path.substringBeforeLast('/')
        return NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, null).orEmpty()
            .map { "$directory/$it" }
    }

    actual fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
