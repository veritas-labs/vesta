package uk.veritaslabs.vesta.location

import java.io.File
import java.util.UUID
import uk.veritaslabs.vesta.autonomi.AutonomiClient

/** A [ChunkCache] with one file per address under [dir]. Writes go through a temp file and a rename. */
class DiskChunkCache(private val dir: File) : ChunkCache {

    private fun file(address: String): File? =
        address.takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() } }?.let { File(dir, it) }

    override fun get(address: String): ByteArray? =
        file(address)?.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }

    override fun put(address: String, bytes: ByteArray) {
        val target = file(address) ?: return
        runCatching {
            dir.mkdirs()
            val temp = File(dir, ".$address.${UUID.randomUUID()}.tmp")
            try {
                temp.writeBytes(bytes)
                if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
            } finally {
                temp.delete()
            }
        }
    }
}

/** Reads the pack's address from env `VESTA_LOCALITY_POINTER` / `VESTA_LOCALITY_ROOT`, else `vesta.locality*` properties. */
fun localityPackConfig(): LocalityPackConfig {
    fun setting(env: String, property: String) =
        (System.getenv(env) ?: System.getProperty(property))?.trim()?.takeIf { it.isNotEmpty() }
    return LocalityPackConfig(
        pointer = setting("VESTA_LOCALITY_POINTER", "vesta.localityPointer"),
        root = setting("VESTA_LOCALITY_ROOT", "vesta.localityRoot"),
    )
}

fun createLocalityPackSource(client: AutonomiClient, cacheDir: File): LocalityPackSource =
    AutonomiLocalityPackSource(client, localityPackConfig(), DiskChunkCache(cacheDir), JvmInflater(), Blake3ChunkVerifier)

/** Reads the places pack's address from env `VESTA_PLACES_POINTER` / `VESTA_PLACES_ROOT`, else `vesta.places*` properties. */
fun placesPackConfig(): PlacesPackConfig {
    fun setting(env: String, property: String) =
        (System.getenv(env) ?: System.getProperty(property))?.trim()?.takeIf { it.isNotEmpty() }
    return PlacesPackConfig(
        pointer = setting("VESTA_PLACES_POINTER", "vesta.placesPointer"),
        root = setting("VESTA_PLACES_ROOT", "vesta.placesRoot"),
    )
}

fun createPlacesPackSource(client: AutonomiClient, cacheDir: File): PlacesPackSource =
    AutonomiPlacesPackSource(client, placesPackConfig(), DiskChunkCache(cacheDir), JvmInflater(), Blake3ChunkVerifier)
