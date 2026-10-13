package uk.veritaslabs.vesta.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.WeekStart

class MealPlannerLogicTest {

    // 2026-10-14 is a Wednesday.
    private val wednesday = CalendarDate(2026, 10, 14)

    @Test
    fun aMondayWeekRunsFromMondayToSunday() {
        // Arrange
        val anchor = wednesday

        // Act
        val days = weekDays(anchor, WeekStart.Monday)

        // Assert
        assertEquals(CalendarDate(2026, 10, 12), days.first())
        assertEquals(CalendarDate(2026, 10, 18), days.last())
        assertEquals(7, days.size)
    }

    @Test
    fun aSundayWeekRunsFromSundayToSaturday() {
        // Arrange
        val anchor = wednesday

        // Act
        val days = weekDays(anchor, WeekStart.Sunday)

        // Assert
        assertEquals(CalendarDate(2026, 10, 11), days.first())
        assertEquals(CalendarDate(2026, 10, 17), days.last())
    }

    @Test
    fun theFirstDayOfTheWeekIsItsOwnWeekStart() {
        // Arrange
        val monday = CalendarDate(2026, 10, 12)

        // Act
        val days = weekDays(monday, WeekStart.Monday)

        // Assert
        assertEquals(monday, days.first())
    }

    @Test
    fun aWeekCanCrossAMonthAndAYear() {
        // Arrange
        val newYearsDay = CalendarDate(2027, 1, 1) // a Friday

        // Act
        val days = weekDays(newYearsDay, WeekStart.Monday)

        // Assert
        assertEquals(CalendarDate(2026, 12, 28), days.first())
        assertEquals(CalendarDate(2027, 1, 3), days.last())
    }

    private val pie = Recipe(key = "pie", title = "Shepherd's Pie", tags = listOf("Comfort"))
    private val crumble = Recipe(key = "crumble", title = "apple Crumble")
    private val soup = Recipe(key = "soup", title = "Soup", tags = listOf("Quick"))

    @Test
    fun pickableRecipesLeaveOutThoseAlreadyInTheSlotAndSortByTitle() {
        // Arrange
        val recipes = listOf(pie, soup, crumble)

        // Act
        val pickable = pickableRecipes(recipes, planned = listOf("soup"), query = "")

        // Assert
        assertEquals(listOf("crumble", "pie"), pickable.map { it.key })
    }

    @Test
    fun pickableRecipesMatchTheQueryAgainstTitleOrTagIgnoringCase() {
        // Arrange
        val recipes = listOf(pie, soup, crumble)

        // Act
        val byTitle = pickableRecipes(recipes, emptyList(), "  CRUMB ")
        val byTag = pickableRecipes(recipes, emptyList(), "quick")

        // Assert
        assertEquals(listOf("crumble"), byTitle.map { it.key })
        assertEquals(listOf("soup"), byTag.map { it.key })
    }
}
