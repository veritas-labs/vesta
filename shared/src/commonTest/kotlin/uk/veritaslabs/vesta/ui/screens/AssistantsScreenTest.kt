package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class AssistantsScreenTest {

    private val grok = AssistantListItem("a1", "Grok Bot", pending = false, homeCount = 2)
    private val stranger = AssistantListItem("a2", "63ab3172…d930", pending = true, homeCount = 0)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAnEmptyStateWhenThereAreNoAssistants() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { AssistantsScreen(assistants = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(AssistantsTestTags.EmptyState).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachAssistantWithItsStatus() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { AssistantsScreen(assistants = listOf(grok, stranger)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Grok Bot").assertIsDisplayed()
        onNodeWithText("Acts in 2 home(s)").assertIsDisplayed()
        onNodeWithText("63ab3172…d930").assertIsDisplayed()
        onNodeWithText("Waiting for review").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingARowOpensThatAssistantAndTheButtonAddsOne() = runComposeUiTest {
        // Arrange
        val opened = mutableListOf<String>()
        var added = 0
        setContent { VestaTheme { AssistantsScreen(listOf(grok), onAdd = { added++ }, onOpen = { opened += it }) } }

        // Act
        onNodeWithTag(AssistantsTestTags.row("a1")).performClick()
        onNodeWithTag(AssistantsTestTags.AddButton).performClick()

        // Assert
        assertEquals(listOf("a1"), opened)
        assertEquals(1, added)
    }
}
