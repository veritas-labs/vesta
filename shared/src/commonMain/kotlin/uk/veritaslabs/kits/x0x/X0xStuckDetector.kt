package uk.veritaslabs.kits.x0x

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What a watchdog should do about the daemon it owns. */
enum class X0xWatchdogAction { Wait, Restart }

/**
 * Decides when an x0x daemon the app owns has got stuck, so it can be restarted. A daemon that starts while the
 * device has no network keeps zero peers even once the network is back, and nothing it sends reaches anyone.
 *
 * The daemon counts as *unhealthy* when `/health` can't be read, isn't ok, or reports no peers. It is judged stuck
 * after it has been unhealthy for [stuckAfter] with a usable network, and a restart is allowed no sooner than a backoff
 * after the last one: [minRestartInterval] after the first, doubling after each restart that doesn't help up to
 * [maxRestartInterval], and reset once the daemon has peers. The clock only runs while the daemon is meant to be running and the
 * network is usable: a daemon stopped on purpose (the user keeps x0x off mobile data) or a device with no network is
 * waited on, never restarted.
 */
class X0xStuckDetector(
    private val stuckAfter: Duration = 45.seconds,
    private val minRestartInterval: Duration = 60.seconds,
    private val maxRestartInterval: Duration = 10.minutes,
) {
    private var unhealthySince: Long? = null
    private var lastRestart: Long? = null

    // How long must pass after the last restart before the next, and what it becomes after that one.
    private var requiredGap: Duration = minRestartInterval
    private var nextGap: Duration = minRestartInterval

    /**
     * One look at the daemon at [nowMillis]. [daemonRunning] is whether the app has started it and not stopped it on
     * purpose, [health] its `/health` (null if unreachable) and [networkUsable] whether the device has a working
     * connection to the internet.
     */
    fun check(nowMillis: Long, daemonRunning: Boolean, health: X0xHealth?, networkUsable: Boolean): X0xWatchdogAction {
        if (!daemonRunning || !networkUsable) {
            unhealthySince = null
            return X0xWatchdogAction.Wait
        }
        if (health != null && health.ok && health.peers > 0) {
            unhealthySince = null
            lastRestart = null
            requiredGap = minRestartInterval
            nextGap = minRestartInterval
            return X0xWatchdogAction.Wait
        }
        val since = unhealthySince ?: nowMillis.also { unhealthySince = it }
        val stuck = nowMillis - since >= stuckAfter.inWholeMilliseconds
        val rested = lastRestart?.let { nowMillis - it >= requiredGap.inWholeMilliseconds } ?: true
        if (!stuck || !rested) return X0xWatchdogAction.Wait
        lastRestart = nowMillis
        // The restarted daemon gets a fresh chance to find peers before being judged again.
        unhealthySince = nowMillis
        requiredGap = nextGap
        nextGap = (nextGap * 2).coerceAtMost(maxRestartInterval)
        return X0xWatchdogAction.Restart
    }
}
