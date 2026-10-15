package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType

/** Convergence of [migratePantryCategories] when devices migrate before they sync (ADR-010 amendment, ADR-034 WP3). */
class PantryCategoriesMigrationTest {

    /** A Pantry document as the first release wrote it: the version 1 registry and no `categoriesVersion`. */
    private fun versionOneBytes(): ByteArray {
        val doc = Document()
        val tx = doc.startTransaction()
        tx.set(ObjectId.ROOT, "items", ObjectType.MAP)
        val categories = tx.set(ObjectId.ROOT, "categories", ObjectType.MAP)
        LegacyDefaultPantryCategoriesV1.forEach { category ->
            val id = tx.set(categories, categoryKey(category.name), ObjectType.MAP)
            tx.set(id, "name", category.name)
            val secondary = tx.set(id, "secondary", ObjectType.MAP)
            category.secondary.forEach { tx.set(secondary, categoryKey(it), it) }
        }
        tx.commit()
        return doc.save()
    }

    private fun Document.category(name: String) = readPantryCategories().firstOrNull { it.name == name }

    private fun Document.version() =
        (get(ObjectId.ROOT, "categoriesVersion").getOrNull() as? AmValue.Int)?.value?.toInt()

    @Test
    fun aCustomCategoryAddedBeforeBothDevicesMigrateSurvivesTheMerge() {
        // Arrange
        val a = Document.load(versionOneBytes())
        val b = a.fork()
        addPrimaryCategory(a, "Pet Food")
        addSecondaryCategory(a, "Bakery", "Sourdough")

        // Act
        migratePantryCategories(a)
        migratePantryCategories(b)
        val mergedIntoA = a.fork().also { it.merge(b) }
        val mergedIntoB = b.fork().also { it.merge(a) }

        // Assert
        listOf(mergedIntoA, mergedIntoB).forEach { merged ->
            assertNotNull(merged.category("Pet Food"), "custom primary lost")
            assertTrue("Sourdough" in merged.category("Bakery")!!.secondary, "custom secondary lost")
        }
    }

    @Test
    fun theMergeIsTheSameWhicheverWayRoundAndHoldsTheCurrentDefaults() {
        // Arrange
        val a = Document.load(versionOneBytes())
        val b = a.fork()
        addPrimaryCategory(b, "Pet Food")

        // Act
        migratePantryCategories(a)
        migratePantryCategories(b)
        val mergedIntoA = a.fork().also { it.merge(b) }
        val mergedIntoB = b.fork().also { it.merge(a) }

        // Assert
        assertEquals(mergedIntoA.readPantryCategories(), mergedIntoB.readPantryCategories())
        val expected = (DefaultPantryCategories + PantryCategory("Pet Food", emptyList())).map { it.name }
        assertEquals(expected.sortedBy { it.lowercase() }, mergedIntoA.readPantryCategories().map { it.name })
        assertEquals(2, mergedIntoA.version())
    }

    @Test
    fun threeReplicasThatEachAddSomethingAndMigrateConvergeWithEverythingKept() {
        // Arrange
        val a = Document.load(versionOneBytes())
        val b = a.fork()
        val c = a.fork()
        addPrimaryCategory(a, "Pet Food")
        addSecondaryCategory(b, "Drinks", "Kombucha")
        addPrimaryCategory(c, "Garden")

        // Act
        listOf(a, b, c).forEach(::migratePantryCategories)
        val ab = a.fork().also { it.merge(b) }.also { it.merge(c) }
        val ca = c.fork().also { it.merge(a) }.also { it.merge(b) }
        val bc = b.fork().also { it.merge(c) }.also { it.merge(a) }

        // Assert
        assertEquals(ab.readPantryCategories(), ca.readPantryCategories())
        assertEquals(ab.readPantryCategories(), bc.readPantryCategories())
        assertNotNull(ab.category("Pet Food"))
        assertNotNull(ab.category("Garden"))
        assertTrue("Kombucha" in ab.category("Drinks")!!.secondary)
        assertEquals(DefaultPantryCategories.size + 2, ab.readPantryCategories().size)
    }

    @Test
    fun aCategoryAddedAfterOneDeviceMigratedSurvivesTheOtherDevicesMigration() {
        // Arrange
        val a = Document.load(versionOneBytes())
        val b = a.fork()
        migratePantryCategories(a)
        addPrimaryCategory(a, "Pet Food")

        // Act
        migratePantryCategories(b)
        b.merge(a)
        a.merge(b)

        // Assert
        assertNotNull(b.category("Pet Food"))
        assertEquals(a.readPantryCategories(), b.readPantryCategories())
    }

    @Test
    fun migrationKeepsWhatTheHouseholdAddedAndRemovesOnlyDroppedDefaults() {
        // Arrange
        val doc = Document.load(versionOneBytes())
        addPrimaryCategory(doc, "Pet Food")
        addSecondaryCategory(doc, "Meat", "Biltong")
        addSecondaryCategory(doc, "Bakery", "Sourdough")

        // Act
        migratePantryCategories(doc)

        // Assert
        assertNull(doc.category("Fish"))
        assertNull(doc.category("Cooking Sauces"))
        assertEquals(listOf("Biltong"), doc.category("Meat")!!.secondary)
        assertNotNull(doc.category("Pet Food"))
        assertTrue("Sourdough" in doc.category("Bakery")!!.secondary)
        assertTrue("Cereal" !in doc.category("Pasta, Rice & Grains")!!.secondary)
        assertNotNull(doc.category("Meat & Fish"))
    }

    @Test
    fun aCurrentDefaultTheHouseholdHadDeletedComesBackOnceAndCustomEntriesStay() {
        // Arrange
        val doc = Document.load(versionOneBytes())
        deletePrimaryCategory(doc, "Household")
        addPrimaryCategory(doc, "Pet Food")

        // Act
        migratePantryCategories(doc)

        // Assert
        assertNotNull(doc.category("Pet Food"))
        assertNotNull(doc.category("Household"))
    }

    @Test
    fun migrationIsIdempotentAndASecondRunWritesNothing() {
        // Arrange
        val doc = Document.load(versionOneBytes())
        migratePantryCategories(doc)
        val categories = doc.readPantryCategories()
        val heads = doc.heads.toList()

        // Act
        migratePantryCategories(doc)

        // Assert
        assertEquals(categories, doc.readPantryCategories())
        assertEquals(heads, doc.heads.toList())
    }

    @Test
    fun migrationDoesNotReplaceTheCategoriesMap() {
        // Arrange
        val doc = Document.load(versionOneBytes())
        val before = (doc.get(ObjectId.ROOT, "categories").getOrNull() as AmValue.Map).id

        // Act
        migratePantryCategories(doc)

        // Assert
        assertEquals(before, (doc.get(ObjectId.ROOT, "categories").getOrNull() as AmValue.Map).id)
    }

    @Test
    fun aVersionTwoDocumentIsNotTouched() {
        // Arrange
        val doc = Document.load(newPantryDocument())
        deletePrimaryCategory(doc, "Pets")
        val heads = doc.heads.toList()

        // Act
        migratePantryCategories(doc)

        // Assert
        assertNull(doc.category("Pets"))
        assertEquals(heads, doc.heads.toList())
    }
}
