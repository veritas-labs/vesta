package uk.veritaslabs.vesta.calendar

import java.util.TimeZone

actual fun zoneOffsetMillis(zoneId: String, utcMillis: Long): Long? =
    // TimeZone.getTimeZone answers GMT for a name it doesn't know, so ask whether it knows the name first.
    TimeZone.getAvailableIDs().takeIf { zoneId in it }?.let { TimeZone.getTimeZone(zoneId).getOffset(utcMillis).toLong() }

actual fun deviceOffsetMillis(utcMillis: Long): Long = TimeZone.getDefault().getOffset(utcMillis).toLong()
