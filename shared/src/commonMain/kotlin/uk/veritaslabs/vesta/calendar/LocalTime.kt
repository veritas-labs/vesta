package uk.veritaslabs.vesta.calendar

import kotlin.time.Clock

private const val MillisPerMinute = 60_000L
private const val MillisPerDay = 86_400_000L
internal const val MinutesPerDay = 1_440

/** The device time zone's offset from UTC at the UTC instant [utcMillis], in milliseconds, daylight saving included. */
expect fun deviceOffsetMillis(utcMillis: Long): Long

/** A wall-clock moment: the [date] and the [minute] of that day (0..1439). */
data class LocalMoment(val date: CalendarDate, val minute: Int)

/** The wall-clock moment the UTC instant [utcMillis] is in the device time zone ([offset] stands in for it in tests). */
fun localMoment(utcMillis: Long, offset: (Long) -> Long = ::deviceOffsetMillis): LocalMoment {
    val local = utcMillis + offset(utcMillis)
    return LocalMoment(CalendarDate.fromEpochDay(local.floorDiv(MillisPerDay)), (local.mod(MillisPerDay) / MillisPerMinute).toInt())
}

/** The UTC instant at which the device's wall clock reads [minute] on [date], the inverse of [localMoment]. */
fun utcMillisOf(date: CalendarDate, minute: Int, offset: (Long) -> Long = ::deviceOffsetMillis): Long {
    val naive = date.epochDay * MillisPerDay + minute * MillisPerMinute
    // The offset belongs to the instant, not the wall clock, so ask for it where the guess lands.
    return naive - offset(naive - offset(naive))
}

/** The UTC midnight that stands for the all-day [date]: an all-day event has no time zone. */
fun allDayMillis(date: CalendarDate): Long = date.epochDay * MillisPerDay

/** The date an [allDayMillis] stands for. */
fun allDayDate(utcMillis: Long): CalendarDate = CalendarDate.fromEpochDay(utcMillis.floorDiv(MillisPerDay))

/** Today by the device's clock and time zone. */
fun CalendarDate.Companion.today(): CalendarDate = localMoment(Clock.System.now().toEpochMilliseconds()).date

/** Hours and minutes as `09:30`. */
fun formatMinute(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"
