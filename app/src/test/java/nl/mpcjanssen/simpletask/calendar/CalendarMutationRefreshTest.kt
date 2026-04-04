package nl.mpcjanssen.simpletask.calendar

import junit.framework.TestCase
import nl.mpcjanssen.simpletask.task.Task

class CalendarMutationRefreshTest : TestCase() {
    fun testProjectionRefreshesWhenTaskDateChanges() {
        val projector = CalendarTaskProjector()
        val task = Task("Move me due:2026-04-10")

        val original = projector.project(
            tasks = listOf(task),
            selectedDate = "2026-04-10",
            visibleMonth = "2026-04",
            visibility = ScheduledDateVisibility.BOTH
        )
        assertTrue(original.indicatorsByDate.containsKey("2026-04-10"))

        task.dueDate = "2026-04-12"
        val updated = projector.project(
            tasks = listOf(task),
            selectedDate = "2026-04-12",
            visibleMonth = "2026-04",
            visibility = ScheduledDateVisibility.BOTH
        )

        assertFalse(updated.indicatorsByDate.containsKey("2026-04-10"))
        assertTrue(updated.indicatorsByDate.containsKey("2026-04-12"))
    }
}
