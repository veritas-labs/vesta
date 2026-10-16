package uk.veritaslabs.kits.blobs

import java.io.File

/** The bytes of this source, or null when it is over [PhotoLimits.MaxInputBytes] or unreadable. A file's size is checked before it is read. */
internal fun PhotoSource.readBytesWithinLimit(): ByteArray? = when (this) {
    is PhotoSource.Bytes -> bytes.takeIf { it.size <= PhotoLimits.MaxInputBytes }
    is PhotoSource.FilePath -> File(path).takeIf { it.isFile && it.length() <= PhotoLimits.MaxInputBytes }
        ?.let { runCatching { it.readBytes() }.getOrNull() }
}

/** True when the source is over the byte limit, as opposed to missing or unreadable. */
internal fun PhotoSource.exceedsByteLimit(): Boolean = when (this) {
    is PhotoSource.Bytes -> bytes.size > PhotoLimits.MaxInputBytes
    is PhotoSource.FilePath -> File(path).let { it.isFile && it.length() > PhotoLimits.MaxInputBytes }
}
