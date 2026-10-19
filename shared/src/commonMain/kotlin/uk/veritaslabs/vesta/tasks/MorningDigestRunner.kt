package uk.veritaslabs.vesta.tasks

import kotlin.time.Clock
import kotlinx.coroutines.delay
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.deviceOffsetMillis
import uk.veritaslabs.vesta.calendar.localMoment

/**
 * Shows the morning digest (ADR-040) from a timer, for a platform with no alarm to lean on (the desktop app): once it is past 07:00 and
 * today's digest hasn't been shown, [read] today's items and [show] them if there are any. Checking every [intervalMillis] means a
 * machine that sleeps through 07:00 shows it within a minute of waking. [lastShown] and [record] keep the day across restarts.
 */
class MorningDigestRunner(
    private val read: suspend (today: CalendarDate) -> List<MorningDigestItem>,
    private val lastShown: () -> CalendarDate?,
    private val record: (CalendarDate) -> Unit,
    private val show: (List<MorningDigestItem>) -> Unit,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val offset: (Long) -> Long = ::deviceOffsetMillis,
) {
    /** One check. The day is recorded before reading, so a failing read does not retry every minute; a day with nothing to say counts as shown. */
    suspend fun checkOnce() {
        val now = localMoment(nowMillis(), offset)
        if (!morningDigestDue(now, lastShown())) return
        record(now.date)
        read(now.date).takeIf { it.isNotEmpty() }?.let(show)
    }

    suspend fun run(intervalMillis: Long = 60_000L) {
        while (true) {
            runCatching { checkOnce() }.onFailure { println("Vesta: morning digest check failed: $it") }
            delay(intervalMillis)
        }
    }
}
