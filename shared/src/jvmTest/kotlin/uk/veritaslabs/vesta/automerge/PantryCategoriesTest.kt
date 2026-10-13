package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.automerge.Document

class PantryCategoriesTest {

    private fun newDoc() = Document.load(newPantryDocument())

    private fun Document.category(name: String) = readPantryCategories().firstOrNull { it.name == name }

    @Test
    fun newDocumentIsSeededWithTheDefaults() {
        // Arrange
        val doc = newDoc()

        // Act
        val categories = doc.readPantryCategories()

        // Assert
        assertEquals(DefaultPantryCategories.map { it.name }.sortedBy { it.lowercase() }, categories.map { it.name })
        assertTrue("Ketchup" in doc.category("Condiments & Spreads")!!.secondary)
    }

    @Test
    fun documentWithoutARegistryReadsAsTheDefaults() {
        // Arrange
        val doc = newDoc()
        val legacy = Document()
        legacy.startTransaction().also { it.set(org.automerge.ObjectId.ROOT, "items", org.automerge.ObjectType.MAP) }.commit()

        // Act
        val categories = legacy.readPantryCategories()

        // Assert
        assertEquals(doc.readPantryCategories(), categories)
    }

    @Test
    fun addedPrimaryAppearsAndDuplicatesByKeyAreIgnored() {
        // Arrange
        val doc = newDoc()

        // Act
        addPrimaryCategory(doc, "  Pet Food ")
        addPrimaryCategory(doc, "pet food")

        // Assert
        assertEquals(listOf(PantryCategory("Pet Food", emptyList())), doc.readPantryCategories().filter { it.name == "Pet Food" })
    }

    @Test
    fun addedSecondaryIsListedUnderItsPrimaryAndCreatesAMissingPrimary() {
        // Arrange
        val doc = newDoc()

        // Act
        addSecondaryCategory(doc, "Dairy & Eggs", "Oat Milk")
        addSecondaryCategory(doc, "Pet Food", "Cat")

        // Assert
        assertTrue("Oat Milk" in doc.category("Dairy & Eggs")!!.secondary)
        assertEquals(listOf("Cat"), doc.category("Pet Food")!!.secondary)
    }

    @Test
    fun deletingASecondaryLeavesItsSiblings() {
        // Arrange
        val doc = newDoc()

        // Act
        deleteSecondaryCategory(doc, "Dairy & Eggs", "milk")

        // Assert
        val secondary = doc.category("Dairy & Eggs")!!.secondary
        assertFalse("Milk" in secondary)
        assertTrue("Cheese" in secondary)
    }

    @Test
    fun deletingAPrimaryRemovesItWithItsSecondaries() {
        // Arrange
        val doc = newDoc()

        // Act
        deletePrimaryCategory(doc, "Dairy & Eggs")

        // Assert
        assertEquals(null, doc.category("Dairy & Eggs"))
        assertTrue(doc.category("Meat & Fish") != null)
    }

    @Test
    fun deletingAgainstALegacyDocumentMaterialisesTheDefaultsFirst() {
        // Arrange
        val legacy = Document()
        legacy.startTransaction().also { it.set(org.automerge.ObjectId.ROOT, "items", org.automerge.ObjectType.MAP) }.commit()

        // Act
        deletePrimaryCategory(legacy, "Dairy & Eggs")

        // Assert
        assertEquals(DefaultPantryCategories.size - 1, legacy.readPantryCategories().size)
    }

    @Test
    fun concurrentAddsOfTheSameNameMergeIntoOneCategory() {
        // Arrange
        val a = newDoc()
        val b = a.fork()

        // Act
        addPrimaryCategory(a, "Pet Food")
        addPrimaryCategory(b, "Pet Food")
        addSecondaryCategory(a, "Pet Food", "Cat")
        addSecondaryCategory(b, "Dairy & Eggs", "Oat Milk")
        a.merge(b)

        // Assert
        assertEquals(1, a.readPantryCategories().count { it.name == "Pet Food" })
        assertTrue("Oat Milk" in a.category("Dairy & Eggs")!!.secondary)
    }

    @Test
    fun ensuringCategoriesRegistersOnlyWhatIsMissing() {
        // Arrange
        val doc = newDoc()

        // Act
        ensurePantryCategories(doc, "Condiments & Spreads", "Ketchup")
        ensurePantryCategories(doc, "Pet Food", "Cat")

        // Assert
        assertEquals(DefaultPantryCategories.size + 1, doc.readPantryCategories().size)
        assertEquals(1, doc.category("Condiments & Spreads")!!.secondary.count { it == "Ketchup" })
        assertEquals(listOf("Cat"), doc.category("Pet Food")!!.secondary)
    }

    @Test
    fun migrationReplacesAnOlderSeededRegistryWithTheCurrentDefaultsOnce() {
        // Arrange
        val doc = Document()
        doc.startTransaction().also {
            val categories = it.set(org.automerge.ObjectId.ROOT, "categories", org.automerge.ObjectType.MAP)
            val old = it.set(categories, "meat", org.automerge.ObjectType.MAP)
            it.set(old, "name", "Meat")
        }.commit()

        // Act
        migratePantryCategories(doc)
        addPrimaryCategory(doc, "Custom")
        migratePantryCategories(doc)

        // Assert
        assertEquals(null, doc.category("Meat"))
        assertTrue(doc.category("Meat & Fish") != null)
        assertTrue(doc.category("Custom") != null)
    }

    @Test
    fun migrationLeavesADocumentWithoutARegistryAlone() {
        // Arrange
        val doc = Document()

        // Act
        migratePantryCategories(doc)

        // Assert
        assertEquals(0, doc.heads.size)
    }
}
