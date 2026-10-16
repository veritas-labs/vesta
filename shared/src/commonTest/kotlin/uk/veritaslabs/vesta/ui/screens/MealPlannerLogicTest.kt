package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.spaces.MemberRole
import uk.veritaslabs.kits.spaces.MemberListItem
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

    private val mark = MemberListItem("mark", "Mark", MemberRole.Admin)
    private val sam = MemberListItem("sam", "Sam", MemberRole.Member)
    private val ella = MealGuest("ella", "Ella")
    private val noah = MealGuest("noah", "Noah")
    private val dinner = CalendarDate(2026, 10, 12)

    @Test
    fun peopleAreTheMembersInOrderThenTheGuestsByName() {
        // Arrange
        val members = listOf(sam, mark)

        // Act
        val people = mealPeople(members, listOf(noah, ella))

        // Assert
        assertEquals(listOf("m:sam", "m:mark", "p:ella", "p:noah"), people.map { it.ref })
        assertEquals(listOf(true, true, false, false), people.map { it.isMember })
    }

    @Test
    fun aRecipeNobodyChoseEatersForIsEatenByEveryMemberAndNoGuests() {
        // Arrange
        val people = mealPeople(listOf(mark, sam), listOf(ella))

        // Act
        val eaters = eatersOf(MealPlan(guests = listOf(ella)), dinner, MealSlot.Dinner, "pie", people)

        // Assert
        assertEquals(listOf("Mark", "Sam"), eaters.map { it.name })
    }

    @Test
    fun aMemberWhoJoinsLaterEatsTheRecipesNobodyChose() {
        // Arrange
        val plan = MealPlan()

        // Act
        val before = eatersOf(plan, dinner, MealSlot.Dinner, "pie", mealPeople(listOf(mark), emptyList()))
        val after = eatersOf(plan, dinner, MealSlot.Dinner, "pie", mealPeople(listOf(mark, sam), emptyList()))

        // Assert
        assertEquals(listOf("Mark"), before.map { it.name })
        assertEquals(listOf("Mark", "Sam"), after.map { it.name })
    }

    @Test
    fun aChosenRecipeIsEatenByJustThoseChosen() {
        // Arrange
        val people = mealPeople(listOf(mark, sam), listOf(ella, noah))
        val plan = MealPlan(eaters = mapOf(PlannedMeal(dinner, MealSlot.Dinner, "pie") to setOf("m:sam", "p:ella")))

        // Act
        val eaters = eatersOf(plan, dinner, MealSlot.Dinner, "pie", people)

        // Assert
        assertEquals(listOf("Sam", "Ella"), eaters.map { it.name })
    }

    @Test
    fun aChosenPersonWhoHasGoneIsDropped() {
        // Arrange
        val people = mealPeople(listOf(mark), emptyList())
        val plan = MealPlan(eaters = mapOf(PlannedMeal(dinner, MealSlot.Dinner, "pie") to setOf("m:mark", "m:gone", "p:removed")))

        // Act
        val eaters = eatersOf(plan, dinner, MealSlot.Dinner, "pie", people)

        // Assert
        assertEquals(listOf("Mark"), eaters.map { it.name })
    }

    @Test
    fun anotherRecipeInTheSameSlotKeepsTheDefault() {
        // Arrange
        val people = mealPeople(listOf(mark, sam), emptyList())
        val plan = MealPlan(eaters = mapOf(PlannedMeal(dinner, MealSlot.Dinner, "pie") to setOf("m:mark")))

        // Act
        val mash = eatersOf(plan, dinner, MealSlot.Dinner, "mash", people)

        // Assert
        assertEquals(listOf("Mark", "Sam"), mash.map { it.name })
    }
}
