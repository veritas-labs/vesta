package uk.veritaslabs.vesta.x0x

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class X0xEventHubTest {

    private fun message(sender: String) = X0xDirectMessage(sender = sender)

    private fun TestScopeHub(client: FakeX0xClient, scope: kotlinx.coroutines.test.TestScope) =
        X0xEventHub(client, Backoff(1.seconds, 8.seconds), stableAfter = 10.seconds, timeSource = scope.testScheduler.timeSource)

    @Test
    fun emitsMessagesFromTheStream() = runTest {
        // Arrange
        val client = FakeX0xClient()
        val received = mutableListOf<String?>()
        val job = launch { TestScopeHub(client, this@runTest).directMessages.collect { received += it.sender } }
        runCurrent()

        // Act
        client.openedStreams.single().emit(message("a"))
        client.openedStreams.single().emit(message("b"))
        runCurrent()

        // Assert
        assertEquals(listOf<String?>("a", "b"), received)
        job.cancel()
    }

    @Test
    fun reconnectsAfterTheStreamDrops() = runTest {
        // Arrange
        val client = FakeX0xClient()
        val received = mutableListOf<String?>()
        val job = launch { TestScopeHub(client, this@runTest).directMessages.collect { received += it.sender } }
        runCurrent()
        client.openedStreams[0].emit(message("first"))

        // Act
        client.openedStreams[0].drop()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()
        client.openedStreams[1].emit(message("second"))
        runCurrent()

        // Assert
        assertEquals(2, client.openedStreams.size)
        assertEquals(listOf<String?>("first", "second"), received)
        job.cancel()
    }

    @Test
    fun backsOffBetweenFailedOpens() = runTest {
        // Arrange
        val client = FakeX0xClient().apply { openFailure = X0xApiException(0, "daemon not reachable") }
        val failingClient = object : X0xClient by client {
            override suspend fun directEvents(onMessage: (X0xDirectMessage) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream {
                client.openAttempts += testScheduler.currentTime
                return client.directEvents(onMessage, onClose)
            }
        }
        val job = launch { X0xEventHub(failingClient, Backoff(1.seconds, 4.seconds), timeSource = testScheduler.timeSource).directMessages.collect { } }

        // Act
        advanceTimeBy(20.seconds)
        runCurrent()

        // Assert: attempts at 0s, then delays of 1s, 2s, 4s, 4s (capped)
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L, 11_000L, 15_000L, 19_000L), client.openAttempts)
        job.cancel()
    }

    @Test
    fun backoffResetsAfterAConnectionThatDeliveredAMessage() = runTest {
        // Arrange
        val client = FakeX0xClient()
        val job = launch { TestScopeHub(client, this@runTest).directMessages.collect { } }
        runCurrent()
        client.openedStreams[0].drop() // no message: next delay is 1s, then 2s
        advanceTimeBy(1.seconds)
        runCurrent()
        client.openedStreams[1].emit(message("x"))

        // Act
        client.openedStreams[1].drop()
        advanceTimeBy(1.seconds)
        runCurrent()

        // Assert: reset to the initial 1s rather than 2s
        assertEquals(3, client.openedStreams.size)
        job.cancel()
    }

    @Test
    fun cancellationClosesTheUnderlyingStream() = runTest {
        // Arrange
        val client = FakeX0xClient()
        val job = launch { TestScopeHub(client, this@runTest).directMessages.collect { } }
        runCurrent()

        // Act
        job.cancel()
        runCurrent()

        // Assert
        assertTrue(client.openedStreams.single().closed)
        advanceTimeBy(60.seconds)
        assertEquals(1, client.openedStreams.size)
    }
}
