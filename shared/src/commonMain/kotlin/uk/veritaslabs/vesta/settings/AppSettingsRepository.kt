package uk.veritaslabs.vesta.settings

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.ui.theme.AppTheme
import uk.veritaslabs.vesta.ui.theme.ThemeMode

/** How the user has chosen the app to look. */
data class Appearance(val appTheme: AppTheme = AppTheme.Vesta, val themeMode: ThemeMode = ThemeMode.System)

/** The app's device-level preferences, kept in the database so they survive relaunches. Not synced between devices. */
class AppSettingsRepository(private val database: VestaDatabase) {
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

    private suspend fun read(key: String): String? = database.appSettingQueries.selectValue(key).awaitAsOneOrNull()

    private companion object {
        const val ThemeKey = "appTheme"
        const val ThemeModeKey = "themeMode"
        const val UseMobileDataKey = "useMobileData"
    }
}
