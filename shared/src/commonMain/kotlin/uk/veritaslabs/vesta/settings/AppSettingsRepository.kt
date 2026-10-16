package uk.veritaslabs.vesta.settings

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import uk.veritaslabs.vesta.calendar.CalendarSettings
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.ui.theme.AppTheme
import uk.veritaslabs.vesta.ui.theme.ThemeMode

/** How the user has chosen the app to look. */
data class Appearance(val appTheme: AppTheme = AppTheme.Vesta, val themeMode: ThemeMode = ThemeMode.System)

/** The app's device-level preferences, kept in the database so they survive relaunches. Not synced between devices. */
class AppSettingsRepository(private val database: KitsDatabase) {
    /** The saved [Appearance]; anything not saved yet, or no longer recognised, is the default. */
    suspend fun appearance(): Appearance = Appearance(
        appTheme = read(ThemeKey)?.let { name -> AppTheme.entries.firstOrNull { it.name == name } } ?: AppTheme.Vesta,
        themeMode = read(ThemeModeKey)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
    )

    suspend fun setAppTheme(appTheme: AppTheme) = database.appSettingQueries.upsert(ThemeKey, appTheme.name)

    suspend fun setThemeMode(themeMode: ThemeMode) = database.appSettingQueries.upsert(ThemeModeKey, themeMode.name)

    /** Whether the user lets x0x use mobile data; on until they turn it off. */
    suspend fun useMobileData(): Boolean = read(UseMobileDataKey) != false.toString()

    suspend fun setUseMobileData(useMobileData: Boolean) = database.appSettingQueries.upsert(UseMobileDataKey, useMobileData.toString())

    suspend fun calendarSettings(): CalendarSettings {
        val defaults = CalendarSettings()
        return CalendarSettings(
            weekStart = read(CalendarWeekStartKey)?.let { name -> WeekStart.entries.firstOrNull { it.name == name } } ?: defaults.weekStart,
            showWeekNumbers = read(CalendarWeekNumbersKey)?.toBooleanStrictOrNull() ?: defaults.showWeekNumbers,
            showOtherMonthDays = read(CalendarOtherMonthsKey)?.toBooleanStrictOrNull() ?: defaults.showOtherMonthDays,
            startCollapsed = read(CalendarStartCollapsedKey)?.toBooleanStrictOrNull() ?: defaults.startCollapsed,
        )
    }

    suspend fun setCalendarSettings(settings: CalendarSettings) {
        database.appSettingQueries.upsert(CalendarWeekStartKey, settings.weekStart.name)
        database.appSettingQueries.upsert(CalendarWeekNumbersKey, settings.showWeekNumbers.toString())
        database.appSettingQueries.upsert(CalendarOtherMonthsKey, settings.showOtherMonthDays.toString())
        database.appSettingQueries.upsert(CalendarStartCollapsedKey, settings.startCollapsed.toString())
    }

    /** Whether the welcome tour has been shown or deliberately skipped for this install; null until one or the other has happened. */
    suspend fun onboardingSeen(): Boolean? = read(OnboardingSeenKey)?.toBooleanStrictOrNull()

    suspend fun setOnboardingSeen(seen: Boolean) = database.appSettingQueries.upsert(OnboardingSeenKey, seen.toString())

    private suspend fun read(key: String): String? = database.appSettingQueries.selectValue(key).awaitAsOneOrNull()

    private companion object {
        const val ThemeKey = "appTheme"
        const val ThemeModeKey = "themeMode"
        const val UseMobileDataKey = "useMobileData"
        const val CalendarWeekStartKey = "calendarWeekStart"
        const val CalendarWeekNumbersKey = "calendarShowWeekNumbers"
        const val CalendarOtherMonthsKey = "calendarShowOtherMonthDays"
        const val CalendarStartCollapsedKey = "calendarStartCollapsed"
        const val OnboardingSeenKey = "onboardingSeen"
    }
}
