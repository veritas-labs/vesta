package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.automerge.Document
import uk.veritaslabs.vesta.ui.screens.MediaKind
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeMedia
import uk.veritaslabs.vesta.ui.screens.RecipeStep
import uk.veritaslabs.vesta.ui.screens.RecipeType

class CookbookDocumentTest {

    private val pie = Recipe(
        title = "Shepherd's Pie",
        description = "Lamb under mash",
        type = RecipeType.Dinner,
        servings = 4,
        prepMinutes = 20,
        cookMinutes = 50,
        restMinutes = 10,
        rating = 4,
        favourite = true,
        media = listOf(RecipeMedia("file:///pie.jpg", MediaKind.Photo, "Fresh"), RecipeMedia("https://x/pie.mp4", MediaKind.Video)),
        ingredients = listOf(RecipeIngredient("Lamb mince", 0.5, "kg", "lean"), RecipeIngredient("Thyme")),
        steps = listOf(RecipeStep("a", "Boil", minutes = 20), RecipeStep("b", "Bake", after = listOf("a"))),
        tags = listOf("Comfort", "comfort", " Winter "),
        source = "Gran",
        notes = "Better next day",
    )

    @Test
    fun anAddedRecipeReadsBackWithEveryField() {
        // Arrange
        val doc = Document()

        // Act
        addRecipe(doc, "r1", pie)
        val read = doc.readRecipe("r1")

        // Assert
        assertEquals(pie.copy(key = "r1", tags = listOf("Comfort", "Winter")), read)
    }

    @Test
    fun anUnsetQuantityAndStepTimeStayAbsent() {
        // Arrange
        val doc = Document()

        // Act
        addRecipe(doc, "r1", pie)
        val read = doc.readRecipe("r1")!!

        // Assert
        assertNull(read.ingredients[1].quantity)
        assertNull(read.steps[1].minutes)
    }

    @Test
    fun recipesAreListedByTitleIgnoringCase() {
        // Arrange
        val doc = Document()
        addRecipe(doc, "1", Recipe(title = "zucchini bake"))
        addRecipe(doc, "2", Recipe(title = "Apple crumble"))

        // Act
        val titles = doc.readCookbook().map { it.title }

        // Assert
        assertEquals(listOf("Apple crumble", "zucchini bake"), titles)
    }

    @Test
    fun updatingReplacesEveryFieldIncludingShortenedLists() {
        // Arrange
        val doc = Document()
        addRecipe(doc, "r1", pie)

        // Act
        val updated = updateRecipe(doc, "r1", Recipe(title = "Cottage Pie", ingredients = listOf(RecipeIngredient("Beef"))))
        val read = doc.readRecipe("r1")!!

        // Assert
        assertTrue(updated)
        assertEquals("Cottage Pie", read.title)
        assertEquals(listOf("Beef"), read.ingredients.map { it.name })
        assertEquals(emptyList(), read.steps)
        assertEquals(emptyList(), read.media)
        assertFalse(read.favourite)
    }

    @Test
    fun updatingARecipeThatIsNotThereChangesNothing() {
        // Arrange
        val doc = Document()

        // Act
        val updated = updateRecipe(doc, "ghost", pie)

        // Assert
        assertFalse(updated)
        assertEquals(emptyList(), doc.readCookbook())
    }

    @Test
    fun ratingAndFavouriteWriteOnlyTheirOwnField() {
        // Arrange
        val doc = Document()
        addRecipe(doc, "r1", pie.copy(rating = 1, favourite = false))

        // Act
        setRecipeRating(doc, "r1", 5)
        setRecipeFavourite(doc, "r1", true)
        val read = doc.readRecipe("r1")!!

        // Assert
        assertEquals(pie.copy(key = "r1", rating = 5, favourite = true, tags = listOf("Comfort", "Winter")), read)
    }

    @Test
    fun aRatingIsClampedToZeroToFive() {
        // Arrange
        val doc = Document()
        addRecipe(doc, "r1", pie)

        // Act
        setRecipeRating(doc, "r1", 9)
        val high = doc.readRecipe("r1")!!.rating
        setRecipeRating(doc, "r1", -3)
        val low = doc.readRecipe("r1")!!.rating

        // Assert
        assertEquals(5, high)
        assertEquals(0, low)
    }

    @Test
    fun removingARecipeDeletesIt() {
        // Arrange
        val doc = Document()
        addRecipe(doc, "r1", pie)
        addRecipe(doc, "r2", Recipe(title = "Soup"))

        // Act
        removeRecipe(doc, "r1")

        // Assert
        assertEquals(listOf("Soup"), doc.readCookbook().map { it.title })
        assertNull(doc.readRecipe("r1"))
    }

    @Test
    fun recipesAddedOnTwoDevicesToAnEmptyDocumentMergeWithoutLoss() {
        // Arrange — two devices that each started from their own empty document, as paired devices do
        val a = Document()
        val b = Document()
        addRecipe(a, "from-a", Recipe(title = "A's recipe"))
        addRecipe(b, "from-b", Recipe(title = "B's recipe"))

        // Act
        a.merge(b)

        // Assert
        assertEquals(listOf("A's recipe", "B's recipe"), a.readCookbook().map { it.title })
    }

    @Test
    fun aSavedDocumentReloadsWithItsRecipes() {
        // Arrange
        val doc = Document()
        addRecipe(doc, "r1", pie)

        // Act
        val reloaded = Document.load(doc.save())

        // Assert
        assertEquals("Shepherd's Pie", reloaded.readRecipe("r1")?.title)
    }
}
