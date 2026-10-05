package uk.veritaslabs.vesta.x0x

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive

/** Capped exponential retry delay: [initial], doubling after each failure up to [max]. */
data class Backoff(val initial: Duration = 1.seconds, val max: Duration = 30.seconds) {
    fun next(current: Duration): Duration = minOf(current * 2, max)
}

/**
 * Owns the daemon's direct-message and presence SSE streams and exposes each as a [Flow] that survives the stream
 * dropping: whenever the stream fails to open or ends, it reconnects after a capped exponential
 * backoff. The delay resets once a connection delivered a message or stayed up for [stableAfter].
 *
 * Collecting opens the stream; cancelling the collector closes it. Not tied to any composable, so
 * app-scope code can collect it for the life of the process.
 */
class X0xEventHub(
    private val client: X0xClient,
    private val backoff: Backoff = Backoff(),
    private val stableAfter: Duration = 10.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    val directMessages: Flow<X0xDirectMessage> = reconnecting { onItem, onClose -> client.directEvents(onItem, onClose) }

    /** Agents coming online and going offline, from `/presence/events`; reconnects like [directMessages]. */
    val presence: Flow<X0xPresenceEvent> = reconnecting { onItem, onClose -> client.presenceEvents(onItem, onClose) }

    private fun <T> reconnecting(open: suspend (onItem: (T) -> Unit, onClose: (Throwable?) -> Unit) -> X0xEventStream): Flow<T> = channelFlow {
        var retryDelay = backoff.initial
        while (isActive) {
            val closed = CompletableDeferred<Throwable?>()
            var delivered = false
            val openedAt = timeSource.markNow()

            val stream = try {
                open(
                    { item ->
                        delivered = true
                        trySend(item)
                    },
                    { closed.complete(it) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // daemon not reachable (yet): retry after the backoff below
            }

            if (stream != null) {
                try {
                    closed.await()
                } finally {
                    stream.close()
                }
                if (delivered || openedAt.elapsedNow() >= stableAfter) retryDelay = backoff.initial
            }

            delay(retryDelay)
            retryDelay = backoff.next(retryDelay)
        }
    }.buffer(Channel.UNLIMITED)
}
