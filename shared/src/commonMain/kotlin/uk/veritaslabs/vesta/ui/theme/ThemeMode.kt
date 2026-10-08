package uk.veritaslabs.vesta.ui.theme

/** Whether the app is light, dark, or follows the system. */
enum class ThemeMode {
    Light, Dark, System;

    /** Whether the app is drawn dark in this mode, given whether the system is ([systemDark]). */
    fun isDark(systemDark: Boolean) = when (this) {
        Light -> false
        Dark -> true
        System -> systemDark
    }
}

/** The look the app is drawn in. Halloween is always dark and ignores [ThemeMode]. */
enum class AppTheme { Vesta, Halloween }
