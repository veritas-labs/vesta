package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read

private const val ItemsKey = "items"
private const val IdKey = "id"
private const val PrimaryCategoryKey = "primaryCategory"
private const val SecondaryCategoryKey = "secondaryCategory"
private const val TitleKey = "title"
private const val QuantityKey = "quantity"
private const val PackageSizeKey = "packageSize"
private const val BarcodeKey = "barcode"
private const val TagsKey = "tags"
private const val GotKey = "got"
private const val OutOfStockKey = "outOfStock"

/**
 * One line of a Home's shopping list — see ADR-008 ("Fourth amendment"). [id] is the item's [pantryDedupKey], which
 * is also the key of the Pantry entry it corresponds to.
 */
data class ShoppingItem(
    val id: String,
    val primaryCategory: String,
    val secondaryCategory: String?,
    val title: String,
    val quantity: Int,
    val packageSize: String,
    val barcode: String?,
    val tags: List<String> = emptyList(),
    /** How many of [quantity] have been picked up so far; [quantity] or more means crossed off. */
    val got: Int = 0,
    /** The shop had none of it. */
    val outOfStock: Boolean = false,
)

/** Builds the initial (empty) bytes for a shopping-list document. */
fun newShoppingListDocument(): ByteArray {
    val doc = Document()
    val tx = doc.startTransaction()
    tx.set(ObjectId.ROOT, ItemsKey, ObjectType.MAP)
    tx.commit()
    return doc.save()
}

