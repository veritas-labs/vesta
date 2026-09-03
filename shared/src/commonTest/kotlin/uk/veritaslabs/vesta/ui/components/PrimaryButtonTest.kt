package uk.veritaslabs.vesta.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class PrimaryButtonTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsLabel() = runComposeUiTest {
        setContent { VestaTheme { PrimaryButton(label = "Create profile") } }

        onNodeWithText("Create profile").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun invokesOnClickWhenTapped() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme { PrimaryButton(label = "Create profile", onClick = { clicked = true }) }
        }

        onNodeWithText("Create profile").performClick()

        assertTrue(clicked)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun doesNotInvokeOnClickWhenDisabled() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme {
                PrimaryButton(label = "Create profile", enabled = false, onClick = { clicked = true })
            }
        }

        onNodeWithText("Create profile").performClick()

        assertTrue(!clicked)
    }
}
