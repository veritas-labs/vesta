package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read
import uk.veritaslabs.vesta.home.StoredHome

private const val NameKey = "name"
private const val ShapeKey = "shape"
private const val CreatedAtKey = "createdAt"
private const val DeletedKey = "deleted"

/** ADR-006-style id of the user's Home registry, which isn't scoped to any Home. */
const val HomeRegistryDocumentId = "user:homes"

/** One Home as the registry holds it; a [deleted] entry is a tombstone, so a deletion reaches the other devices. */
data class RegistryHome(val id: String, val name: String, val shape: String, val createdAt: Long, val deleted: Boolean)

/*
 * The registry is a map at the document root with one entry per Home id (docs/adr/ADR-013-device-data-sync.md).
 * There is deliberately no wrapping `homes` map: two devices that each created one would conflict on that key and
 * one device's Homes would be lost on merge. Keyed by Home id, a Home's entry is only ever created by the device
 * that made the Home. Updates set individual fields, so edits to different fields merge.
 */

/** Every Home in the registry, tombstones included. */
fun Read.readHomeRegistry(): List<RegistryHome> =
    mapEntries(ObjectId.ROOT).getOrNull().orEmpty().mapNotNull { entry ->
        val id = (entry.value as? AmValue.Map)?.id ?: return@mapNotNull null
        RegistryHome(
            id = entry.key,
            name = str(id, NameKey).orEmpty(),
            shape = str(id, ShapeKey).orEmpty(),
            createdAt = str(id, CreatedAtKey)?.toLongOrNull() ?: 0L,
            deleted = (get(id, DeletedKey).getOrNull() as? AmValue.Bool)?.value == true,
        )
    }

/** Adds [home] to the registry, or brings its name and shape up to date. A deleted Home stays deleted. Returns whether anything changed. */
fun putRegistryHome(doc: Document, home: StoredHome): Boolean {
    val tx = doc.startTransaction()
    val existing = (tx.get(ObjectId.ROOT, home.id).getOrNull() as? AmValue.Map)?.id
    if (existing != null && tx.get(existing, DeletedKey).isPresent) {
        tx.rollback()
        return false
    }
    var changed = false
    val entry = existing ?: tx.set(ObjectId.ROOT, home.id, ObjectType.MAP).also { changed = true }
    if (tx.str(entry, NameKey) != home.name) { tx.set(entry, NameKey, home.name); changed = true }
    if (tx.str(entry, ShapeKey) != home.shape) { tx.set(entry, ShapeKey, home.shape); changed = true }
    if (tx.str(entry, CreatedAtKey) == null) { tx.set(entry, CreatedAtKey, home.createdAt.toString()); changed = true }
    if (changed) tx.commit() else tx.rollback()
    return changed
}

/** Marks the Home with [id] deleted. Returns whether anything changed. */
fun deleteRegistryHome(doc: Document, id: String): Boolean {
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