/** Reads every item, ordered by primary category, secondary category, then title (case-insensitive). */
fun Read.readShoppingList(): List<ShoppingItem> {
    val itemsId = itemsMapId() ?: return emptyList()
    return mapEntries(itemsId).getOrNull()
        ?.mapNotNull { (it.value as? AmValue.Map)?.id }
        ?.map { readShoppingItemAt(it) }
        ?.sortedWith(
            compareBy<ShoppingItem, String>(String.CASE_INSENSITIVE_ORDER) { it.primaryCategory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.secondaryCategory.orEmpty() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        .orEmpty()
}

/** Reads a single item by its dedup key, or `null` if the list has none there. */
fun Read.readShoppingItem(key: String): ShoppingItem? {
    val itemId = itemsMapId()?.let { (get(it, key).getOrNull() as? AmValue.Map)?.id } ?: return null
    return readShoppingItemAt(itemId)
}

/**
 * Adds [quantity] of a product to the list. When the list already has the product (same [pantryDedupKey]) the
 * quantities are added together and the other fields are left as they are; otherwise a new item is created.
 * Returns the item's key. Does not touch the Pantry — see `ensurePantryItem`.
 */
fun addShoppingItem(
    doc: Document,
    primaryCategory: String,
    secondaryCategory: String?,
    title: String,
    quantity: Int,
    packageSize: String,
    barcode: String?,
    tags: List<String> = emptyList(),
): String {
    val key = pantryDedupKey(barcode, title)
    val tx = doc.startTransaction()
    val itemsId = tx.itemsMapId() ?: tx.set(ObjectId.ROOT, ItemsKey, ObjectType.MAP)
    val existingId = (tx.get(itemsId, key).getOrNull() as? AmValue.Map)?.id
    if (existingId != null) {
        val current = (tx.get(existingId, QuantityKey).getOrNull() as? AmValue.Int)?.value?.toInt() ?: 0
        tx.set(existingId, QuantityKey, current + quantity)
        tx.mergeTags(existingId, tags)
    } else {
        tx.writeItem(tx.set(itemsId, key, ObjectType.MAP), key, primaryCategory, secondaryCategory, title, quantity, packageSize, barcode, tags)
    }
    tx.commit()
    return key
}

/**
 * Replaces every field of the item at [key]. If the new [title]/[barcode] give it a different dedup key it moves
 * there (adding its quantity to any item already at that key). No-op if [key] has no item. Returns the item's key
 * after the edit, or `null` if there was none.
 */
fun updateShoppingItem(
    doc: Document,
    key: String,
    primaryCategory: String,
    secondaryCategory: String?,
    title: String,
    quantity: Int,
    packageSize: String,
    barcode: String?,
    tags: List<String> = emptyList(),
): String? {
    val newKey = pantryDedupKey(barcode, title)
    val tx = doc.startTransaction()
    val itemsId = tx.itemsMapId()
    val itemId = itemsId?.let { (tx.get(it, key).getOrNull() as? AmValue.Map)?.id }
    if (itemsId == null || itemId == null) {
        tx.rollback()
        return null
    }
    if (newKey == key) {
        tx.writeItem(itemId, key, primaryCategory, secondaryCategory, title, quantity, packageSize, barcode, tags)
    } else {
        tx.delete(itemsId, key)
        val targetId = (tx.get(itemsId, newKey).getOrNull() as? AmValue.Map)?.id
        if (targetId != null) {
            val current = (tx.get(targetId, QuantityKey).getOrNull() as? AmValue.Int)?.value?.toInt() ?: 0
            tx.set(targetId, QuantityKey, current + quantity)
            tx.mergeTags(targetId, tags)
        } else {
            tx.writeItem(tx.set(itemsId, newKey, ObjectType.MAP), newKey, primaryCategory, secondaryCategory, title, quantity, packageSize, barcode, tags)
        }
    }
    tx.commit()
    return newKey
}

/**
 * Records how many of the item at [key] have been picked up ([got], at least 0) and whether the shop was out of it.
 * Only these two fields are written, so the rest of the item (and a concurrent edit of it) is left alone. No-op if
 * [key] has no item.
 */
fun setShoppingItemProgress(doc: Document, key: String, got: Int, outOfStock: Boolean) {
    val tx = doc.startTransaction()
    val itemId = tx.itemsMapId()?.let { (tx.get(it, key).getOrNull() as? AmValue.Map)?.id }
    if (itemId == null) {
        tx.rollback()
        return
    }
    tx.set(itemId, GotKey, got.coerceAtLeast(0))
    tx.set(itemId, OutOfStockKey, outOfStock)
    tx.commit()
}

/** Removes the item at [key]. No-op if there is none. */
fun removeShoppingItem(doc: Document, key: String) {
    val tx = doc.startTransaction()
    val itemsId = tx.itemsMapId()
    if (itemsId != null && tx.get(itemsId, key).isPresent) {
        tx.delete(itemsId, key)
        tx.commit()
    } else {
        tx.rollback()
    }
}

private fun org.automerge.Transaction.writeItem(
    itemId: ObjectId,
    key: String,
    primaryCategory: String,
    secondaryCategory: String?,
    title: String,
    quantity: Int,
    packageSize: String,
    barcode: String?,
    tags: List<String>,
) {
    set(itemId, IdKey, key)
    set(itemId, PrimaryCategoryKey, primaryCategory)
    if (secondaryCategory != null) {
        set(itemId, SecondaryCategoryKey, secondaryCategory)
    } else if (get(itemId, SecondaryCategoryKey).isPresent) {
        delete(itemId, SecondaryCategoryKey)
    }
    set(itemId, TitleKey, title)
    set(itemId, QuantityKey, quantity)
    set(itemId, PackageSizeKey, packageSize)
    if (barcode != null) {
        set(itemId, BarcodeKey, barcode)
    } else if (get(itemId, BarcodeKey).isPresent) {
        delete(itemId, BarcodeKey)
    }
    writeTags(itemId, normalizeTags(tags))
}

private fun org.automerge.Transaction.writeTags(itemId: ObjectId, tags: List<String>) {
    if (tags.isEmpty()) {
        if (get(itemId, TagsKey).isPresent) delete(itemId, TagsKey)
        return
    }
    val listId = set(itemId, TagsKey, ObjectType.LIST)
    tags.forEachIndexed { index, tag -> insert(listId, index.toLong(), tag) }
}

/** Adds [tags] the item doesn't already have (ignoring case) to its own, for a product added to the list twice. */
private fun org.automerge.Transaction.mergeTags(itemId: ObjectId, tags: List<String>) {
    val current = readTags(itemId)
    val merged = normalizeTags(current + tags)
    if (merged != current) writeTags(itemId, merged)
}

/** [tags] trimmed, without blanks, and without repeats (ignoring case; the first spelling wins). */
fun normalizeTags(tags: List<String>): List<String> =
    tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

/** Whether this document holds a shopping list's root map; see [isPantrySeeded] for why an unseeded one must not be edited. */
fun Read.isShoppingListSeeded(): Boolean = itemsMapId() != null

private fun Read.itemsMapId(): ObjectId? = (get(ObjectId.ROOT, ItemsKey).getOrNull() as? AmValue.Map)?.id

private fun Read.readShoppingItemAt(itemId: ObjectId): ShoppingItem = ShoppingItem(
    id = strField(itemId, IdKey),
    primaryCategory = strField(itemId, PrimaryCategoryKey),
    secondaryCategory = (get(itemId, SecondaryCategoryKey).getOrNull() as? AmValue.Str)?.value,
    title = strField(itemId, TitleKey),
    quantity = (get(itemId, QuantityKey).getOrNull() as? AmValue.Int)?.value?.toInt() ?: 0,
    packageSize = strField(itemId, PackageSizeKey),
    barcode = (get(itemId, BarcodeKey).getOrNull() as? AmValue.Str)?.value,
    tags = readTags(itemId),
    got = (get(itemId, GotKey).getOrNull() as? AmValue.Int)?.value?.toInt() ?: 0,
    outOfStock = (get(itemId, OutOfStockKey).getOrNull() as? AmValue.Bool)?.value ?: false,
)

private fun Read.readTags(itemId: ObjectId): List<String> {
    val listId = (get(itemId, TagsKey).getOrNull() as? AmValue.List)?.id ?: return emptyList()
    return listItems(listId).getOrNull()?.mapNotNull { (it as? AmValue.Str)?.value }.orEmpty()
}

private fun Read.strField(obj: ObjectId, key: String): String =
    (get(obj, key).getOrNull() as? AmValue.Str)?.value.orEmpty()
