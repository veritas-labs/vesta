package uk.veritaslabs.kits.x0x

import kotlin.test.Test
import kotlin.test.assertEquals

class X0xStuckDetectorTest {

    private val second = 1_000L
    private val minute = 60 * second

    private fun health(peers: Int, ok: Boolean = true) = X0xHealth(ok, "healthy", "0.45.0", peers, peers, uptimeSecs = 100)

    private val withPeers = health(peers = 8)
    private val noPeers = health(peers = 0)

    private fun X0xStuckDetector.look(now: Long, health: X0xHealth?, running: Boolean = true, network: Boolean = true) =
        check(now, running, health, network)

    @Test
    fun aDaemonWithPeersIsNeverRestarted() {
        // Arrange
        val detector = X0xStuckDetector()

        // Act
        val actions = (0..20).map { detector.look(it * minute, withPeers) }

        // Assert
        assertEquals(setOf(X0xWatchdogAction.Wait), actions.toSet())
    }

    @Test
    fun aDaemonWithNoPeersIsGivenTimeToFindSome() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, noPeers)

        // Act
        val action = detector.look(44 * second, noPeers)

        // Assert
        assertEquals(X0xWatchdogAction.Wait, action)
    }

    @Test
    fun aDaemonWithNoPeersForTooLongOnAWorkingNetworkIsRestarted() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, noPeers)

        // Act
        val action = detector.look(45 * second, noPeers)

        // Assert
        assertEquals(X0xWatchdogAction.Restart, action)
    }

    @Test
    fun aDaemonThatCannotBeReachedCountsAsStuck() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, null)

        // Act
        val action = detector.look(45 * second, null)

        // Assert
        assertEquals(X0xWatchdogAction.Restart, action)
    }

    @Test
    fun aDaemonThatReportsNotOkCountsAsStuck() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, health(peers = 5, ok = false))

        // Act
        val action = detector.look(45 * second, health(peers = 5, ok = false))

        // Assert
        assertEquals(X0xWatchdogAction.Restart, action)
    }

    @Test
    fun peersAppearingRestartsTheClock() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, noPeers)
        detector.look(30 * second, withPeers)
        detector.look(40 * second, noPeers)

        // Act
        val before = detector.look(80 * second, noPeers)
        val after = detector.look(85 * second, noPeers)

        // Assert
        assertEquals(X0xWatchdogAction.Wait, before)
        assertEquals(X0xWatchdogAction.Restart, after)
    }

    @Test
    fun aDaemonStoppedOnPurposeIsNeverRestarted() {
        // Arrange
        val detector = X0xStuckDetector()

        // Act
        val actions = (0..20).map { detector.look(it * minute, health = null, running = false) }

        // Assert
        assertEquals(setOf(X0xWatchdogAction.Wait), actions.toSet())
    }

    @Test
    fun aDaemonStartedAgainAfterBeingStoppedIsJudgedFromThenOn() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, noPeers)
        detector.look(10 * second, health = null, running = false)

        // Act
        val soonAfter = detector.look(50 * second, noPeers)

        // Assert
        assertEquals(X0xWatchdogAction.Wait, soonAfter)
    }

    @Test
    fun noRestartHappensWhileThereIsNoNetwork() {
        // Arrange
        val detector = X0xStuckDetector()

        // Act
        val actions = (0..20).map { detector.look(it * minute, noPeers, network = false) }

        // Assert
        assertEquals(setOf(X0xWatchdogAction.Wait), actions.toSet())
    }

    @Test
    fun theClockOnlyStartsWhenTheNetworkReturns() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, noPeers, network = false)
        detector.look(10 * minute, noPeers, network = false)

        // Act
        val atReturn = detector.look(10 * minute + second, noPeers)
        val soonAfter = detector.look(10 * minute + 30 * second, noPeers)
        val later = detector.look(10 * minute + 47 * second, noPeers)

        // Assert
        assertEquals(X0xWatchdogAction.Wait, atReturn)
        assertEquals(X0xWatchdogAction.Wait, soonAfter)
        assertEquals(X0xWatchdogAction.Restart, later)
    }

    @Test
    fun aRestartedDaemonIsGivenTimeBeforeBeingJudgedAgain() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, noPeers)
        detector.look(45 * second, noPeers) // restart

        // Act
        val soonAfter = detector.look(60 * second, noPeers)

        // Assert
        assertEquals(X0xWatchdogAction.Wait, soonAfter)
    }

    @Test
    fun restartsBackOffWhileTheyDoNotHelp() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, noPeers)
        val first = detector.look(45 * second, noPeers)

        // Act
        val tooSoon = detector.look(100 * second, noPeers) // 55s after the first: under the 60s backoff
        val secondRestart = detector.look(106 * second, noPeers) // 61s after the first
        val tooSoonAgain = detector.look(200 * second, noPeers) // 94s after the second: under the doubled 120s
        val thirdRestart = detector.look(230 * second, noPeers) // 124s after the second

        // Assert
        assertEquals(X0xWatchdogAction.Restart, first)
        assertEquals(X0xWatchdogAction.Wait, tooSoon)
        assertEquals(X0xWatchdogAction.Restart, secondRestart)
        assertEquals(X0xWatchdogAction.Wait, tooSoonAgain)
        assertEquals(X0xWatchdogAction.Restart, thirdRestart)
    }

    @Test
    fun theBackoffIsCapped() {
        // Arrange
        val detector = X0xStuckDetector()
        var now = 0L
        detector.look(now, noPeers)
        repeat(10) {
            now += 11 * minute // longer than the cap, so each look is allowed to restart
            detector.look(now, noPeers)
        }

        // Act
        now += 10 * minute + 46 * second
        val action = detector.look(now, noPeers)

        // Assert
        assertEquals(X0xWatchdogAction.Restart, action)
    }

    @Test
    fun findingPeersResetsTheBackoff() {
        // Arrange
        val detector = X0xStuckDetector()
        detector.look(0, noPeers)
        detector.look(45 * second, noPeers) // restart, backoff now doubled
        detector.look(110 * second, noPeers) // restart again, doubled again
        detector.look(120 * second, withPeers)
        detector.look(130 * second, noPeers)

        // Act
        val action = detector.look(176 * second, noPeers) // 46s unhealthy, 66s since the last restart

        // Assert
        assertEquals(X0xWatchdogAction.Restart, action)
    }
}
