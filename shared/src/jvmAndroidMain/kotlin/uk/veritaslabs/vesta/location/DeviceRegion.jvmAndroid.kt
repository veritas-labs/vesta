package uk.veritaslabs.vesta.location

actual fun deviceRegionCode(): String? = java.util.Locale.getDefault().country.takeIf { it.isNotBlank() }
