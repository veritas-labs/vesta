package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class CookbookScreenTest {

    private val pie = Recipe(
        key = "pie", title = "Shepherd's Pie", type = RecipeType.Dinner, servings = 4, prepMinutes = 20, cookMinutes = 50, rating = 4,
        ingredients = listOf(RecipeIngredient("Potatoes"), RecipeIngredient("Lamb"), RecipeIngredient("Onion")),
    )
    private val crumble = Recipe(key = "crumble", title = "Apple Crumble", type = RecipeType.Dessert, favourite = true, tags = listOf("Quick"))
    private val pantry = listOf(
        PantryListItem("potatoes", "Veg", null, "Potatoes", "", 2),
        PantryListItem("onion", "Veg", null, "Onion", "", 0),
    )

    @Test
    fun showsAMessageWhenThereAreNoRecipes() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CookbookScreen(recipes = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("No recipes yet").assertIsDisplayed()
        onNodeWithTag(CookbookTestTags.SearchField).assertDoesNotExist()
    }

    @Test
    fun eachRowShowsTitleTypeTimeServingsAndHowManyIngredientsThePantryHas() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CookbookScreen(recipes = listOf(pie), pantry = pantry) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("Shepherd's Pie").assertIsDisplayed()
        onNodeWithText("Dinner · 1 h 10 min · Serves 4").assertIsDisplayed()
        onNodeWithTag(CookbookTestTags.pantrySummary("pie"), useUnmergedTree = true).assertTextEquals("1 of 3 ingredients in pantry")
    }

    @Test
    fun tappingARowReportsTheRecipe() = runComposeUiTest {
        // Arrange
        var selected: Recipe? = null
        setContent { VestaTheme { CookbookScreen(recipes = listOf(pie, crumble), onRecipeSelected = { selected = it }) } }

        // Act
        onNodeWithTag(CookbookTestTags.recipe("crumble")).performClick()

        // Assert
        assertEquals("crumble", selected?.key)
    }

    @Test
    fun theFilterHidesRecipesItDoesNotLetThrough() = runComposeUiTest {
        // Arrange
        setContent {
            VestaTheme { CookbookScreen(recipes = listOf(pie, crumble), filter = CookbookFilter(type = RecipeType.Dessert)) }
        }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("Apple Crumble").assertIsDisplayed()
        onNodeWithText("Shepherd's Pie").assertDoesNotExist()
    }

    @Test
    fun aFilterThatMatchesNothingSaysSoInsteadOfPromptingForAFirstRecipe() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CookbookScreen(recipes = listOf(pie), filter = CookbookFilter(query = "zzz")) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("No recipes match").assertIsDisplayed()
    }

    @Test
    fun chipsOfferOnlyTheTypesAndTagsInUseAndReportTaps() = runComposeUiTest {
        // Arrange
        val toggled = mutableListOf<String>()
        setContent {
            VestaTheme {
                CookbookScreen(
                    recipes = listOf(pie, crumble),
                    onToggleType = { toggled += it.name },
                    onToggleTag = { toggled += it },
                    onToggleFavourites = { toggled += "favourites" },
                )
            }
        }

        // Act
        onNodeWithTag(CookbookTestTags.typeChip(RecipeType.Dessert)).performClick()
        onNodeWithTag(CookbookTestTags.tagChip("Quick")).performClick()
        onNodeWithTag(CookbookTestTags.FavouritesChip).performClick()

        // Assert
        assertEquals(listOf("Dessert", "Quick", "favourites"), toggled)
        onNodeWithTag(CookbookTestTags.typeChip(RecipeType.Drink)).assertDoesNotExist()
    }
}
