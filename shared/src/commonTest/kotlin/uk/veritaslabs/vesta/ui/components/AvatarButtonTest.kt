package uk.veritaslabs.vesta.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarButtonTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsLabel() = runComposeUiTest {
        setContent { VestaTheme { AvatarButton(label = "Settings", avatar = {}) } }

        onNodeWithText("Settings").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersCallerSuppliedAvatarContent() = runComposeUiTest {
        setContent {
            VestaTheme { AvatarButton(label = "Settings", avatar = { Text("⚙") }) }
        }

        onNodeWithText("⚙").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun invokesOnClickWhenTapped() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme { AvatarButton(label = "Settings", avatar = {}, onClick = { clicked = true }) }
        }

        onNodeWithText("Settings").performClick()

        assertTrue(clicked)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun verticalOrientationShowsLabelAndRespondsToClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            VestaTheme {
                AvatarButton(
                    label = "Settings",
                    avatar = {},
                    orientation = AvatarButtonOrientation.Vertical,
                    onClick = { clicked = true },
                )
            }
        }

        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithText("Settings").performClick()

        assertTrue(clicked)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun invokesOnLongClickWithoutTriggeringOnClick() = runComposeUiTest {
        var clicked = false
        var longClicked = false
        setContent {
            VestaTheme {
                AvatarButton(
                    label = "Settings",
                    avatar = {},
                    onClick = { clicked = true },
                    onLongClick = { longClicked = true },
                )
            }
        }

        onNodeWithText("Settings").performTouchInput { longClick() }

        assertTrue(longClicked)
        assertFalse(clicked)
    }
}
