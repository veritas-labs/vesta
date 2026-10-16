package uk.veritaslabs.kits.spaces

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read
private const val NameKey = "name"
// The stored key predates the Space vocabulary and is on the wire inside Automerge (ADR-037): it stays `shape`.
private const val KindKey = "shape"
private const val CreatedAtKey = "createdAt"
private const val DeletedKey = "deleted"

/** ADR-006-style id of the user's Space registry, which isn't scoped to any Space. */
const val SpaceRegistryDocumentId = "user:homes" // persisted and on the wire: the id predates the Space vocabulary (ADR-037), so it must not change

/** One Space as the registry holds it; a [deleted] entry is a tombstone, so a deletion reaches the other devices. */
data class RegistrySpace(val id: String, val name: String, val kind: String, val createdAt: Long, val deleted: Boolean)

/*
 * The registry is a map at the document root with one entry per Space id (docs/adr/ADR-013-device-data-sync.md).
 * There is deliberately no wrapping `homes` map: two devices that each created one would conflict on that key and
 * one device's Spaces would be lost on merge. Keyed by Space id, a Space's entry is only ever created by the device
 * that made the Space. Updates set individual fields, so edits to different fields merge.
 */

/** Every Space in the registry, tombstones included. */
fun Read.readSpaceRegistry(): List<RegistrySpace> =
    mapEntries(ObjectId.ROOT).getOrNull().orEmpty().mapNotNull { entry ->
        val id = (entry.value as? AmValue.Map)?.id ?: return@mapNotNull null
        RegistrySpace(
            id = entry.key,
            name = str(id, NameKey).orEmpty(),
            kind = str(id, KindKey).orEmpty(),
            createdAt = str(id, CreatedAtKey)?.toLongOrNull() ?: 0L,
            deleted = (get(id, DeletedKey).getOrNull() as? AmValue.Bool)?.value == true,
        )
    }

/** Adds [space] to the registry, or brings its name and kind up to date. A deleted Space stays deleted. Returns whether anything changed. */
fun putRegistrySpace(doc: Document, space: StoredSpace): Boolean {
    val tx = doc.startTransaction()
    val existing = (tx.get(ObjectId.ROOT, space.id).getOrNull() as? AmValue.Map)?.id
    if (existing != null && tx.get(existing, DeletedKey).isPresent) {
        tx.rollback()
        return false
    }
    var changed = false
    val entry = existing ?: tx.set(ObjectId.ROOT, space.id, ObjectType.MAP).also { changed = true }
    if (tx.str(entry, NameKey) != space.name) { tx.set(entry, NameKey, space.name); changed = true }
    if (tx.str(entry, KindKey) != space.kind) { tx.set(entry, KindKey, space.kind); changed = true }
    if (tx.str(entry, CreatedAtKey) == null) { tx.set(entry, CreatedAtKey, space.createdAt.toString()); changed = true }
    if (changed) tx.commit() else tx.rollback()
    return changed
}

/** Marks the Space with [id] deleted. Returns whether anything changed. */
fun deleteRegistrySpace(doc: Document, id: String): Boolean {
    val tx = doc.startTransaction()
    val entry = (tx.get(ObjectId.ROOT, id).getOrNull() as? AmValue.Map)?.id ?: tx.set(ObjectId.ROOT, id, ObjectType.MAP)
    if ((tx.get(entry, DeletedKey).getOrNull() as? AmValue.Bool)?.value == true) {
        tx.rollback()
        return false
    }
    tx.set(entry, DeletedKey, true)
    tx.commit()
    return true
}

private fun Read.str(obj: ObjectId, key: String): String? = (get(obj, key).getOrNull() as? AmValue.Str)?.value
