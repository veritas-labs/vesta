package uk.veritaslabs.vesta.calendar

/**
 * Whether something first happening on [first] and coming round every [every] days, weeks, months or years (as [this] says)
 * happens on [day]. Counted from [first]: nothing recurs before it. Monthly keeps the day of the month, so a repeat on the
 * 31st skips months without one, and yearly on 29 February skips non-leap years. Shared by calendar events (ADR-029) and tasks (ADR-031).
 */
fun EventRepeat.recursOn(first: CalendarDate, day: CalendarDate, every: Int = 1): Boolean {
    val step = every.coerceAtLeast(1)
    return when (this) {
        EventRepeat.None -> day == first
        EventRepeat.Daily -> day >= first && (day.epochDay - first.epochDay) % step == 0L
        EventRepeat.Weekly -> day >= first && (day.epochDay - first.epochDay) % (7L * step) == 0L
        EventRepeat.Monthly -> day >= first && day.day == first.day && monthIndex(day).minus(monthIndex(first)) % step == 0
        EventRepeat.Yearly -> day >= first && day.month == first.month && day.day == first.day && (day.year - first.year) % step == 0
    }
}

private fun monthIndex(date: CalendarDate): Int = date.year * 12 + date.month - 1

/**
 * The last day on or before [limit] that the repeat first happening on [first] lands on, or null if it hasn't started by then
 * (a repeat of [EventRepeat.None] lands only on [first]).
 */
fun EventRepeat.latestOn(first: CalendarDate, limit: CalendarDate, every: Int = 1): CalendarDate? {
    if (limit < first) return null
    val step = every.coerceAtLeast(1)
    return when (this) {
        EventRepeat.None -> first
        EventRepeat.Daily, EventRepeat.Weekly -> {
            val length = (if (this == EventRepeat.Daily) 1L else 7L) * step
            val back = (limit.epochDay - first.epochDay) % length
            limit.plusDays(-back.toInt())
        }
        // A month or year may lack the day (the 31st, 29 February), so walk back to one that has it.
        EventRepeat.Monthly, EventRepeat.Yearly -> {
            val months = if (this == EventRepeat.Monthly) step else 12 * step
            var index = monthIndex(first) + (monthIndex(limit) - monthIndex(first)).floorDiv(months) * months
            var found: CalendarDate? = null
            while (found == null && index >= monthIndex(first)) {
                val year = index.floorDiv(12)
                val month = index.mod(12) + 1
                if (first.day <= daysInMonth(year, month)) {
                    val candidate = CalendarDate(year, month, first.day)
                    if (candidate <= limit && candidate >= first) found = candidate
                }
                index -= months
            }
            found
        }
    }
}
