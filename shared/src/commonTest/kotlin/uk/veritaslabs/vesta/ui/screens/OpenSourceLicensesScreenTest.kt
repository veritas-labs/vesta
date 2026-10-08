package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.licenses.OpenSourceLicense
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class OpenSourceLicensesScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachLibraryWithItsVersionAndLicences() = runComposeUiTest {
        // Arrange
        val licenses = listOf(
            OpenSourceLicense("org.automerge:automerge", "0.0.9", listOf("MIT License")),
            OpenSourceLicense("net.java.dev.jna:jna", "5.17.0", listOf("Apache License, Version 2.0", "LGPL 2.1")),
        )
        setContent { VestaTheme { OpenSourceLicensesScreen(licenses) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("org.automerge:automerge").assertIsDisplayed()
        onNodeWithText("0.0.9 · MIT License").assertIsDisplayed()
        onNodeWithText("5.17.0 · Apache License, Version 2.0 / LGPL 2.1").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun saysWhenThereIsNoLicenceInformation() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { OpenSourceLicensesScreen(emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(OpenSourceLicensesTestTags.Empty).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backArrowCallsOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { OpenSourceLicensesScreen(emptyList(), onBack = { backs++ }) } }

        // Act
        onNodeWithTag(OpenSourceLicensesTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }
}
