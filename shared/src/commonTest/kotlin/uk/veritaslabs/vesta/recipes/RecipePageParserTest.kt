package uk.veritaslabs.vesta.recipes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeType

class RecipePageParserTest {

    private fun page(json: String) = """<html><head><script type="application/ld+json">$json</script></head><body></body></html>"""

    private val soup = """
        {"@context":"https://schema.org","@type":"Recipe","name":"Tomato &amp; basil soup","description":"<p>Quick &amp; easy.</p>",
         "recipeYield":"Serves 6","prepTime":"PT15M","cookTime":"PT30M","totalTime":"PT1H",
         "recipeCategory":["Soup"],"keywords":"vegan, Easy, vegan",
         "image":["/img/soup.jpg"],
         "recipeIngredient":["1 1/2 cups plain flour, sifted","2 tomatoes","salt to taste","½ tsp pepper"],
         "recipeInstructions":[{"@type":"HowToStep","text":"Chop the onion."},{"@type":"HowToStep","text":"Simmer for 30 minutes."}]}
    """

    @Test
    fun `reads the recipe fields from the pages JSON-LD`() {
        // Arrange
        val html = page(soup)

        // Act
        val imported = assertNotNull(parseRecipePage(html, "https://example.com/recipes/soup?x=1"))

        // Assert
        val recipe = imported.recipe
        assertEquals("Tomato & basil soup", recipe.title)
        assertEquals("Quick & easy.", recipe.description)
        assertEquals(6, recipe.servings)
        assertEquals(15, recipe.prepMinutes)
        assertEquals(30, recipe.cookMinutes)
        assertEquals(15, recipe.restMinutes)
        assertEquals(RecipeType.Starter, recipe.type)
        assertEquals(listOf("vegan", "Easy"), recipe.tags)
        assertEquals("https://example.com/recipes/soup?x=1", recipe.source)
        assertEquals("https://example.com/img/soup.jpg", imported.photoUrl)
    }

    @Test
    fun `splits ingredient lines into quantity unit name and note`() {
        // Arrange
        val html = page(soup)

        // Act
        val ingredients = assertNotNull(parseRecipePage(html, "https://example.com/")).recipe.ingredients

        // Assert
        assertEquals(
            listOf(
                RecipeIngredient("plain flour", 1.5, "cups", "sifted"),
                RecipeIngredient("tomatoes", 2.0),
                RecipeIngredient("salt to taste"),
                RecipeIngredient("pepper", 0.5, "tsp"),
            ),
            ingredients,
        )
    }

    @Test
    fun `chains the steps one after another`() {
        // Arrange
        val html = page(soup)

        // Act
        val steps = assertNotNull(parseRecipePage(html, "https://example.com/")).recipe.steps

        // Assert
        assertEquals(listOf("Chop the onion.", "Simmer for 30 minutes."), steps.map { it.text })
        assertEquals(emptyList(), steps[0].after)
        assertEquals(listOf(steps[0].id), steps[1].after)
    }

    @Test
    fun `finds the recipe inside a graph and flattens sections of steps`() {
        // Arrange
        val html = page(
            """{"@context":"https://schema.org","@graph":[{"@type":"WebSite","name":"Site"},
            {"@type":["Recipe","Thing"],"name":"Pie","recipeInstructions":[
              {"@type":"HowToSection","name":"Pastry","itemListElement":[{"@type":"HowToStep","text":"Rub in butter."}]},
              {"@type":"HowToSection","name":"Filling","itemListElement":[{"@type":"HowToStep","text":"Stew the apples."},{"@type":"HowToStep","text":"Bake."}]}]}]}""",
        )

        // Act
        val recipe = assertNotNull(parseRecipePage(html, "https://example.com/")).recipe

        // Assert
        assertEquals("Pie", recipe.title)
        assertEquals(listOf("Rub in butter.", "Stew the apples.", "Bake."), recipe.steps.map { it.text })
    }

    @Test
    fun `splits instructions given as one string into a step per line`() {
        // Arrange
        val html = page("""{"@type":"Recipe","name":"Toast","recipeInstructions":"Slice the bread.<br>Toast it.\nButter it."}""")

        // Act
        val recipe = assertNotNull(parseRecipePage(html, "https://example.com/")).recipe

        // Assert
        assertEquals(listOf("Slice the bread.", "Toast it.", "Butter it."), recipe.steps.map { it.text })
    }

    @Test
    fun `a page that gives only a total time counts it as cooking time`() {
        // Arrange
        val html = page("""{"@type":"Recipe","name":"Stew","totalTime":"PT2H30M","recipeYield":4}""")

        // Act
        val recipe = assertNotNull(parseRecipePage(html, "https://example.com/")).recipe

        // Assert
        assertEquals(150, recipe.cookMinutes)
        assertEquals(0, recipe.restMinutes)
        assertEquals(4, recipe.servings)
    }

    @Test
    fun `skips scripts that are not recipes or not JSON and finds a later one`() {
        // Arrange
        val html = page("{ not json") + page("""{"@type":"Organization","name":"Acme"}""") + page("""{"@type":"Recipe","name":"Jam"}""")

        // Act
        val recipe = assertNotNull(parseRecipePage(html, "https://example.com/")).recipe

        // Assert
        assertEquals("Jam", recipe.title)
    }

    @Test
    fun `a page with no recipe or a recipe without a title imports nothing`() {
        // Arrange / Act / Assert
        assertNull(parseRecipePage("<html><body>Hello</body></html>", "https://example.com/"))
        assertNull(parseRecipePage(page("""{"@type":"Recipe","name":"  "}"""), "https://example.com/"))
    }

    @Test
    fun `reads ISO durations`() {
        // Arrange / Act / Assert
        assertEquals(90, durationMinutes("PT1H30M"))
        assertEquals(45, durationMinutes("pt45m"))
        assertEquals(1440, durationMinutes("P1D"))
        assertNull(durationMinutes("about an hour"))
    }

    @Test
    fun `reads ranges and attached units in ingredient lines`() {
        // Arrange / Act
        val eggs = parseIngredientLine("2-3 large eggs")
        val chicken = parseIngredientLine("200g chicken breast")

        // Assert
        assertEquals(RecipeIngredient("large eggs", 2.0), eggs)
        assertEquals(RecipeIngredient("chicken breast", 200.0, "g"), chicken)
    }
}
