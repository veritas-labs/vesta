package uk.veritaslabs.vesta.ui.screens

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.automerge.newShoppingListDocument
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/** The shopping list screen over the real Automerge-backed source (the screen and the document layer together). */
class ShoppingListFlowTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anItemSavedThroughTheSourceAppearsOnTheScreenButNotInThePantry() = runComposeUiTest {
        // Arrange
        val pantry = PantryDocumentSource(Document.load(newPantryDocument()))
        val source = ShoppingListDocumentSource(Document.load(newShoppingListDocument()), pantry)
        setContent {
            val items by source.items.collectAsState(emptyList())
            VestaTheme { ShoppingListScreen(items) }
        }

        // Act
        runBlocking { source.save(ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk", quantity = 2)) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Whole Milk").fetchSemanticsNodes().isNotEmpty() }

        // Assert
        onNodeWithText("Whole Milk").assertIsDisplayed()
        onNodeWithText("×2").assertIsDisplayed()
        kotlin.test.assertEquals(emptyList(), runBlocking { pantry.contents.first() }.items.map { it.title })
    }
}
