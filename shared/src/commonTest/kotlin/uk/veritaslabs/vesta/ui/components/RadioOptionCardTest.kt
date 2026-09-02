package uk.veritaslabs.vesta.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class RadioOptionCardTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTitle() = runComposeUiTest {
        setContent { VestaTheme { RadioOptionCard(title = "Family") } }

        onNodeWithText("Family").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsBodyWhenProvided() = runComposeUiTest {
        setContent {
            VestaTheme { RadioOptionCard(title = "Family", body = "A household with kids.") }
        }

        onNodeWithText("A household with kids.").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersCallerSuppliedIconWhenProvided() = runComposeUiTest {
        setContent {
            VestaTheme { RadioOptionCard(title = "Family", icon = { Text("⌂") }) }
        }

        onNodeWithText("⌂").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun invokesOnClickWhenTapped() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme { RadioOptionCard(title = "Family", onClick = { clicked = true }) }
        }

        onNodeWithText("Family").performClick()

        assertTrue(clicked)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reflectsSelectedState() = runComposeUiTest {
        setContent {
            VestaTheme { RadioOptionCard(title = "Family", selected = true) }
        }

        onNodeWithText("Family").assertIsSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reflectsUnselectedStateByDefault() = runComposeUiTest {
        setContent {
            VestaTheme { RadioOptionCard(title = "Family") }
        }

        onNodeWithText("Family").assertIsNotSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun verticalOrientationShowsTitleBodyAndRespondsToClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme {
                RadioOptionCard(
                    title = "Family",
                    body = "A household with kids.",
                    orientation = RadioOptionCardOrientation.Vertical,
                    selected = true,
                    onClick = { clicked = true },
                )
            }
        }

        onNodeWithText("Family").assertIsDisplayed()
        onNodeWithText("A household with kids.").assertIsDisplayed()
        onNodeWithText("Family").assertIsSelected()
        onNodeWithText("Family").performClick()

        assertTrue(clicked)
    }
}
