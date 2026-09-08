package uk.veritaslabs.vesta.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class SecondaryButtonTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsLabel() = runComposeUiTest {
        setContent { VestaTheme { SecondaryButton(label = "Join a home") } }

        onNodeWithText("Join a home").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun invokesOnClickWhenTapped() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme { SecondaryButton(label = "Join a home", onClick = { clicked = true }) }
        }

        onNodeWithText("Join a home").performClick()

        assertTrue(clicked)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun doesNotInvokeOnClickWhenDisabled() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme {
                SecondaryButton(label = "Join a home", enabled = false, onClick = { clicked = true })
            }
        }

        onNodeWithText("Join a home").performClick()

        assertTrue(!clicked)
    }
}
