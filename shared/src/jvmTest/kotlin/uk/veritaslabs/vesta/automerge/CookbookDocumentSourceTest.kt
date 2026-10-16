package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.screens.Recipe

class CookbookDocumentSourceTest {

    private fun newSource(persist: suspend (ByteArray) -> Unit = {}, onLocalChange: () -> Unit = {}) =
        CookbookDocumentSource(Document(), persist, onLocalChange)

    @Test
    fun aSavedNewRecipeIsPublishedUnderTheKeyReturned() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        val key = source.save(Recipe(title = "Soup"))
        val recipes = source.recipes.first()

        // Assert
        assertEquals(listOf(key to "Soup"), recipes.map { it.key to it.title })
    }

    @Test
    fun savingTwoNewRecipesGivesThemDifferentKeys() = runBlocking {
        // Arrange
        val source = newSource()

        // Act
        val first = source.save(Recipe(title = "Soup"))
        val second = source.save(Recipe(title = "Soup"))

        // Assert
        assertNotEquals(first, second)
        assertEquals(2, source.recipes.first().size)
    }

    @Test
    fun savingARecipeWithAKeyReplacesThatRecipe() = runBlocking {
        // Arrange
        val source = newSource()
        val key = source.save(Recipe(title = "Soup", rating = 2))

        // Act
        source.save(Recipe(key = key, title = "Tomato Soup", rating = 2))
        val recipes = source.recipes.first()

        // Assert
        assertEquals(listOf("Tomato Soup"), recipes.map { it.title })
    }

    @Test
    fun ratingFavouritingAndRemovingArePublished() = runBlocking {
        // Arrange
        val source = newSource()
        val key = source.save(Recipe(title = "Soup"))
        val other = source.save(Recipe(title = "Pie"))

        // Act
        source.setRating(key, 4)
        source.setFavourite(key, true)
        source.remove(other)
        val recipes = source.recipes.first()

        // Assert
        assertEquals(listOf(Triple("Soup", 4, true)), recipes.map { Triple(it.title, it.rating, it.favourite) })
    }

    @Test
    fun everyLocalEditIsPersistedAndReported() = runBlocking {
        // Arrange
        var saves = 0
        var changes = 0
        val source = newSource(persist = { saves++ }, onLocalChange = { changes++ })

        // Act
        val key = source.save(Recipe(title = "Soup"))
        source.setRating(key, 3)

        // Assert
        assertEquals(2, saves)
        assertEquals(2, changes)
    }

    @Test
    fun aChangeSyncedInFromAnotherDeviceIsPublishedButNotReportedAsLocal() = runBlocking {
        // Arrange
        var changes = 0
        val ours = newSource(onLocalChange = { changes++ })
        val theirs = newSource()
        theirs.save(Recipe(title = "Their pie"))

        // Act
        ours.applyChanges(theirs.changesSince(ByteArray(0)))

        // Assert
        assertEquals(listOf("Their pie"), ours.recipes.first().map { it.title })
        assertEquals(0, changes)
    }

    @Test
    fun reopeningAHomesCookbookRestoresItsRecipes() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        openCookbookDocumentSource(database, "home-1").save(Recipe(title = "Soup"))

        // Act
        val reopened = openCookbookDocumentSource(database, "home-1")

        // Assert
        assertEquals(listOf("Soup"), reopened.recipes.first().map { it.title })
    }

    @Test
    fun aNewCookbookIsStoredImmediatelyAndRemovedWithItsHome() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)

        // Act
        openCookbookDocumentSource(database, "home-1")
        val before = database.automergeDocumentQueries.selectById(cookbookDocumentId("home-1")).awaitAsOneOrNull()
        database.homeQueries.deleteById("home-1")
        val after = database.automergeDocumentQueries.selectById(cookbookDocumentId("home-1")).awaitAsOneOrNull()

        // Assert
        assertEquals("cookbook", before?.feature)
        assertNull(after)
    }

    @Test
    fun theSharedSourceForAHomeIsOneInstanceAndNoneExistsForAnUnknownHome() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val sources = CookbookSources(database)

        // Act
        val first = sources.forHome("home-1")
        val second = sources.forHome("home-1")
        val unknown = sources.forHome("nope")

        // Assert
        assertEquals(true, first === second)
        assertNull(unknown)
    }
}
