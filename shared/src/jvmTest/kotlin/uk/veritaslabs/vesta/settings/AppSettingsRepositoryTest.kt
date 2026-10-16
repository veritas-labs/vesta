package uk.veritaslabs.vesta.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.theme.AppTheme
import uk.veritaslabs.vesta.ui.theme.ThemeMode

class AppSettingsRepositoryTest {

    @Test
    fun startsWithTheVestaThemeFollowingTheSystem() = runTest {
        // Arrange
        val repository = AppSettingsRepository(openInMemoryDatabase())

        // Act
        val appearance = repository.appearance()

        // Assert
        assertEquals(Appearance(AppTheme.Vesta, ThemeMode.System), appearance)
    }

    @Test
    fun remembersTheChosenThemeAndModeForALaterRepositoryOverTheSameDatabase() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = AppSettingsRepository(db)

        // Act
        repository.setAppTheme(AppTheme.Halloween)
        repository.setThemeMode(ThemeMode.Dark)
        val appearance = AppSettingsRepository(db).appearance()

        // Assert
        assertEquals(Appearance(AppTheme.Halloween, ThemeMode.Dark), appearance)
    }

    @Test
    fun aLaterChoiceReplacesTheEarlierOne() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = AppSettingsRepository(db)
        repository.setThemeMode(ThemeMode.Dark)

        // Act
        repository.setThemeMode(ThemeMode.Light)

        // Assert
        assertEquals(ThemeMode.Light, repository.appearance().themeMode)
    }

    @Test
    fun mobileDataIsOnUntilTheUserTurnsItOffAndThenStaysOff() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val initial = AppSettingsRepository(db).useMobileData()

        // Act
        AppSettingsRepository(db).setUseMobileData(false)
        val afterOff = AppSettingsRepository(db).useMobileData()
        AppSettingsRepository(db).setUseMobileData(true)
        val afterOn = AppSettingsRepository(db).useMobileData()

        // Assert
        assertEquals(listOf(true, false, true), listOf(initial, afterOff, afterOn))
    }

    @Test
    fun theWelcomeTourIsNeitherSeenNorSkippedOnAFreshInstall() = runTest {
        // Arrange
        val repository = AppSettingsRepository(openInMemoryDatabase())

        // Act
        val seen = repository.onboardingSeen()

        // Assert
        assertNull(seen)
    }

    @Test
    fun remembersThatTheWelcomeTourWasSeenForALaterRepositoryOverTheSameDatabase() = runTest {
        // Arrange
        val db = openInMemoryDatabase()

        // Act
        AppSettingsRepository(db).setOnboardingSeen(true)
        val seen = AppSettingsRepository(db).onboardingSeen()

        // Assert
        assertEquals(true, seen)
    }
}
