package uk.veritaslabs.kits.spaces

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read

private const val NameKey = "name"
private const val AvatarKey = "avatar"
private const val CreatedAtKey = "createdAt"
private const val NamedKey = "named"

/** Id of the user's Profile document, which isn't scoped to any Space (docs/adr/ADR-014-home-membership.md). */
const val ProfileDocumentId = "user:profile"

/** One Profile as the document holds it. */
data class RegistryProfile(
    val id: String,
    val name: String,
    val avatar: String?,
    val createdAt: Long,
    /** Whether the user chose this name, rather than it being a placeholder. */
    val named: Boolean = false,
) {
    /** False until every field of the entry has arrived. */
    val isComplete: Boolean get() = name.isNotEmpty() && createdAt > 0
}

/*
 * A map at the document root with one entry per Profile id, like the Space registry (ADR-013) and for the same reason:
 * no wrapping map for two devices to create concurrently. A person normally has one Profile, but two devices that
 * each made one before they were paired write two entries; every device then picks the same one (see [canonical]), so
 * they converge on a single id, which is the person's member id in every Space.
 */

/** Every Profile in the document. */
fun Read.readProfiles(): List<RegistryProfile> =
    mapEntries(ObjectId.ROOT).getOrNull().orEmpty().mapNotNull { entry ->
        val id = (entry.value as? AmValue.Map)?.id ?: return@mapNotNull null
        RegistryProfile(
            id = entry.key,
            name = str(id, NameKey).orEmpty(),
            avatar = str(id, AvatarKey),
            createdAt = str(id, CreatedAtKey)?.toLongOrNull() ?: 0L,
            named = (get(id, NamedKey).getOrNull() as? AmValue.Bool)?.value == true,
        )
    }

/**
 * The Profile every device agrees is the person's: one whose name the user chose beats a placeholder, then the oldest,
 * then the lowest id. So a name chosen on one device isn't lost to an older device's placeholder. Null if none is complete.
 */
fun List<RegistryProfile>.canonical(): RegistryProfile? =
    filter { it.isComplete }.minWithOrNull(compareBy({ !it.named }, { it.createdAt }, { it.id }))

/**
 * Adds the Profile [id], or brings its name and avatar up to date; [createdAt] is only used when it is new. [named]
 * records that the user chose the name, and is never taken back. Returns whether anything changed.
 */
fun putProfile(doc: Document, id: String, name: String, avatar: String?, createdAt: Long, named: Boolean = false): Boolean {
    val tx = doc.startTransaction()
    val existing = (tx.get(ObjectId.ROOT, id).getOrNull() as? AmValue.Map)?.id
    var changed = false
    val entry = existing ?: tx.set(ObjectId.ROOT, id, ObjectType.MAP).also { changed = true }
    if (tx.str(entry, NameKey) != name) { tx.set(entry, NameKey, name); changed = true }
    if (avatar != null && tx.str(entry, AvatarKey) != avatar) { tx.set(entry, AvatarKey, avatar); changed = true }
    if (named && (tx.get(entry, NamedKey).getOrNull() as? AmValue.Bool)?.value != true) { tx.set(entry, NamedKey, true); changed = true }
    if (tx.str(entry, CreatedAtKey) == null) { tx.set(entry, CreatedAtKey, createdAt.toString()); changed = true }
    if (changed) tx.commit() else tx.rollback()
    return changed
}

private fun Read.str(obj: ObjectId, key: String): String? = (get(obj, key).getOrNull() as? AmValue.Str)?.value
