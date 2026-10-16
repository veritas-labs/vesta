package uk.veritaslabs.kits.store

/**
 * Where the app keeps its database file, which the app says and the store does not guess: the kit has no name of its
 * own to put on disk, and an install that already holds a file must keep finding it.
 *
 * @property appDirName the directory under the user's home that the desktop keeps the file in (`.vesta`); the phones
 * ignore it, as each app has its own sandboxed storage there.
 * @property fileName the database file's name (`vesta.db`).
 */
data class DatabaseLocation(
    val appDirName: String,
    val fileName: String,
) {
    /** The file inside [directory], the way the desktop and iOS spell it. */
    fun inDirectory(directory: String): String = "$directory/$fileName"
}
