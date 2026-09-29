package uk.veritaslabs.vesta.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectedHomeTest {

    @Test
    fun startsWithNoHomeSelected() {
        // Arrange
        val selectedHome = SelectedHome()

        // Act
        val homeId = selectedHome.homeId.value

        // Assert
        assertNull(homeId)
    }

    @Test
    fun selectReplacesThePreviousSelection() {
        // Arrange
        val selectedHome = SelectedHome()
        selectedHome.select("home-1")

        // Act
        selectedHome.select("home-2")

        // Assert
        assertEquals("home-2", selectedHome.homeId.value)
    }

    @Test
    fun clearIfOnlyClearsTheMatchingHome() {
        // Arrange
        val selectedHome = SelectedHome()
        selectedHome.select("home-1")

        // Act
        selectedHome.clearIf("home-2")
        val afterMismatch = selectedHome.homeId.value
        selectedHome.clearIf("home-1")

        // Assert
        assertEquals("home-1", afterMismatch)
        assertNull(selectedHome.homeId.value)
    }
}
