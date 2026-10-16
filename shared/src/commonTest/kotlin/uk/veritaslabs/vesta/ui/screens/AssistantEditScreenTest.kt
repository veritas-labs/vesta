package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.agent.AssistantCapability
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.agent.AssistantFeature
import uk.veritaslabs.vesta.agent.AssistantAccessKind
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class AssistantEditScreenTest {

    private val homes = listOf(
        HomeAccess("h1", "The Johnsons", emptySet(), ask = true),
        HomeAccess("h2", "Flat 4", setOf(AssistantCapability.PantryWrite), ask = false),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aNewAssistantCannotBeSavedWithoutAnAgentId() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { AssistantEditScreen(agentId = null, initialName = "", homes = homes) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(AssistantEditTestTags.SaveButton).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun savingHandsBackTheIdTheNameAndWhatWasSwitchedOn() = runComposeUiTest {
        // Arrange
        var saved: Triple<String, String, List<HomeAccess>>? = null
        setContent {
            VestaTheme {
                AssistantEditScreen(null, "", homes, onSave = { id, name, access -> saved = Triple(id, name, access) })
            }
        }

        // Act
        onNodeWithTag(AssistantEditTestTags.AgentIdField).performTextInput(" bot-1 ")
        onNodeWithTag(AssistantEditTestTags.NameField).performTextInput("Grok Bot")
        onNodeWithTag(AssistantEditTestTags.capability("h1", AssistantCapability.PantryWrite)).performClick()
        onNodeWithTag(AssistantEditTestTags.SaveButton).performScrollTo().assertIsEnabled().performClick()

        // Assert
        val (id, name, access) = checkNotNull(saved)
        assertEquals("bot-1", id)
        assertEquals("Grok Bot", name)
        assertEquals(listOf(true, true), access.map { AssistantCapability.PantryWrite in it.capabilities })
        assertEquals(true, access.first { it.homeId == "h1" }.ask)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun askEachTimeIsOnlyOfferedOnceAddingIsSwitchedOnAndStartsOn() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { AssistantEditScreen(null, "", homes) } }

        // Act
        onNodeWithTag(AssistantEditTestTags.askEachTime("h1")).assertDoesNotExist()
        onNodeWithTag(AssistantEditTestTags.capability("h1", AssistantCapability.PantryWrite)).assertIsOff().performClick()

        // Assert
        onNodeWithTag(AssistantEditTestTags.capability("h1", AssistantCapability.PantryWrite)).assertIsOn()
        onNodeWithTag(AssistantEditTestTags.askEachTime("h1")).assertIsOn()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anExistingAssistantsIdIsFixedAndItCanBeRemovedAfterConfirming() = runComposeUiTest {
        // Arrange
        var removed = 0
        setContent { VestaTheme { AssistantEditScreen("bot-1", "Grok Bot", homes, onRemove = { removed++ }) } }

        // Act
        onNodeWithTag(AssistantEditTestTags.RemoveButton).performScrollTo().performClick()
        val removedBeforeConfirming = removed
        onNodeWithTag(AssistantEditTestTags.ConfirmRemove).performClick()

        // Assert
        assertEquals(0, removedBeforeConfirming)
        assertEquals(1, removed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheX0xTrustCommandForTheAgentIdOnceThereIsOne() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { AssistantEditScreen(agentId = null, initialName = "", homes = homes) } }
        onNodeWithTag(AssistantEditTestTags.TrustCommand).assertDoesNotExist()

        // Act
        onNodeWithTag(AssistantEditTestTags.AgentIdField).performTextInput("bot-1")

        // Assert
        onNodeWithTag(AssistantEditTestTags.TrustCommand).assertTextEquals("x0x trust set bot-1 trusted")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheTrustCommandForAnExistingAssistantToo() = runComposeUiTest {
        // Arrange / Act
        setContent { VestaTheme { AssistantEditScreen(agentId = "bot-1", initialName = "Grok", homes = homes) } }

        // Assert
        onNodeWithTag(AssistantEditTestTags.TrustCommand).assertTextEquals("x0x trust set bot-1 trusted")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun capabilitiesNoCommandImplementsYetAreShownSwitchedOffAndDisabled() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { AssistantEditScreen(agentId = null, initialName = "", homes = homes) } }

        // Act
        // (no user interaction — checking screen's initial state)

        // Assert
        onNodeWithTag(AssistantEditTestTags.capability("h1", AssistantCapability.PantryWrite)).assertIsEnabled()
        onNodeWithTag(AssistantEditTestTags.capability("h1", AssistantCapability.ShoppingListRead)).assertIsNotEnabled()
        onNodeWithTag(AssistantEditTestTags.capability("h1", AssistantCapability.MerchantsWrite)).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyFeatureHasItsOwnReadAndWriteSwitch() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { AssistantEditScreen(agentId = null, initialName = "", homes = homes) } }

        // Act
        // (no user interaction — checking screen's initial state)

        // Assert
        AssistantFeature.entries.forEach { feature ->
            AssistantAccessKind.entries.forEach { kind ->
                onNodeWithTag(AssistantEditTestTags.capability("h1", AssistantCapability.of(feature, kind))).assertExists()
            }
        }
    }
}
