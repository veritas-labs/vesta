package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.kits.blobs.BlobReference
import uk.veritaslabs.kits.blobs.MediaKind
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeMedia

class CookbookBlobReferencesTest {

    private val database = openInMemoryDatabase()
    private val cookbooks = CookbookSources(database)
    private val references = CookbookBlobReferences(cookbooks)

    private fun hashOf(letter: String) = letter.repeat(64)

    private fun blob(hash: String) = BlobReference(hash, 10L, "image/jpeg", MediaKind.Photo, 4, 3, "blur")

    private fun recipeWith(vararg hashes: String) =
        Recipe(title = "Pie", media = hashes.map { RecipeMedia("", blob = blob(hashOf(it))) })

    private suspend fun addHome(id: String) = database.homeQueries.insert(id, "Home", "family", 1_000L)

    @Test
    fun aHomeThatDoesNotExistHasNoReadableReferences() = runBlocking {
        // Arrange
        val homeId = "missing"

        // Act
        val result = references.references(homeId)

        // Assert
        assertNull(result)
    }

    @Test
    fun aHomeWithNoRecipesHasAnEmptyReferenceSetNotNull() = runBlocking {
        // Arrange
        addHome("h1")

        // Act
        val result = references.references("h1")

        // Assert
        assertEquals(emptySet(), result)
    }

    @Test
    fun theReferencesAreTheDistinctPhotosOfTheHomesRecipes() = runBlocking {
        // Arrange
        addHome("h1")
        val source = cookbooks.forHome("h1")!!
        source.save(recipeWith("a", "b"))
        source.save(recipeWith("b", "c"))

        // Act
        val result = references.references("h1")

        // Assert
        assertEquals(setOf("a", "b", "c").map(::hashOf).toSet(), result?.map { it.hash }?.toSet())
        assertEquals(3, result?.size)
    }

    @Test
    fun aReferenceBelongsOnlyToTheHomeWhoseRecipeHoldsIt() = runBlocking {
        // Arrange
        addHome("h1")
        addHome("h2")
        cookbooks.forHome("h1")!!.save(recipeWith("a"))

        // Act
        val other = references.references("h2")

        // Assert
        assertEquals(emptySet(), other)
    }

    @Test
    fun localChangesAreTheCookbooksLocalChangesNamingTheHome() = runBlocking {
        // Arrange
        addHome("h1")
        val source = cookbooks.forHome("h1")!!
        val seen = kotlinx.coroutines.CompletableDeferred<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { seen.complete(references.localChanges.first()) }

        // Act
        source.save(recipeWith("a"))
        val homeId = withTimeout(5_000) { seen.await() }
        job.cancel()

        // Assert
        assertEquals("h1", homeId)
    }

    @Test
    fun watchingACookbookCallsBackOnEachRecipeChange() = runBlocking {
        // Arrange
        addHome("h1")
        val source = cookbooks.forHome("h1")!!
        var calls = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) { references.watch("h1") { calls++ } }
        val before = calls

        // Act
        source.save(recipeWith("a"))
        withTimeout(5_000) { while (calls <= before) kotlinx.coroutines.yield() }
        job.cancel()

        // Assert
        assertTrue(calls > before)
    }

    @Test
    fun watchingAHomeWithNoCookbookReturnsAtOnce() = runBlocking {
        // Arrange
        var calls = 0

        // Act
        references.watch("missing") { calls++ }

        // Assert
        assertEquals(0, calls)
    }
}
