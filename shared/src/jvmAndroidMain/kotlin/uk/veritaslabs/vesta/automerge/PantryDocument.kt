package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import kotlin.time.Clock
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.NewValue
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read

private const val ItemsKey = "items"
private const val IdKey = "id"
private const val PrimaryCategoryKey = "primaryCategory"
private const val SecondaryCategoryKey = "secondaryCategory"
private const val TitleKey = "title"
private const val PackageSizeKey = "packageSize"
private const val BarcodeKey = "barcode"
private const val UseCountKey = "useCount"
private const val LastUsedAtKey = "lastUsedAt"
private const val StockCountKey = "stockCount"

/**
 * A household's shared product record — see ADR-008. One entry per distinct product, addressed by
 * [pantryDedupKey], read out of the Pantry Automerge document via [Read.readPantry]/[Read.readPantryItem].
 */
data class PantryItem(
    val id: String,
    val primaryCategory: String,
    val secondaryCategory: String?,
    val title: String,
    val packageSize: String,
    val barcode: String?,
    val useCount: Int,
    val lastUsedAt: String,
    val stockCount: Int,
)

/** Builds the initial (empty) bytes for a Pantry document. */
fun newPantryDocument(): ByteArray {
    val doc = Document()
    val tx = doc.startTransaction()
    tx.set(ObjectId.ROOT, ItemsKey, ObjectType.MAP)
    tx.seedCategories()
    tx.commit()
    return doc.save()
}

/** The dedup key a [PantryItem] is addressed by: [barcode] when present, else the normalized [title]. */
fun pantryDedupKey(barcode: String?, title: String): String = barcode ?: normalizeTitle(title)

private fun normalizeTitle(title: String): String = title.trim().lowercase()

/** Reads every Pantry entry, ordered by [PantryItem.useCount] descending then [PantryItem.lastUsedAt] descending. */
fun Read.readPantry(): List<PantryItem> {
    val itemsId = itemsMapId() ?: return emptyList()
    return mapEntries(itemsId).getOrNull()
        ?.mapNotNull { (it.value as? AmValue.Map)?.id }
        ?.map { readPantryItemAt(it) }
        ?.sortedWith(compareByDescending<PantryItem> { item -> item.useCount }.thenByDescending { item -> item.lastUsedAt })
        .orEmpty()
}

/** Reads a single Pantry entry by its dedup key, or `null` if no entry currently occupies that key. */
fun Read.readPantryItem(key: String): PantryItem? {
    val itemId = itemsMapId()?.let { (get(it, key).getOrNull() as? AmValue.Map)?.id } ?: return null
    return readPantryItemAt(itemId)
}

/**
 * Upserts the Pantry entry for a product with the given [barcode]/[title]: if an entry already exists at
 * [pantryDedupKey], increments its [PantryItem.useCount] by 1 and refreshes its other fields; otherwise creates a
 * new entry with `useCount = 1` and `stockCount = 0`. Returns the dedup key the entry was written at.
 */
fun upsertPantryItem(
    doc: Document,
    primaryCategory: String,
    secondaryCategory: String?,
    title: String,
    packageSize: String,
    barcode: String?,
    now: () -> String = { Clock.System.now().toString() },
): String {
    val key = pantryDedupKey(barcode, title)
    val tx = doc.startTransaction()
    val itemsId = tx.itemsMapId() ?: tx.set(ObjectId.ROOT, ItemsKey, ObjectType.MAP)
    val existingId = (tx.get(itemsId, key).getOrNull() as? AmValue.Map)?.id
    val itemId = existingId ?: tx.set(itemsId, key, ObjectType.MAP)

    tx.set(itemId, IdKey, key)
    tx.set(itemId, PrimaryCategoryKey, primaryCategory)
    if (secondaryCategory != null) {
        tx.set(itemId, SecondaryCategoryKey, secondaryCategory)
    } else if (tx.get(itemId, SecondaryCategoryKey).isPresent) {
        tx.delete(itemId, SecondaryCategoryKey)
    }
    tx.set(itemId, TitleKey, title)
    tx.set(itemId, PackageSizeKey, packageSize)
    if (barcode != null) {
        tx.set(itemId, BarcodeKey, barcode)
    } else if (tx.get(itemId, BarcodeKey).isPresent) {
        tx.delete(itemId, BarcodeKey)
    }
    tx.set(itemId, LastUsedAtKey, now())

    if (existingId != null) {
        tx.increment(itemId, UseCountKey, 1)
    } else {
        tx.set(itemId, UseCountKey, NewValue.counter(1))
        tx.set(itemId, StockCountKey, NewValue.counter(0))
    }
    tx.commit()
    return key
}

/** Adjusts [key]'s [PantryItem.stockCount] by [delta] (negative to decrement). No-op if [key] has no entry. */
fun adjustStock(doc: Document, key: String, delta: Int) {
    val tx = doc.startTransaction()
    val itemId = tx.itemsMapId()?.let { (tx.get(it, key).getOrNull() as? AmValue.Map)?.id }
    if (itemId != null) {
        tx.increment(itemId, StockCountKey, delta.toLong())
        tx.commit()
    } else {
        tx.rollback()
    }
}

/**
 * Best-effort absolute correction of [key]'s [PantryItem.stockCount] to [targetValue], implemented as a delta from
 * the locally-known value — see ADR-008 "Pantry stock count" for why this isn't safe under concurrent edits.
 * No-op if [key] has no entry.
 */
fun correctStock(doc: Document, key: String, targetValue: Int) {
    val current = doc.readPantryItem(key)?.stockCount ?: return
    adjustStock(doc, key, targetValue - current)
}

/** Removes [key]'s entry from the Pantry. No-op if [key] has no entry. */
fun deletePantryItem(doc: Document, key: String) {
    val tx = doc.startTransaction()
    val itemsId = tx.itemsMapId()
    if (itemsId != null && tx.get(itemsId, key).isPresent) {
        tx.delete(itemsId, key)
        tx.commit()
    } else {
        tx.rollback()
    }
}

private fun Read.itemsMapId(): ObjectId? = (get(ObjectId.ROOT, ItemsKey).getOrNull() as? AmValue.Map)?.id

private fun Read.readPantryItemAt(itemId: ObjectId): PantryItem = PantryItem(
    id = strField(itemId, IdKey),
    primaryCategory = strField(itemId, PrimaryCategoryKey),
    secondaryCategory = (get(itemId, SecondaryCategoryKey).getOrNull() as? AmValue.Str)?.value,
    title = strField(itemId, TitleKey),
    packageSize = strField(itemId, PackageSizeKey),
    barcode = (get(itemId, BarcodeKey).getOrNull() as? AmValue.Str)?.value,
    useCount = counterField(itemId, UseCountKey),
    lastUsedAt = strField(itemId, LastUsedAtKey),
    stockCount = counterField(itemId, StockCountKey),
)

private fun Read.strField(obj: ObjectId, key: String): String =
    (get(obj, key).getOrNull() as? AmValue.Str)?.value.orEmpty()

private fun Read.counterField(obj: ObjectId, key: String): Int =
    (get(obj, key).getOrNull() as? AmValue.Counter)?.value?.toInt() ?: 0
