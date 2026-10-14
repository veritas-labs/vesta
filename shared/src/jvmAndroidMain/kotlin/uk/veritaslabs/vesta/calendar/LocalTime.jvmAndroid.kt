package uk.veritaslabs.vesta.calendar

import java.util.TimeZone

actual fun deviceOffsetMillis(utcMillis: Long): Long = TimeZone.getDefault().getOffset(utcMillis).toLong()
