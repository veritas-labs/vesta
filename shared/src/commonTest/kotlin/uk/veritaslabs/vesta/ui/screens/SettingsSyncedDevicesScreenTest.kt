package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.devicesync.SyncedDevice
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class SettingsSyncedDevicesScreenTest {

    private val pixel = SyncedDevice(name = "Mark's Pixel", agentId = "abc123")
    private val desktop = SyncedDevice(name = "Office desktop", agentId = "def456")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachDevicesNameAndAgentId() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsSyncedDevicesScreen(devices = listOf(pixel, desktop)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Mark's Pixel").assertIsDisplayed()
        onNodeWithText("abc123").assertIsDisplayed()
        onNodeWithText("Office desktop").assertIsDisplayed()
        onNodeWithText("def456").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAnEmptyStateWhenThereAreNoDevices() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsSyncedDevicesScreen(devices = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncedDevicesTestTags.EmptyState).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hidesTheEmptyStateWhenThereAreDevices() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsSyncedDevicesScreen(devices = listOf(pixel)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(SyncedDevicesTestTags.EmptyState).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingTheFabInvokesOnAddDevice() = runComposeUiTest {
        // Arrange
        var adds = 0
        setContent { VestaTheme { SettingsSyncedDevicesScreen(devices = emptyList(), onAddDevice = { adds++ }) } }

        // Act
        onNodeWithTag(SyncedDevicesTestTags.AddButton).performClick()

        // Assert
        assertEquals(1, adds)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun removingADeviceNeedsConfirmation() = runComposeUiTest {
        // Arrange
        var removed: SyncedDevice? = null
        setContent {
            VestaTheme { SettingsSyncedDevicesScreen(devices = listOf(pixel, desktop), onRemoveDevice = { removed = it }) }
        }

        // Act
        onNodeWithTag(SyncedDevicesTestTags.remove("def456")).performClick()
        val beforeConfirm = removed
        onNodeWithTag(SyncedDevicesTestTags.ConfirmRemove).performClick()

        // Assert
        assertNull(beforeConfirm)
        assertEquals(desktop, removed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingBackInvokesOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { SettingsSyncedDevicesScreen(devices = emptyList(), onBack = { backs++ }) } }

        // Act
        onNodeWithTag(SyncedDevicesTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }
}
