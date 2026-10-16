package uk.veritaslabs.kits.x0x

import kotlin.test.Test
import kotlin.test.assertEquals

class X0xConnectivityTest {

    @Test
    fun aDaemonWithPeersIsReachingTheNetwork() {
        // Arrange
        val connectivity = X0xConnectivity.Connected(peers = 4)

        // Act
        val reaching = connectivity.isReachingNetwork

        // Assert
        assertEquals(true, reaching)
    }

    @Test
    fun aDaemonWithNoPeersIsNotReachingTheNetwork() {
        // Arrange
        val connectivity = X0xConnectivity.Connected(peers = 0)

        // Act
        val reaching = connectivity.isReachingNetwork

        // Assert
        assertEquals(false, reaching)
    }

    @Test
    fun anOfflineDaemonIsNotReachingTheNetwork() {
        // Arrange
        val connectivity = X0xConnectivity.Offline

        // Act
        val reaching = connectivity.isReachingNetwork

        // Assert
        assertEquals(false, reaching)
    }

    @Test
    fun aDaemonStillConnectingIsGivenTheBenefitOfTheDoubt() {
        // Arrange
        val connectivity = X0xConnectivity.Connecting

        // Act
        val reaching = connectivity.isReachingNetwork

        // Assert
        assertEquals(true, reaching)
    }
}
