package uk.veritaslabs.vesta.acceptance

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.CookbookDocumentSource
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.ui.flows.CookbookFlow
import uk.veritaslabs.vesta.ui.screens.CookbookViewModel
import uk.veritaslabs.vesta.ui.screens.CookingTestTags
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeDetailTestTags
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeStep
import uk.veritaslabs.vesta.ui.screens.RecipeType
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for cooking mode on a recipe (see docs/testing/acceptance-tests.md). Each test is one scenario, driven
 * only through what a user can see and tap, over the real Automerge-backed cookbook, the view model and the cookbook flow.
 * The criteria:
 *
 *  1. A recipe with a method has a Start cooking button on its page; a recipe with no method does not.
 *  2. Starting shows a full-screen slideshow on the first step, with Previous unavailable.
 *  3. Next and Previous move through the steps in order, showing each step's minutes when it has them.
 *  4. The ingredients are one tap away on every step, scaled to the servings I chose on the recipe page.
 *  5. Next on the last step reads Done and returns me to the recipe page; the close button does the same at any step.
 *
 * Cooking mode keeps no state of its own (nothing is saved or synced), so there is no persistence scenario.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CookingAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its saves run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val shepherdsPie = Recipe(
        title = "Shepherd's Pie",
        type = RecipeType.Dinner,
        servings = 4,
        ingredients = listOf(RecipeIngredient("Eggs", 2.0), RecipeIngredient("Lamb mince", 500.0, "g")),
        steps = listOf(
            RecipeStep("boil", "Boil the potatoes"),
            RecipeStep("brown", "Brown the mince"),
            RecipeStep("bake", "Assemble and bake", minutes = 30),
        ),
    )
    private val salad = Recipe(title = "Green Salad", type = RecipeType.Lunch, servings = 2)

    private fun ComposeUiTest.showCookbookWith(vararg recipes: Recipe) {
        val source = CookbookDocumentSource(Document(), {})
        runBlocking { recipes.forEach { source.save(it) } }
        val pantry = PantryDocumentSource(Document.load(newPantryDocument()))
        setContent {
            val viewModel = androidx.compose.runtime.remember { CookbookViewModel(source, pantry.contents.map { it.items }) }
            VestaTheme { CookbookFlow(viewModel, onBack = {}) }
        }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.openRecipe(title: String) {
        waitForText(title)
        onNodeWithText(title).performClick()
        waitForText("Ingredients")
    }

    @Test
    fun aRecipeWithAMethodOffersStartCookingAndOneWithoutDoesNot() = runComposeUiTest {
        // Arrange — Given a recipe with a method and one with none
        showCookbookWith(shepherdsPie, salad)

        // Act — When I open each recipe's page
        openRecipe("Shepherd's Pie")

        // Assert — Then the one with a method has a Start cooking button
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).assertIsDisplayed()
    }

    @Test
    fun aRecipeWithNoMethodHasNoStartCookingButton() = runComposeUiTest {
        // Arrange — Given a recipe with no method
        showCookbookWith(salad)

        // Act — When I open its page
        openRecipe("Green Salad")

        // Assert — Then there is nothing to cook through
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).assertDoesNotExist()
    }

    @Test
    fun startingCookingShowsTheFirstStepFullScreenWithNoPrevious() = runComposeUiTest {
        // Arrange — Given a recipe's page
        showCookbookWith(shepherdsPie)
        openRecipe("Shepherd's Pie")

        // Act — When I tap Start cooking
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).performClick()

        // Assert — Then I see step 1 of 3 alone, and can't go back
        onNodeWithTag(CookingTestTags.Progress).assertTextEquals("Step 1 of 3")
        onNodeWithTag(CookingTestTags.StepText).assertTextEquals("Boil the potatoes")
        onNodeWithTag(CookingTestTags.Previous).assertIsNotEnabled()
        onNodeWithTag(CookingTestTags.Next).assertIsEnabled()
    }

    @Test
    fun nextAndPreviousWalkTheStepsInOrderShowingMinutes() = runComposeUiTest {
        // Arrange — Given I am cooking the first step
        showCookbookWith(shepherdsPie)
        openRecipe("Shepherd's Pie")
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).performClick()

        // Act — When I go Next twice to the baking step
        repeat(2) { onNodeWithTag(CookingTestTags.Next).performClick() }

        // Assert — Then I see step 3 with its 30 minutes, and Previous takes me back to step 2
        onNodeWithTag(CookingTestTags.StepText).assertTextEquals("Assemble and bake")
        onNodeWithTag(CookingTestTags.StepMinutes).assertTextEquals("30 min")
        onNodeWithTag(CookingTestTags.Previous).performClick()
        onNodeWithTag(CookingTestTags.StepText).assertTextEquals("Brown the mince")
        onNodeWithTag(CookingTestTags.StepMinutes).assertDoesNotExist()
    }

    @Test
    fun theIngredientsAreScaledToMyServingsAndAvailableOnAnyStep() = runComposeUiTest {
        // Arrange — Given a recipe for 4 that I have asked to show for 8
        showCookbookWith(shepherdsPie)
        openRecipe("Shepherd's Pie")
        repeat(4) { onNodeWithTag(RecipeDetailTestTags.ServingsIncrease).performClick() }
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).performClick()
        onNodeWithTag(CookingTestTags.Next).performClick()

        // Act — When I tap the ingredients button on the second step
        onNodeWithTag(CookingTestTags.Ingredients).performClick()

        // Assert — Then the list shows the doubled quantities
        // (the recipe page behind the slideshow shows the same lines, so look inside the dialog only)
        onNode(hasText("1000 g Lamb mince") and hasAnyAncestor(hasTestTag(CookingTestTags.IngredientsDialog))).assertIsDisplayed()
        onNode(hasText("4 Eggs") and hasAnyAncestor(hasTestTag(CookingTestTags.IngredientsDialog))).assertIsDisplayed()
    }

    @Test
    fun nextOnTheLastStepIsDoneAndReturnsMeToTheRecipe() = runComposeUiTest {
        // Arrange — Given I am cooking the last step
        showCookbookWith(shepherdsPie)
        openRecipe("Shepherd's Pie")
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).performClick()
        repeat(2) { onNodeWithTag(CookingTestTags.Next).performClick() }
        onNodeWithTag(CookingTestTags.Next).assertTextEquals("Done")

        // Act — When I tap Done
        onNodeWithTag(CookingTestTags.Next).performClick()

        // Assert — Then the slideshow is gone and the recipe page is showing
        onNodeWithTag(CookingTestTags.Screen).assertDoesNotExist()
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).assertIsDisplayed()
    }

    @Test
    fun theCloseButtonLeavesCookingPartWayThrough() = runComposeUiTest {
        // Arrange — Given I am cooking the second step
        showCookbookWith(shepherdsPie)
        openRecipe("Shepherd's Pie")
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).performClick()
        onNodeWithTag(CookingTestTags.Next).performClick()

        // Act — When I tap close
        onNodeWithTag(CookingTestTags.Close).performClick()

        // Assert — Then I am back on the recipe page
        onNodeWithTag(CookingTestTags.Screen).assertDoesNotExist()
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).assertIsDisplayed()
    }
}
