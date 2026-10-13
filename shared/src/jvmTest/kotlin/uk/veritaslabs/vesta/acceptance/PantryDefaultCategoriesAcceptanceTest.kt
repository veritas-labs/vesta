package uk.veritaslabs.vesta.acceptance

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import uk.veritaslabs.vesta.automerge.openPantryDocumentSource
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.screens.PantryCategoriesScreen
import uk.veritaslabs.vesta.ui.screens.PantryCategoriesTestTags
import uk.veritaslabs.vesta.ui.screens.PantryCategoriesViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for the default Pantry categories (ADR-010 amendment, see docs/testing/acceptance-tests.md): a
 * Pantry stored with the first default set is reset to the supermarket-aisle set when the app opens it, once, and
 * categories added afterwards are kept. Driven through the real stored document, source, view model and categories
 * screen.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class PantryDefaultCategoriesAcceptanceTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A Pantry as stored before the amendment: the first default set (here just Meat and Dairy) plus a custom one. */
    private fun VestaDatabase.storeOldPantry(homeId: String) {
        val doc = Document()
        val tx = doc.startTransaction()
        tx.set(ObjectId.ROOT, "items", ObjectType.MAP)
        val categories = tx.set(ObjectId.ROOT, "categories", ObjectType.MAP)
        listOf("Meat", "Dairy", "Aardvark Food").forEach { name ->
            val category = tx.set(categories, name.lowercase(), ObjectType.MAP)
            tx.set(category, "name", name)
            tx.set(category, "secondary", ObjectType.MAP)
        }
        tx.commit()
        runBlocking {
            homeQueries.insert(homeId, "Home", "family", 0L)
            automergeDocumentQueries.upsert(pantryDocumentId(homeId), homeId, "pantry", doc.save(), 0L)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showCategories(database: VestaDatabase, homeId: String) {
        val source = runBlocking { openPantryDocumentSource(database, homeId) }
        setContent { VestaTheme { PantryCategoriesScreen(PantryCategoriesViewModel(source)) } }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(PantryCategoriesTestTags.category("Baby")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aPantryStoredWithTheOldCategoriesShowsTheNewDefaults() = runComposeUiTest {
        // Arrange — Given a Home whose stored Pantry has the old categories and a custom one
        val database = openInMemoryDatabase()
        database.storeOldPantry("home-1")

        // Act — When I open the Pantry and go to its categories
        showCategories(database, "home-1")

        // Assert — Then I see the new defaults, and the old categories are gone
        onNodeWithTag(PantryCategoriesTestTags.category("Baby")).assertIsDisplayed()
        onNodeWithTag(PantryCategoriesTestTags.category("Bakery")).assertIsDisplayed()
        onNodeWithTag(PantryCategoriesTestTags.category("Aardvark Food")).assertDoesNotExist()
        onNodeWithTag(PantryCategoriesTestTags.category("Meat")).assertDoesNotExist()
        onNodeWithTag(PantryCategoriesTestTags.category("Dairy")).assertDoesNotExist()
    }

    @Test
    fun aCategoryIAddAfterTheResetSurvivesReopeningThePantry() = runComposeUiTest {
        // Arrange — Given an old Pantry that has been opened (and so reset) once, then given a category of my own
        val database = openInMemoryDatabase()
        database.storeOldPantry("home-1")
        runBlocking { openPantryDocumentSource(database, "home-1").addPrimary("Aardvark Food") }

        // Act — When the app reopens the Pantry and I open its categories
        showCategories(database, "home-1")

        // Assert — Then my category is still there beside the defaults
        onNodeWithTag(PantryCategoriesTestTags.category("Aardvark Food")).assertIsDisplayed()
        onNodeWithTag(PantryCategoriesTestTags.category("Baby")).assertIsDisplayed()
    }
}
