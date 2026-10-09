package uk.veritaslabs.vesta.calendar

import kotlin.time.Clock

/** A day in the proleptic Gregorian calendar. [month] is 1..12 and [day] 1..31. */
data class CalendarDate(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDate> {

    init {
        require(month in 1..12) { "month $month" }
        require(day in 1..daysInMonth(year, month)) { "day $day of $year-$month" }
    }

    /** Days since 1970-01-01. */
    val epochDay: Long get() = daysFromCivil(year, month, day)

    /** 0 = Monday ... 6 = Sunday. */
    val dayOfWeek: Int get() = ((epochDay + 3).mod(7L)).toInt()

    fun plusDays(days: Int): CalendarDate = fromEpochDay(epochDay + days)

    /** The same day [months] later (or earlier), clamped to the end of a shorter month. */
    fun plusMonths(months: Int): CalendarDate {
        val index = year * 12 + (month - 1) + months
        val y = index.floorDiv(12)
        val m = index.mod(12) + 1
        return CalendarDate(y, m, minOf(day, daysInMonth(y, m)))
    }

    override fun compareTo(other: CalendarDate): Int = compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    companion object {
        fun fromEpochDay(epochDay: Long): CalendarDate {
            // Howard Hinnant's civil_from_days.
            val z = epochDay + 719468
            val era = z.floorDiv(146097)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return CalendarDate(y, m, d)
        }

        /** Today by the UTC clock; callers that know the user's time zone should pass their own date instead. */
        fun todayUtc(): CalendarDate = fromEpochDay(Clock.System.now().epochSeconds.floorDiv(86_400L))
    }
}

fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = y.floorDiv(400)
    val yoe = y - era * 400
    val mp = (month + if (month > 2) -3 else 9).toLong()
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/** The day a calendar week starts on; [dayOfWeek] uses [CalendarDate.dayOfWeek]'s numbering (0 = Monday). */
enum class WeekStart(val dayOfWeek: Int) { Monday(0), Saturday(5), Sunday(6) }

/**
 * The weeks to draw for a month: always six rows of seven days starting on [weekStart], so the grid keeps one size from
 * month to month. Days from the neighbouring months fill the first and last rows.
 */
fun monthGrid(year: Int, month: Int, weekStart: WeekStart = WeekStart.Monday): List<List<CalendarDate>> {
    val first = CalendarDate(year, month, 1)
    val start = first.plusDays(-((first.dayOfWeek - weekStart.dayOfWeek).mod(7)))
    return List(6) { week -> List(7) { day -> start.plusDays(week * 7 + day) } }
}

/** The ISO 8601 week number (1..53) of the week containing this row of seven days, whichever day the row starts on. */
fun weekNumber(week: List<CalendarDate>): Int {
    val thursday = week.first { it.dayOfWeek == 3 }
    val jan1 = CalendarDate(thursday.year, 1, 1)
    return ((thursday.epochDay - jan1.epochDay).toInt()) / 7 + 1
}
