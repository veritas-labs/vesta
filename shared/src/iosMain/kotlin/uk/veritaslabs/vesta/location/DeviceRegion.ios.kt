package uk.veritaslabs.vesta.location

// The area flow is unavailable on iOS (no chunk support), so there is nothing to preselect.
actual fun deviceRegionCode(): String? = null
