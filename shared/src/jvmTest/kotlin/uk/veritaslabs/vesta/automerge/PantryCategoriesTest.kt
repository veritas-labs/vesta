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
        assertTrue("Ketchup" in doc.category("Condiments")!!.secondary)
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
        assertEquals(listOf(PantryCategory("Pet Food", emptyList())), doc.readPantryCategories().filter { it.name.contains("Pet", true) })
    }

    @Test
    fun addedSecondaryIsListedUnderItsPrimaryAndCreatesAMissingPrimary() {
        // Arrange
        val doc = newDoc()

        // Act
        addSecondaryCategory(doc, "Dairy", "Oat Milk")
        addSecondaryCategory(doc, "Pet Food", "Cat")

        // Assert
        assertTrue("Oat Milk" in doc.category("Dairy")!!.secondary)
        assertEquals(listOf("Cat"), doc.category("Pet Food")!!.secondary)
    }

    @Test
    fun deletingASecondaryLeavesItsSiblings() {
        // Arrange
        val doc = newDoc()

        // Act
        deleteSecondaryCategory(doc, "Dairy", "milk")

        // Assert
        val secondary = doc.category("Dairy")!!.secondary
        assertFalse("Milk" in secondary)
        assertTrue("Cheese" in secondary)
    }

    @Test
    fun deletingAPrimaryRemovesItWithItsSecondaries() {
        // Arrange
        val doc = newDoc()

        // Act
        deletePrimaryCategory(doc, "Dairy")

        // Assert
        assertEquals(null, doc.category("Dairy"))
        assertTrue(doc.category("Meat") != null)
    }

    @Test
    fun deletingAgainstALegacyDocumentMaterialisesTheDefaultsFirst() {
        // Arrange
        val legacy = Document()
        legacy.startTransaction().also { it.set(org.automerge.ObjectId.ROOT, "items", org.automerge.ObjectType.MAP) }.commit()

        // Act
        deletePrimaryCategory(legacy, "Dairy")

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
        addSecondaryCategory(b, "Dairy", "Oat Milk")
        a.merge(b)

        // Assert
        assertEquals(1, a.readPantryCategories().count { it.name == "Pet Food" })
        assertTrue("Oat Milk" in a.category("Dairy")!!.secondary)
    }

    @Test
    fun ensuringCategoriesRegistersOnlyWhatIsMissing() {
        // Arrange
        val doc = newDoc()

        // Act
        ensurePantryCategories(doc, "Condiments", "Ketchup")
        ensurePantryCategories(doc, "Pet Food", "Cat")

        // Assert
        assertEquals(DefaultPantryCategories.size + 1, doc.readPantryCategories().size)
        assertEquals(1, doc.category("Condiments")!!.secondary.count { it == "Ketchup" })
        assertEquals(listOf("Cat"), doc.category("Pet Food")!!.secondary)
    }
}
