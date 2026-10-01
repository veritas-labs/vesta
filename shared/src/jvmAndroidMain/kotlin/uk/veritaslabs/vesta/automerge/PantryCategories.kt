package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read
import org.automerge.Transaction

private const val CategoriesKey = "categories"
private const val NameKey = "name"
private const val SecondaryKey = "secondary"

/** A primary category and its secondary categories, as registered in the Pantry document — see ADR-010. */
data class PantryCategory(val name: String, val secondary: List<String>)

/** The set a new Pantry document is seeded with; after that the document is the source of truth. */
val DefaultPantryCategories: List<PantryCategory> = listOf(
    PantryCategory("Fruit & Vegetables", listOf("Fruit", "Vegetables", "Salad", "Herbs")),
    PantryCategory("Meat", listOf("Beef", "Pork", "Poultry", "Lamb", "Sausages & Bacon")),
    PantryCategory("Fish", listOf("Fresh Fish", "Frozen Fish", "Tinned Fish")),
    PantryCategory("Dairy", listOf("Milk", "Cheese", "Yoghurt", "Butter & Spreads", "Eggs", "Cream")),
    PantryCategory("Bakery", listOf("Bread", "Rolls & Wraps", "Cakes & Pastries")),
    PantryCategory("Condiments", listOf("Ketchup", "Brown Sauce", "Mayonnaise", "Mustard", "Pickles")),
    PantryCategory("Cooking Sauces", listOf("Pasta Sauce", "Curry Sauce", "Stock & Gravy")),
    PantryCategory("Tins & Jars", listOf("Tinned Vegetables", "Tinned Tomatoes", "Beans & Pulses", "Soup")),
    PantryCategory("Pasta, Rice & Grains", listOf("Pasta", "Rice", "Noodles", "Cereal", "Oats")),
    PantryCategory("Baking", listOf("Flour", "Sugar", "Baking Ingredients")),
    PantryCategory("Herbs & Spices", listOf("Herbs", "Spices", "Salt & Pepper")),
    PantryCategory("Snacks", listOf("Crisps", "Biscuits", "Chocolate & Sweets", "Nuts")),
    PantryCategory("Frozen", listOf("Frozen Meals", "Frozen Vegetables", "Ice Cream", "Frozen Chips")),
    PantryCategory("Drinks", listOf("Tea & Coffee", "Juice", "Soft Drinks", "Water", "Alcohol")),
    PantryCategory("Household", listOf("Cleaning", "Laundry", "Paper Goods", "Toiletries")),
)

/**
 * Reads the registered categories, sorted by name (case-insensitively), each with its secondary categories sorted
 * the same way. A document with no registry yet (created before ADR-010) reads as [DefaultPantryCategories].
 */
fun Read.readPantryCategories(): List<PantryCategory> {
    val categoriesId = categoriesMapId() ?: return DefaultPantryCategories.sortedCategories()
    return mapEntries(categoriesId).getOrNull()
        ?.mapNotNull { (it.value as? AmValue.Map)?.id }
        ?.map { categoryId ->
            PantryCategory(
                name = (get(categoryId, NameKey).getOrNull() as? AmValue.Str)?.value.orEmpty(),
                secondary = (get(categoryId, SecondaryKey).getOrNull() as? AmValue.Map)?.id
                    ?.let { mapEntries(it).getOrNull() }
                    ?.mapNotNull { (it.value as? AmValue.Str)?.value }
                    .orEmpty(),
            )
        }
        ?.sortedCategories()
        .orEmpty()
}

/** Registers [name] as a primary category. No-op if it (by [categoryKey]) already is. */
fun addPrimaryCategory(doc: Document, name: String) {
    val tx = doc.startTransaction()
    tx.ensurePrimary(name)
    tx.commit()
}

/** Registers [secondary] under the primary category [primary], registering [primary] too if needed. */
fun addSecondaryCategory(doc: Document, primary: String, secondary: String) {
    val tx = doc.startTransaction()
    tx.ensureSecondary(primary, secondary)
    tx.commit()
}

