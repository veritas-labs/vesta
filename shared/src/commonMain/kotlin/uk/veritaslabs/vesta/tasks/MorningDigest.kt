package uk.veritaslabs.vesta.tasks

import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.LocalMoment
import uk.veritaslabs.vesta.calendar.deviceOffsetMillis
import uk.veritaslabs.vesta.calendar.localMoment
import uk.veritaslabs.vesta.calendar.utcMillisOf

/** The device-local minute of the day the morning digest becomes due: 07:00. */
const val MorningDigestMinute = 7 * 60

/** One line of the morning digest: a task due today that has no time, and the Home ([homeId], [homeName]) it is in. */
data class MorningDigestItem(val title: String, val homeId: String, val homeName: String, val isPrivate: Boolean)

/**
 * The open occurrences due on [today] that have no time of day and are for [me] ("m:<member id>"): assigned to them or to anyone,
 * and their private ones. Tasks with a time are left to the reminders of ADR-032; a task with no date is not due today.
 */
fun untimedToday(tasks: List<Task>, done: DoneIndex, me: String, today: CalendarDate): List<TaskOccurrence> =
    tasksOn(tasks, done, today).filter { !it.isDone && it.task.timeMinute == null && it.task.isFor(me) }

/** Whether the digest should be shown at [now] given the day it was [lastShown]: it is past 07:00 and it hasn't been shown today. */
fun morningDigestDue(now: LocalMoment, lastShown: CalendarDate?): Boolean =
    now.minute >= MorningDigestMinute && lastShown != now.date

/**
 * The next 07:00 on the device's clock that is after [nowMillis], as a UTC instant: today's if it is still ahead, tomorrow's if not.
 * [offset] stands in for the device time zone in tests.
 */
fun nextMorningDigestMillis(nowMillis: Long, offset: (Long) -> Long = ::deviceOffsetMillis): Long {
    val now = localMoment(nowMillis, offset)
    val today = utcMillisOf(now.date, MorningDigestMinute, offset)
    return if (today > nowMillis) today else utcMillisOf(now.date.plusDays(1), MorningDigestMinute, offset)
}
