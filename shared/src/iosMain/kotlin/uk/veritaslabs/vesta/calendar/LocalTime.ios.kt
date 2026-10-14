package uk.veritaslabs.vesta.calendar

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName

actual fun zoneOffsetMillis(zoneId: String, utcMillis: Long): Long? =
    NSTimeZone.timeZoneWithName(zoneId)?.let { it.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(utcMillis / 1000.0)) * 1000L }

actual fun deviceOffsetMillis(utcMillis: Long): Long =
    NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(utcMillis / 1000.0)) * 1000L
