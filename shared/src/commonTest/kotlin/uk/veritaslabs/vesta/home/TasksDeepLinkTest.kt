package uk.veritaslabs.vesta.home

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TasksDeepLinkTest {
    @AfterTest
    fun reset() = TasksDeepLink.consume(TasksDeepLink.homeId.value.orEmpty())

    @Test
    fun aRequestIsHeldUntilItsHomeConsumesIt() {
        // Arrange
        TasksDeepLink.request("home-1")

        // Act
        TasksDeepLink.consume("home-1")

        // Assert
        assertNull(TasksDeepLink.homeId.value)
    }

    @Test
    fun consumingAnotherHomeKeepsTheRequest() {
        // Arrange
        TasksDeepLink.request("home-1")

        // Act
        TasksDeepLink.consume("home-2")

        // Assert
        assertEquals("home-1", TasksDeepLink.homeId.value)
    }
}