/**
 * Registers [primary] and, when given, [secondary] under it — used when an item is saved with categories that
 * aren't registered yet. Leaves the document untouched (no commit) if both already are.
 */
fun ensurePantryCategories(doc: Document, primary: String, secondary: String?) {
    val tx = doc.startTransaction()
    if (secondary != null) tx.ensureSecondary(primary, secondary) else tx.ensurePrimary(primary)
    tx.commit()
}

/** Unregisters the primary category [name] and its secondaries. Items using it are untouched. */
fun deletePrimaryCategory(doc: Document, name: String) {
    val tx = doc.startTransaction()
    val categoriesId = tx.materializedCategoriesMapId()
    if (tx.get(categoriesId, categoryKey(name)).isPresent) tx.delete(categoriesId, categoryKey(name))
    tx.commit()
}

/** Unregisters [secondary] from the primary category [primary]. Items using it are untouched. */
fun deleteSecondaryCategory(doc: Document, primary: String, secondary: String) {
    val tx = doc.startTransaction()
    val categoryId = (tx.get(tx.materializedCategoriesMapId(), categoryKey(primary)).getOrNull() as? AmValue.Map)?.id
    val secondaryId = categoryId?.let { (tx.get(it, SecondaryKey).getOrNull() as? AmValue.Map)?.id }
    if (secondaryId != null && tx.get(secondaryId, categoryKey(secondary)).isPresent) {
        tx.delete(secondaryId, categoryKey(secondary))
    }
    tx.commit()
}

/** The key a category name is stored at: trimmed and lowercased, like [pantryDedupKey]'s title fallback. */
fun categoryKey(name: String): String = name.trim().lowercase()

private fun List<PantryCategory>.sortedCategories(): List<PantryCategory> =
    map { it.copy(secondary = it.secondary.sortedBy(String::lowercase)) }.sortedBy { it.name.lowercase() }

private fun Read.categoriesMapId(): ObjectId? = (get(ObjectId.ROOT, CategoriesKey).getOrNull() as? AmValue.Map)?.id

/** Writes [DefaultPantryCategories] into a fresh `categories` map and returns its id. */
internal fun Transaction.seedCategories(): ObjectId {
    val categoriesId = set(ObjectId.ROOT, CategoriesKey, ObjectType.MAP)
    DefaultPantryCategories.forEach { category ->
        val categoryId = set(categoriesId, categoryKey(category.name), ObjectType.MAP)
        set(categoryId, NameKey, category.name)
        val secondaryId = set(categoryId, SecondaryKey, ObjectType.MAP)
        category.secondary.forEach { set(secondaryId, categoryKey(it), it) }
    }
    return categoriesId
}

/** The `categories` map, first seeding the defaults if this document predates the registry. */
private fun Transaction.materializedCategoriesMapId(): ObjectId = categoriesMapId() ?: seedCategories()

private fun Transaction.ensurePrimary(name: String): ObjectId {
    val categoriesId = materializedCategoriesMapId()
    val existing = (get(categoriesId, categoryKey(name)).getOrNull() as? AmValue.Map)?.id
    if (existing != null) return existing
    val categoryId = set(categoriesId, categoryKey(name), ObjectType.MAP)
    set(categoryId, NameKey, name.trim())
    set(categoryId, SecondaryKey, ObjectType.MAP)
    return categoryId
}

private fun Transaction.ensureSecondary(primary: String, secondary: String) {
    val categoryId = ensurePrimary(primary)
    val secondaryId = (get(categoryId, SecondaryKey).getOrNull() as? AmValue.Map)?.id
        ?: set(categoryId, SecondaryKey, ObjectType.MAP)
    if (!get(secondaryId, categoryKey(secondary)).isPresent) set(secondaryId, categoryKey(secondary), secondary.trim())
}
