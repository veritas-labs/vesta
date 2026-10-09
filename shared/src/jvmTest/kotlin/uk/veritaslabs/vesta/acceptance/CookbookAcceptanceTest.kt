package uk.veritaslabs.vesta.acceptance

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.CookbookDocumentSource
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.adjustStock
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.automerge.upsertPantryItem
import uk.veritaslabs.vesta.ui.flows.CookbookFlow
import uk.veritaslabs.vesta.ui.screens.CookbookTestTags
import uk.veritaslabs.vesta.ui.screens.CookbookViewModel
import uk.veritaslabs.vesta.ui.screens.HomeDashboardTestTags
import uk.veritaslabs.vesta.ui.screens.MediaKind
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeDetailTestTags
import uk.veritaslabs.vesta.ui.screens.RecipeEditTestTags
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeMedia
import uk.veritaslabs.vesta.ui.screens.RecipeStep
import uk.veritaslabs.vesta.ui.screens.RecipeType
import uk.veritaslabs.vesta.ui.screens.HomeDashboardScreen
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for the Cookbook (docs/adr/ADR-022-cookbook.md; see docs/testing/acceptance-tests.md). Each test is
 * one scenario, driven only through what a user can see and tap, over the real Automerge-backed cookbook and Pantry,
 * the view model and the list, recipe and form screens. The criteria:
 *
 *  1. I can add a recipe (title, description, type, servings, times, rating) and find it in the cookbook.
 *  2. The recipe page shows prep, cook, rest and total time.
 *  3. Each ingredient shows whether the Pantry has it, is out of it, or doesn't know it.
 *  4. The method is in stages; steps that can happen together share a stage, and a step says what it waits for.
 *  5. I can rate a recipe from its page, and the rating shows in the cookbook list.
 *  6. I can show the recipe for other servings and the quantities scale.
 *  7. I can filter by type and favourites, and search by title, ingredient or tag.
 *  8. I can attach photos and videos and see them on the recipe page.
 *  9. I can edit and delete a recipe.
 * 10. Recipes reach a paired device and survive an app restart.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CookbookAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its saves run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val shepherdsPie = Recipe(
        title = "Shepherd's Pie",
        description = "Lamb under a golden mash.",
        type = RecipeType.Dinner,
        servings = 4,
        prepMinutes = 20,
        cookMinutes = 50,
        restMinutes = 10,
        ingredients = listOf(
            RecipeIngredient("Eggs", 2.0),
            RecipeIngredient("Flour", 100.0, "g"),
            RecipeIngredient("Saffron", note = "a pinch"),
            RecipeIngredient("Lamb mince", 500.0, "g"),
        ),
        steps = listOf(
            RecipeStep("boil", "Boil the potatoes"),
            RecipeStep("brown", "Brown the mince"),
            RecipeStep("mash", "Mash the potatoes", after = listOf("boil")),
            RecipeStep("bake", "Assemble and bake", after = listOf("brown", "mash"), minutes = 30),
        ),
        tags = listOf("Comfort"),
    )
    private val crumble = Recipe(
        title = "Apple Crumble", type = RecipeType.Dessert, servings = 6, cookMinutes = 40, favourite = true,
        ingredients = listOf(RecipeIngredient("Apples", 6.0)), tags = listOf("Autumn"),
    )

    private fun newSource(bytes: ByteArray? = null, persist: suspend (ByteArray) -> Unit = {}) =
        CookbookDocumentSource(bytes?.let { Document.load(it) } ?: Document(), persist)

    private fun newPantry(): PantryDocumentSource = PantryDocumentSource(Document.load(newPantryDocument()))

    private suspend fun PantryDocumentSource.stock(title: String, count: Int) = mutate {
        adjustStock(it, upsertPantryItem(it, "Pantry", null, title, "", null), count)
    }

    /** The cookbook as the app shows it: the list, a recipe's page and the form, with the Home's Pantry feeding availability. */
    private fun ComposeUiTest.showCookbook(source: CookbookDocumentSource, pantry: PantryDocumentSource = newPantry()) {
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
    }

    private suspend fun CookbookDocumentSource.keyOf(title: String) = recipes.first().single { it.title == title }.key

    @Test
    fun theDashboardOffersTheCookbook() = runComposeUiTest {
        // Arrange — Given a Home's dashboard
        var opened = false
        setContent { VestaTheme { HomeDashboardScreen(homeName = "Maple Street", onCookbookClick = { opened = true }) } }

        // Act — When I tap the Cookbook tile
        onNodeWithTag(HomeDashboardTestTags.CookbookTile).performClick()

        // Assert — Then the cookbook opens
        assertEquals(true, opened)
    }

    @Test
    fun addingARecipeAndOpeningItShowsWhatIEntered() = runComposeUiTest {
        // Arrange — Given an empty cookbook
        showCookbook(newSource())
        waitForText("No recipes yet")

        // Act — When I add a dessert for 6 that takes 15 minutes of prep and 40 of cooking, rated three stars
        onNodeWithTag(CookbookTestTags.AddRecipeButton).performClick()
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Apple Crumble")
        onNodeWithTag(RecipeEditTestTags.DescriptionField).performTextInput("Buttery and warm.")
        onNodeWithTag(RecipeEditTestTags.typeChip(RecipeType.Dessert)).performScrollTo().performClick()
        repeat(2) { onNodeWithTag(RecipeEditTestTags.ServingsIncrease).performScrollTo().performClick() }
        onNodeWithTag(RecipeEditTestTags.PrepField).performScrollTo().performTextInput("15")
        onNodeWithTag(RecipeEditTestTags.CookField).performScrollTo().performTextInput("40")
        onNodeWithTag(RecipeEditTestTags.star(3)).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()
        waitForText("Apple Crumble")

        // Assert — Then the cookbook lists it, and its page shows the details and the times
        onNodeWithText("Dessert · 55 min · Serves 6").assertIsDisplayed()
        onNodeWithContentDescription("Rated 3 out of 5", useUnmergedTree = true).assertIsDisplayed()
        openRecipe("Apple Crumble")
        onNodeWithText("Buttery and warm.").assertIsDisplayed()
        onNodeWithTag(RecipeDetailTestTags.Servings).assertTextEquals("Serves 6")
        onNodeWithTag(RecipeDetailTestTags.PrepTime).assertTextEquals("15 min")
        onNodeWithTag(RecipeDetailTestTags.CookTime).assertTextEquals("40 min")
        onNodeWithTag(RecipeDetailTestTags.RestTime).assertTextEquals("-")
        onNodeWithTag(RecipeDetailTestTags.TotalTime).assertTextEquals("55 min")
    }

    @Test
    fun ingredientsSayWhetherThePantryHasThem() = runComposeUiTest {
        // Arrange — Given a Pantry with eggs in stock and flour used up, and a recipe that needs eggs, flour and saffron
        val source = newSource()
        val pantry = newPantry()
        runBlocking {
            pantry.stock("Eggs", 6)
            pantry.stock("Flour", 0)
            source.save(shepherdsPie)
        }
        showCookbook(source, pantry)

        // Act — When I open the recipe
        openRecipe("Shepherd's Pie")
        waitForText("Ingredients")

        // Assert — Then eggs are in the pantry, flour is out of stock, and saffron and lamb are not in the pantry
        onNodeWithTag(RecipeDetailTestTags.availability(0)).assertTextEquals("In pantry")
        onNodeWithTag(RecipeDetailTestTags.availability(1)).assertTextEquals("Out of stock")
        onNodeWithTag(RecipeDetailTestTags.availability(2)).assertTextEquals("Not in pantry")
        onNodeWithTag(RecipeDetailTestTags.availability(3)).assertTextEquals("Not in pantry")
        onNodeWithTag(RecipeDetailTestTags.IngredientsSummary).assertTextEquals("1 of 4 in your pantry")
    }

    @Test
    fun theCookbookListSaysHowManyIngredientsThePantryHas() = runComposeUiTest {
        // Arrange — Given a recipe needing four ingredients and a Pantry holding two of them
        val source = newSource()
        val pantry = newPantry()
        runBlocking {
            pantry.stock("Eggs", 6)
            pantry.stock("Flour", 1)
            source.save(shepherdsPie)
        }

        // Act — When I look at the cookbook
        showCookbook(source, pantry)
        waitForText("Shepherd's Pie")

        // Assert — Then the recipe says 2 of its 4 ingredients are in the pantry
        val key = runBlocking { source.keyOf("Shepherd's Pie") }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("2 of 4 ingredients in pantry", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(CookbookTestTags.pantrySummary(key), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theMethodGroupsStepsThatCanBeDoneAtTheSameTime() = runComposeUiTest {
        // Arrange — Given a recipe where boiling and browning are independent, mashing follows boiling, and baking follows both
        val source = newSource()
        runBlocking { source.save(shepherdsPie) }
        showCookbook(source)

        // Act — When I open the recipe
        openRecipe("Shepherd's Pie")
        waitForText("Method")

        // Assert — Then boiling and browning are together in stage 1, then mashing, then baking
        onNodeWithTag(RecipeDetailTestTags.stage(1)).performScrollTo().assertTextEquals("Stage 1")
        onNodeWithTag(RecipeDetailTestTags.step(1)).performScrollTo().assertIsDisplayed()
        onNodeWithText("Boil the potatoes").assertIsDisplayed()
        onNodeWithText("Brown the mince").assertIsDisplayed()
        onNodeWithTag(RecipeDetailTestTags.stage(2)).assertTextEquals("Stage 2")
        onNodeWithText("Mash the potatoes").performScrollTo().assertIsDisplayed()
        onNodeWithTag(RecipeDetailTestTags.stage(3)).assertTextEquals("Stage 3")
        onNodeWithTag(RecipeDetailTestTags.stepMinutes(4)).assertTextEquals("30 min")
    }

    @Test
    fun stepsIBuildInTheFormComeOutInStages() = runComposeUiTest {
        // Arrange — Given an empty cookbook and a new recipe with three steps
        val source = newSource()
        showCookbook(source)
        waitForText("No recipes yet")
        onNodeWithTag(CookbookTestTags.AddRecipeButton).performClick()
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Pasta")
        repeat(3) { onNodeWithTag(RecipeEditTestTags.AddStepButton).performScrollTo().performClick() }
        listOf("Boil the water", "Make the sauce", "Combine and serve").forEachIndexed { index, text ->
            onNodeWithTag(RecipeEditTestTags.stepText(index)).performScrollTo().performTextInput(text)
        }

        // Act — When I say the last step comes after the first two, and save
        onNodeWithTag(RecipeEditTestTags.stepAfter(2, 0)).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.stepAfter(2, 1)).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()
        openRecipe("Pasta")
        waitForText("Method")

        // Assert — Then the first two are one stage to do together, and the last follows
        onNodeWithTag(RecipeDetailTestTags.stage(1)).performScrollTo().assertTextEquals("Stage 1")
        onNodeWithTag(RecipeDetailTestTags.stage(2)).assertTextEquals("Stage 2")
        onNodeWithTag(RecipeDetailTestTags.step(3)).assertExists()
    }

    @Test
    fun ratingARecipeFromItsPageShowsInTheCookbookList() = runComposeUiTest {
        // Arrange — Given an unrated recipe, opened
        val source = newSource()
        runBlocking { source.save(crumble) }
        showCookbook(source)
        openRecipe("Apple Crumble")
        waitForText("Serves 6")

        // Act — When I give it four stars and go back
        onNodeWithTag(RecipeDetailTestTags.star(4)).performClick()
        waitUntil(timeoutMillis = 5_000) { runBlocking { source.recipes.first().single().rating } == 4 }
        onNodeWithTag(RecipeDetailTestTags.BackButton).performClick()

        // Assert — Then the list shows four stars
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithContentDescription4().isNotEmpty()
        }
    }

    private fun ComposeUiTest.onAllNodesWithContentDescription4() =
        onAllNodesWithContentDescription("Rated 4 out of 5", useUnmergedTree = true).fetchSemanticsNodes()

    @Test
    fun showingTheRecipeForMorePeopleScalesTheQuantities() = runComposeUiTest {
        // Arrange — Given a recipe for 4 with 500 g of lamb mince
        val source = newSource()
        runBlocking { source.save(shepherdsPie) }
        showCookbook(source)
        openRecipe("Shepherd's Pie")
        waitForText("Ingredients")
        onNodeWithText("500 g Lamb mince").assertIsDisplayed()

        // Act — When I ask for 8 servings
        repeat(4) { onNodeWithTag(RecipeDetailTestTags.ServingsIncrease).performClick() }

        // Assert — Then the lamb doubles, the eggs double, and the page says it was scaled from 4
        onNodeWithTag(RecipeDetailTestTags.Servings).assertTextEquals("Serves 8")
        onNodeWithText("1000 g Lamb mince").assertIsDisplayed()
        onNodeWithText("4 Eggs").assertIsDisplayed()
        onNodeWithText("Scaled from 4").assertIsDisplayed()
    }

    @Test
    fun searchingAndFilteringNarrowsTheCookbook() = runComposeUiTest {
        // Arrange — Given a cookbook with a dinner and a favourite dessert
        val source = newSource()
        runBlocking { listOf(shepherdsPie, crumble).forEach { source.save(it) } }
        showCookbook(source)
        waitForText("Shepherd's Pie")

        // Act / Assert — When I filter to desserts, only the crumble shows
        onNodeWithTag(CookbookTestTags.typeChip(RecipeType.Dessert)).performClick()
        onNodeWithText("Apple Crumble").assertIsDisplayed()
        onNodeWithText("Shepherd's Pie").assertDoesNotExist()

        // When I clear that and search for an ingredient, only the recipe using it shows
        onNodeWithTag(CookbookTestTags.typeChip(RecipeType.Dessert)).performClick()
        onNodeWithTag(CookbookTestTags.SearchField).performTextInput("lamb")
        onNodeWithText("Shepherd's Pie").assertIsDisplayed()
        onNodeWithText("Apple Crumble").assertDoesNotExist()
    }

    @Test
    fun favouritingARecipeLetsMeFilterToMyFavourites() = runComposeUiTest {
        // Arrange — Given two recipes, neither a favourite
        val source = newSource()
        runBlocking { listOf(shepherdsPie, crumble.copy(favourite = false)).forEach { source.save(it) } }
        showCookbook(source)
        openRecipe("Apple Crumble")
        waitForText("Serves 6")

        // Act — When I favourite the crumble, go back and filter to favourites
        onNodeWithTag(RecipeDetailTestTags.FavouriteButton).performClick()
        waitUntil(timeoutMillis = 5_000) { runBlocking { source.recipes.first().any { it.favourite } } }
        onNodeWithTag(RecipeDetailTestTags.BackButton).performClick()
        waitForText("Shepherd's Pie")
        onNodeWithTag(CookbookTestTags.FavouritesChip).performClick()

        // Assert — Then only the crumble shows
        onNodeWithText("Apple Crumble").assertIsDisplayed()
        onNodeWithText("Shepherd's Pie").assertDoesNotExist()
    }

    @Test
    fun aPhotoAndAVideoIAttachAppearOnTheRecipePage() = runComposeUiTest {
        // Arrange — Given a recipe I open for editing
        val source = newSource()
        runBlocking { source.save(crumble) }
        showCookbook(source)
        openRecipe("Apple Crumble")
        waitForText("Serves 6")
        onNodeWithTag(RecipeDetailTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeDetailTestTags.EditMenuItem).performClick()

        // Act — When I attach a photo and a captioned video, and save
        onNodeWithTag(RecipeEditTestTags.MediaUriField).performScrollTo().performTextInput("file:///photos/crumble.jpg")
        onNodeWithTag(RecipeEditTestTags.AddMediaButton).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.MediaUriField).performScrollTo().performTextInput("https://example.com/crumble.mp4")
        onNodeWithTag(RecipeEditTestTags.MediaCaptionField).performScrollTo().performTextInput("Topping it")
        onNodeWithTag(RecipeEditTestTags.MediaVideoChip).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.AddMediaButton).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert — Then the page shows both, the photo by file name and the video by caption
        waitForText("Topping it")
        onNodeWithTag(RecipeDetailTestTags.media(0)).assertIsDisplayed()
        onNodeWithText("crumble.jpg").assertIsDisplayed()
        onNodeWithTag(RecipeDetailTestTags.media(1)).assertIsDisplayed()
        assertEquals(
            listOf(MediaKind.Photo, MediaKind.Video),
            runBlocking { source.recipes.first().single().media.map { it.kind } },
        )
    }

    @Test
    fun editingARecipeChangesItAndDeletingItRemovesItAfterConfirming() = runComposeUiTest {
        // Arrange — Given a recipe
        val source = newSource()
        runBlocking { source.save(crumble) }
        showCookbook(source)
        openRecipe("Apple Crumble")
        waitForText("Serves 6")

        // Act — When I rename it through Edit
        onNodeWithTag(RecipeDetailTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeDetailTestTags.EditMenuItem).performClick()
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextReplacement("Apple Crumble and Custard")
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert — Then the page shows the new name
        waitForText("Apple Crumble and Custard")

        // When I delete it and confirm
        onNodeWithTag(RecipeDetailTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeDetailTestTags.DeleteMenuItem).performClick()
        onNodeWithTag(RecipeDetailTestTags.ConfirmDelete).performClick()

        // Then I am back at an empty cookbook
        waitForText("No recipes yet")
        assertEquals(emptyList(), runBlocking { source.recipes.first() })
    }

    @Test
    fun recipesSurviveARestartAndReachAPairedDevice() = runComposeUiTest {
        // Arrange — Given a recipe saved on one device, whose document bytes were persisted
        var saved: ByteArray? = null
        val phone = newSource(persist = { saved = it })
        runBlocking { phone.save(shepherdsPie) }

        // Act — When the app restarts (the document is loaded from the saved bytes) and a paired tablet syncs
        val restarted = newSource(saved)
        val tablet = newSource()
        runBlocking { tablet.applyChanges(restarted.changesSince(ByteArray(0))) }
        showCookbook(tablet)

        // Assert — Then the tablet's cookbook shows the recipe with its method intact
        openRecipe("Shepherd's Pie")
        waitForText("Method")
        onNodeWithTag(RecipeDetailTestTags.stage(1)).performScrollTo().assertTextEquals("Stage 1")
        assertEquals(shepherdsPie.ingredients, runBlocking { tablet.recipes.first().single().ingredients })
    }
}
