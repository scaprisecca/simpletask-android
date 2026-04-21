package nl.mpcjanssen.simpletask

import junit.framework.TestCase
import nl.mpcjanssen.simpletask.dates.DateLens
import nl.mpcjanssen.simpletask.dates.WeekStartMode
import nl.mpcjanssen.simpletask.task.Task

class DateLensQueryApplicationTest : TestCase() {
    private val tasks = listOf(
        Task("Old due:2026-04-20"),
        Task("Today due:2026-04-21"),
        Task("Week due:2026-04-24"),
        Task("Upcoming due:2026-04-28"),
        Task("No date +home")
    )

    private fun apply(lens: DateLens): List<String> {
        val query = Query("test")
        query.dateLens = lens
        return query.applyFilter(
            items = tasks,
            showSelected = false,
            today = "2026-04-21",
            weekStartMode = WeekStartMode.MONDAY,
            upcomingWindowDays = 14
        ).map { it.text }
    }

    fun testAllLensKeepsNormalUnscopedList() {
        assertEquals(tasks.map { it.text }, apply(DateLens.ALL))
    }

    fun testActiveLensFiltersTaskSet() {
        assertEquals(listOf("Old due:2026-04-20"), apply(DateLens.OVERDUE))
        assertEquals(listOf("Today due:2026-04-21"), apply(DateLens.TODAY))
        assertEquals(listOf("Week due:2026-04-24"), apply(DateLens.THIS_WEEK))
        assertEquals(listOf("Upcoming due:2026-04-28"), apply(DateLens.UPCOMING))
    }

    fun testExistingStructuralFiltersStillApplyWithNoLens() {
        val query = Query("test")
        query.projects.add("home")

        val result = query.applyFilter(
            items = tasks,
            showSelected = false,
            today = "2026-04-21",
            weekStartMode = WeekStartMode.MONDAY,
            upcomingWindowDays = 14
        )

        assertEquals(listOf("No date +home"), result.map { it.text })
    }
}

