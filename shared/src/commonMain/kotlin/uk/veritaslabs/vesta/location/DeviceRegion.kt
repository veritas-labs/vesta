package uk.veritaslabs.vesta.location

/** The device's region as an upper-case ISO 3166 alpha-2 code ("GB"), or null when it has none. Used only to preselect a country. */
expect fun deviceRegionCode(): String?
