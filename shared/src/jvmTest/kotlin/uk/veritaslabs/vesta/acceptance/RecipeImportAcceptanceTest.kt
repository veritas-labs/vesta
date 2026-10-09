package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.recipes.HttpRecipeImporter
import uk.veritaslabs.vesta.recipes.LocalRecipeImporter
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeEditScreen
import uk.veritaslabs.vesta.ui.screens.RecipeEditTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Importing a recipe from a web link (add-recipe form's overflow menu).
 *
 * Acceptance criteria:
 * 1. On the add-recipe form, the overflow menu offers "Import from URL".
 * 2. Giving it the address of a page with a recipe fills in the title, servings, times, tags, ingredients and method, and the source is that address.
 * 3. The imported form can be edited and saved like any other.
 * 4. A page with no recipe, or an address that can't be reached, shows an error and leaves what was typed alone.
 *
 * The web server is a real one on localhost: the only thing faked is the internet.
 */
@OptIn(ExperimentalTestApi::class)
class RecipeImportAcceptanceTest {

    private lateinit var server: HttpServer

    private val recipePage = """
        <html><head><script type="application/ld+json">
        {"@context":"https://schema.org","@type":"Recipe","name":"Pancakes","recipeYield":"4 servings","prepTime":"PT10M","cookTime":"PT20M",
         "keywords":"breakfast, easy","recipeIngredient":["2 cups flour","2 eggs"],
         "recipeInstructions":[{"@type":"HowToStep","text":"Mix."},{"@type":"HowToStep","text":"Fry."}]}
        </script></head><body>Pancakes</body></html>
    """.trimIndent()

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/pancakes") { exchange ->
            val body = recipePage.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/plain") { exchange ->
            val body = "<html><body>No recipe here</body></html>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterTest
    fun stopServer() = server.stop(0)

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @Test
    fun importingARecipeFromItsPageFillsTheFormAndItCanBeSaved() = runComposeUiTest {
        // Arrange — Given I am adding a recipe and my friend's pancake recipe is on a web page
        var saved: Recipe? = null
        setContent {
            VestaTheme { CompositionLocalProvider(LocalRecipeImporter provides HttpRecipeImporter()) { RecipeEditScreen(recipe = null, onSave = { saved = it }) } }
        }

        // Act — When I choose Import from URL, give the page's address and save the filled-in form
        onNodeWithTag(RecipeEditTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportMenuItem).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportUrlField).performTextInput(url("/pancakes"))
        onNodeWithTag(RecipeEditTestTags.ImportConfirmButton).performClick()
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithTag(RecipeEditTestTags.TitleField).assertTextContains("Pancakes") }.isSuccess }
        onNodeWithTag(RecipeEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert — Then the saved recipe is the page's, with its address as the source
        val recipe = saved!!
        assertEquals("Pancakes", recipe.title)
        assertEquals(4, recipe.servings)
        assertEquals(10, recipe.prepMinutes)
        assertEquals(20, recipe.cookMinutes)
        assertEquals(listOf("breakfast", "easy"), recipe.tags)
        assertEquals(listOf("flour", "eggs"), recipe.ingredients.map { it.name })
        assertEquals(listOf("Mix.", "Fry."), recipe.steps.map { it.text })
        assertEquals(url("/pancakes"), recipe.source)
    }

    @Test
    fun aPageWithoutARecipeShowsAnErrorAndKeepsWhatIHadTyped() = runComposeUiTest {
        // Arrange — Given I have typed a title and a web page that holds no recipe
        setContent { VestaTheme { CompositionLocalProvider(LocalRecipeImporter provides HttpRecipeImporter()) { RecipeEditScreen(recipe = null) } } }
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Mine")

        // Act — When I try to import that page
        onNodeWithTag(RecipeEditTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportMenuItem).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportUrlField).performTextInput(url("/plain"))
        onNodeWithTag(RecipeEditTestTags.ImportConfirmButton).performClick()

        // Assert — Then I'm told there's no recipe and my title is still there
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithTag(RecipeEditTestTags.ImportError).assertIsDisplayed() }.isSuccess }
        onNodeWithTag(RecipeEditTestTags.ImportCancelButton).performClick()
        onNodeWithTag(RecipeEditTestTags.TitleField).assertTextContains("Mine")
    }

    @Test
    fun anAddressThatCannotBeReachedShowsAnError() = runComposeUiTest {
        // Arrange — Given a web address nothing is listening on
        setContent { VestaTheme { CompositionLocalProvider(LocalRecipeImporter provides HttpRecipeImporter()) { RecipeEditScreen(recipe = null) } } }

        // Act — When I try to import it
        onNodeWithTag(RecipeEditTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportMenuItem).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportUrlField).performTextInput("http://127.0.0.1:1/none")
        onNodeWithTag(RecipeEditTestTags.ImportConfirmButton).performClick()

        // Assert — Then I'm shown an error
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithTag(RecipeEditTestTags.ImportError).assertIsDisplayed() }.isSuccess }
    }
}
