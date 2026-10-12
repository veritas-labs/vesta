package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import kotlin.time.Clock
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.NewValue
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read
import uk.veritaslabs.vesta.ui.screens.MerchantLinkChange
import uk.veritaslabs.vesta.ui.screens.normalizeMerchantKeys

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
private const val MerchantKeysKey = "merchantKeys"

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
    /** Keys of the Home's merchants (ADR-026) the item is bought from, highest priority first; absent in older documents. */
    val merchantKeys: List<String> = emptyList(),
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

    tx.writeProductFields(itemId, key, primaryCategory, secondaryCategory, title, packageSize, barcode)
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

/**
 * Makes sure the Pantry has an entry for the product with the given [barcode]/[title] and returns its dedup key:
 * creates one (with `useCount = 1`, `stockCount = 0`) when absent, otherwise leaves its fields alone and, when
 * [countUse], increments `useCount` and refreshes `lastUsedAt`. With [refreshFields] an existing entry also takes the
 * given categories, title, package size and barcode (counters and `lastUsedAt` are untouched by that alone).
 */
fun ensurePantryItem(
    doc: Document,
    primaryCategory: String,
    secondaryCategory: String?,
    title: String,
    packageSize: String,
    barcode: String?,
    countUse: Boolean = true,
    refreshFields: Boolean = false,
    now: () -> String = { Clock.System.now().toString() },
): String {
    val key = pantryDedupKey(barcode, title)
    if (doc.readPantryItem(key) == null) {
        return upsertPantryItem(doc, primaryCategory, secondaryCategory, title, packageSize, barcode, now)
    }
    if (countUse || refreshFields) {
        val tx = doc.startTransaction()
        val itemId = tx.itemsMapId()?.let { (tx.get(it, key).getOrNull() as? AmValue.Map)?.id }
        if (itemId != null) {
            if (refreshFields) tx.writeProductFields(itemId, key, primaryCategory, secondaryCategory, title, packageSize, barcode)
            if (countUse) {
                tx.set(itemId, LastUsedAtKey, now())
                tx.increment(itemId, UseCountKey, 1)
            }
        }
        tx.commit()
    }
    return key
}

private fun org.automerge.Transaction.writeProductFields(
    itemId: ObjectId,
    key: String,
    primaryCategory: String,
    secondaryCategory: String?,
    title: String,
    packageSize: String,
    barcode: String?,
) {
    set(itemId, IdKey, key)
    set(itemId, PrimaryCategoryKey, primaryCategory)
    if (secondaryCategory != null) {
        set(itemId, SecondaryCategoryKey, secondaryCategory)
    } else if (get(itemId, SecondaryCategoryKey).isPresent) {
        delete(itemId, SecondaryCategoryKey)
    }
    set(itemId, TitleKey, title)
    set(itemId, PackageSizeKey, packageSize)
    if (barcode != null) {
        set(itemId, BarcodeKey, barcode)
    } else if (get(itemId, BarcodeKey).isPresent) {
        delete(itemId, BarcodeKey)
    }
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

/**
 * Replaces the ordered merchant keys of [key]'s entry with [merchantKeys] (blank and repeated keys dropped, first
 * position wins), writing only that field so it never clobbers a concurrent edit of the item's other fields. No-op if
 * [key] has no entry or already holds exactly that list.
 */
fun setPantryItemMerchants(doc: Document, key: String, merchantKeys: List<String>) {
    changePantryMerchants(doc, listOf(key)) { merchantKeys }
}

/**
 * Changes the merchant keys of every entry in [keys] by [change], all in one transaction; entries that don't exist or
 * would not change are left alone. Returns how many entries were changed.
 */
fun changePantryMerchants(doc: Document, keys: Collection<String>, change: MerchantLinkChange): Int =
    changePantryMerchants(doc, keys) { change.applyTo(it) }

private fun changePantryMerchants(doc: Document, keys: Collection<String>, next: (List<String>) -> List<String>): Int {
    val tx = doc.startTransaction()
    var changed = 0
    keys.distinct().forEach { key ->
        val itemId = tx.itemsMapId()?.let { (tx.get(it, key).getOrNull() as? AmValue.Map)?.id } ?: return@forEach
        val current = tx.stringList(itemId, MerchantKeysKey)
        val updated = normalizeMerchantKeys(next(current))
        if (updated != current) {
            val listId = tx.set(itemId, MerchantKeysKey, ObjectType.LIST)
            updated.forEachIndexed { index, merchant -> tx.insert(listId, index.toLong(), merchant) }
            changed++
        }
    }
    if (changed > 0) tx.commit() else tx.rollback()
    return changed
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

/**
 * Whether this document holds a Pantry's root maps. A Home that came from another device starts with an empty
 * placeholder (see `storeEmptyPantry`) that is not seeded until the creator's document arrives; an edit made on it would
 * create root maps of its own that compete with the creator's, and one side's items would be lost on merge.
 */
fun Read.isPantrySeeded(): Boolean = itemsMapId() != null

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
    merchantKeys = stringList(itemId, MerchantKeysKey),
)

private fun Read.stringList(obj: ObjectId, key: String): List<String> {
    val listId = (get(obj, key).getOrNull() as? AmValue.List)?.id ?: return emptyList()
    return listItems(listId).getOrNull()?.mapNotNull { (it as? AmValue.Str)?.value }.orEmpty()
}

private fun Read.strField(obj: ObjectId, key: String): String =
    (get(obj, key).getOrNull() as? AmValue.Str)?.value.orEmpty()

private fun Read.counterField(obj: ObjectId, key: String): Int =
    (get(obj, key).getOrNull() as? AmValue.Counter)?.value?.toInt() ?: 0
