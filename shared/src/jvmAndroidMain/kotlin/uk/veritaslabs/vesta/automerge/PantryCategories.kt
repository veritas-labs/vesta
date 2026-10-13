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
private const val CategoriesVersionKey = "categoriesVersion"

/** Bumped whenever [DefaultPantryCategories] changes; a document seeded with an older set is reset to the new one. */
private const val CategoriesVersion = 2

/** A primary category and its secondary categories, as registered in the Pantry document — see ADR-010. */
data class PantryCategory(val name: String, val secondary: List<String>)

/** The set a new Pantry document is seeded with; after that the document is the source of truth. */
val DefaultPantryCategories: List<PantryCategory> = listOf(
    PantryCategory("Fruit & Vegetables", listOf("Fruit", "Vegetables", "Salad & Leaves", "Potatoes & Roots", "Fresh Herbs")),
    PantryCategory("Bakery", listOf("Bread", "Rolls & Wraps", "Flatbreads & Tortillas", "Cakes & Pastries", "Breakfast Bakery")),
    PantryCategory("Meat & Fish", listOf("Beef", "Pork", "Poultry", "Lamb", "Sausages & Bacon", "Fresh Fish", "Minced & Diced")),
    PantryCategory("Deli & Chilled Ready", listOf("Cold Cuts & Cooked Meat", "Cheese (deli)", "Hummus & Dips", "Ready Meals", "Pies & Quiches")),
    PantryCategory("Dairy & Eggs", listOf("Milk", "Cheese", "Yoghurt", "Butter & Spreads", "Eggs", "Cream", "Dairy Alternatives")),
    PantryCategory("Frozen", listOf("Frozen Meals", "Frozen Vegetables & Fruit", "Frozen Fish & Seafood", "Ice Cream", "Frozen Chips & Potatoes", "Frozen Bakery")),
    PantryCategory("Pasta, Rice & Grains", listOf("Pasta", "Rice", "Noodles", "Other Grains", "Dried Pulses")),
    PantryCategory("Tins & Jars", listOf("Tinned Tomatoes", "Tinned Vegetables", "Beans & Pulses", "Soup", "Tinned Fish")),
    PantryCategory("Cooking Ingredients", listOf("Oils & Vinegars", "Cooking Sauces", "Stock & Gravy", "Pasta Sauce", "Curry & World Sauces")),
    PantryCategory("Condiments & Spreads", listOf("Ketchup", "Mayonnaise", "Mustard", "Pickles & Chutneys", "Salad Dressings", "Spreads")),
    PantryCategory("Herbs & Spices", listOf("Dried Herbs", "Spices", "Salt & Pepper", "Seasoning Mixes")),
    PantryCategory("Baking", listOf("Flour", "Sugar", "Baking Ingredients", "Mixes & Decorations")),
    PantryCategory("Breakfast", listOf("Cereal", "Oats & Hot Cereals", "Granola & Muesli", "Breakfast Bars")),
    PantryCategory("Snacks", listOf("Crisps", "Biscuits & Crackers", "Chocolate & Sweets", "Nuts & Seeds", "Dried Fruit")),
    PantryCategory("Drinks", listOf("Tea & Coffee", "Juice", "Soft Drinks", "Water", "Alcohol")),
    PantryCategory("World Foods", listOf("Asian", "Indian", "Mexican & Latin", "Middle Eastern", "Italian Specialties")),
    PantryCategory("Household", listOf("Cleaning", "Laundry", "Paper Goods", "Kitchen Foil & Bags")),
    PantryCategory("Health & Beauty", listOf("Toiletries", "Hair & Body", "Oral Care", "First Aid & Vitamins")),
    PantryCategory("Baby", listOf("Nappies", "Formula & Food", "Wipes & Toiletries")),
    PantryCategory("Pets", listOf("Dog Food", "Cat Food", "Other Pet")),
    PantryCategory("Other / Uncategorised", listOf()),
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
    set(ObjectId.ROOT, CategoriesVersionKey, CategoriesVersion)
    return categoriesId
}

/**
 * Replaces the registered categories with [DefaultPantryCategories] if they were seeded from an older set (ADR-010
 * amendment). Destructive by design: custom categories are dropped, though items keep their category text. A
 * document with no registry yet — an empty one waiting to be filled from another device — is left alone.
 */
fun migratePantryCategories(doc: Document) {
    val tx = doc.startTransaction()
    val version = (tx.get(ObjectId.ROOT, CategoriesVersionKey).getOrNull() as? AmValue.Int)?.value?.toInt() ?: 0
    if (tx.categoriesMapId() != null && version < CategoriesVersion) tx.seedCategories()
    tx.commit()
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
