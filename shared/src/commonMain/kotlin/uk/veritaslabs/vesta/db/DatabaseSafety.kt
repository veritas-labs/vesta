package uk.veritaslabs.vesta.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import kotlin.time.Clock

/**
 * The database file was written by a newer version of Vesta than this one ([storedVersion] is its
 * PRAGMA user_version, [supportedVersion] the newest this build understands).
 *
 * The file is left exactly as it was: nothing deleted or reset, no schema change. Opening it anyway
 * would not migrate it (the driver only migrates upwards) and this build's queries would run against
 * a schema it does not know, so the app must tell the user to update instead.
 */
class DatabaseDowngradeException(
    val storedVersion: Long,
    val supportedVersion: Long,
) : IllegalStateException(
    "The Vesta database is at version $storedVersion but this app only understands up to version $supportedVersion. " +
        "Update Vesta; the data has not been changed.",
)

/**
 * What the app does to the database file before the SQLDelight driver touches it.
 *
 * The driver (sqldelight-androidx-driver 0.2.2) reads PRAGMA user_version on first use and acts only
 * when it is below the schema version: it creates the schema at 0 and migrates when lower. When the
 * file is HIGHER (a downgrade) it does nothing at all: no error, no write, it carries on and the
 * app's queries hit tables and columns that are missing or different. So a downgrade is detected here,
 * before the driver exists, and refused.
 *
 * Before an upgrade it takes a safety copy with `VACUUM INTO`, which writes one consistent
 * single-file snapshot from the live connection (so -wal and -shm need no separate copying), named
 * `<db>.pre-v<oldVersion>-<epochMillis>.bak` beside the database. It happens only when the stored version
 * is above 0 and below the schema's, so it runs once per upgrade and never on an ordinary launch. The
 * newest [prepare]`backupsToKeep` copies are kept.
 *
 * If the copy cannot be made the upgrade still goes ahead: the driver migrates inside one transaction
 * that rolls back on failure, and refusing to start on, say, a full disk would not protect the data.
 */
internal object DatabaseSafety {
    const val DEFAULT_BACKUPS_TO_KEEP = 2

    /** @throws DatabaseDowngradeException when the file's version is above [schemaVersion]. */
    fun prepare(
        path: String,
        schemaVersion: Long,
        backupsToKeep: Int = DEFAULT_BACKUPS_TO_KEEP,
        nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ) {
        if (!DatabaseFiles.exists(path)) return
        val connection = BundledSQLiteDriver().open(path, SQLITE_OPEN_READWRITE)
        try {
            val stored = connection.userVersion()
            if (stored > schemaVersion) throw DatabaseDowngradeException(stored, schemaVersion)
            if (stored in 1 until schemaVersion) {
                val backup = "$path.pre-v$stored-${nowMillis()}.bak"
                try {
                    DatabaseFiles.delete(backup)
                    connection.execSQL("VACUUM INTO '${backup.replace("'", "''")}'")
                } catch (e: Exception) {
                    DatabaseFiles.delete(backup) // never leave a partial copy that looks like a good one
                    return
                }
                prune(path, backupsToKeep)
            }
        } finally {
            connection.close()
        }
    }

    private fun fileName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')

    private fun prune(path: String, keep: Int) {
        val prefix = "${fileName(path)}.pre-v"
        DatabaseFiles.siblings(path)
            .filter { fileName(it).let { name -> name.startsWith(prefix) && name.endsWith(".bak") } }
            .sortedByDescending { it.removeSuffix(".bak").substringAfterLast('-').toLongOrNull() ?: 0L }
            .drop(keep)
            .forEach(DatabaseFiles::delete)
    }

    private fun SQLiteConnection.userVersion(): Long =
        prepare("PRAGMA user_version").use { if (it.step()) it.getLong(0) else 0L }
}

/** The few file operations [DatabaseSafety] needs, as the platforms give them. */
internal expect object DatabaseFiles {
    fun exists(path: String): Boolean

    /** Full paths of the files in the same directory as [path] (including [path] itself). */
    fun siblings(path: String): List<String>

    /** Deletes [path] if it exists; does nothing otherwise. */
    fun delete(path: String)
}
