package uk.veritaslabs.vesta.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class ActionCardTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTitle() = runComposeUiTest {
        setContent { VestaTheme { ActionCard(title = "Generate device pairing link") } }

        onNodeWithText("Generate device pairing link").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsBodyWhenProvided() = runComposeUiTest {
        setContent {
            VestaTheme {
                ActionCard(title = "Generate device pairing link", body = "Create a link to pair a new device.")
            }
        }

        onNodeWithText("Create a link to pair a new device.").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersCallerSuppliedIconWhenProvided() = runComposeUiTest {
        setContent {
            VestaTheme { ActionCard(title = "Generate device pairing link", icon = { Text("🔗") }) }
        }

        onNodeWithText("🔗").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun invokesOnClickWhenTapped() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme { ActionCard(title = "Generate device pairing link", onClick = { clicked = true }) }
        }

        onNodeWithText("Generate device pairing link").performClick()

        assertTrue(clicked)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun verticalOrientationShowsTitleBodyAndRespondsToClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme {
                ActionCard(
                    title = "Generate device pairing link",
                    body = "Create a link to pair a new device.",
                    orientation = ActionCardOrientation.Vertical,
                    onClick = { clicked = true },
                )
            }
        }

        onNodeWithText("Generate device pairing link").assertIsDisplayed()
        onNodeWithText("Create a link to pair a new device.").assertIsDisplayed()
        onNodeWithText("Generate device pairing link").performClick()

        assertTrue(clicked)
    }
}
