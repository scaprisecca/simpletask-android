package nl.mpcjanssen.simpletask.calendar

import junit.framework.TestCase
import nl.mpcjanssen.simpletask.task.Task

class CalendarTaskProjectorTest : TestCase() {
    private val projector = CalendarTaskProjector()

    fun testDueTaskProjectsToMonthAndSelectedDay() {
        val projection = projector.project(
            tasks = listOf(Task("Pay rent due:2026-04-10")),
            selectedDate = "2026-04-10",
            visibleMonth = "2026-04",
            visibility = ScheduledDateVisibility.BOTH
        )

        assertTrue(projection.indicatorsByDate["2026-04-10"]?.hasDue == true)
        assertEquals(1, projection.selectedDay.tasks.size)
        assertTrue(projection.selectedDay.tasks.first().matchesDue)
    }

    fun testThresholdOnlyTaskRespectsVisibility() {
        val thresholdTask = Task("Wait t:2026-04-12")

        val hiddenProjection = projector.project(
            tasks = listOf(thresholdTask),
            selectedDate = "2026-04-12",
            visibleMonth = "2026-04",
            visibility = ScheduledDateVisibility.DUE
        )
        assertTrue(hiddenProjection.selectedDay.isEmpty)

        val visibleProjection = projector.project(
            tasks = listOf(thresholdTask),
            selectedDate = "2026-04-12",
            visibleMonth = "2026-04",
            visibility = ScheduledDateVisibility.THRESHOLD
        )
        assertEquals(1, visibleProjection.selectedDay.tasks.size)
        assertTrue(visibleProjection.selectedDay.tasks.first().matchesThreshold)
    }

    fun testTaskWithBothDatesProjectsBothIndicatorsAndSingleDayEntry() {
        val task = Task("Trip due:2026-04-18 t:2026-04-18")

        val projection = projector.project(
            tasks = listOf(task),
            selectedDate = "2026-04-18",
            visibleMonth = "2026-04",
            visibility = ScheduledDateVisibility.BOTH
        )

        assertTrue(projection.indicatorsByDate["2026-04-18"]?.hasDue == true)
        assertTrue(projection.indicatorsByDate["2026-04-18"]?.hasThreshold == true)
        assertEquals(1, projection.selectedDay.tasks.size)
    }

    fun testCompletedHiddenAndMalformedDatesAreIgnored() {
        val completed = Task("x 2026-04-01 Done due:2026-04-10")
        val hidden = Task("Hidden h:1 due:2026-04-10")
        val malformed = Task("Bad due:2026-99-99")

        val projection = projector.project(
            tasks = listOf(completed, hidden, malformed),
            selectedDate = "2026-04-10",
            visibleMonth = "2026-04",
            visibility = ScheduledDateVisibility.BOTH
        )

        assertTrue(projection.selectedDay.isEmpty)
        assertTrue(projection.indicatorsByDate.isEmpty())
    }

    fun testSelectedDayDoesNotPullOverdueTasksFromOtherDates() {
        val projection = projector.project(
            tasks = listOf(
                Task("Old due:2026-04-01"),
                Task("Today due:2026-04-15")
            ),
            selectedDate = "2026-04-15",
            visibleMonth = "2026-04",
            visibility = ScheduledDateVisibility.BOTH
        )

        assertEquals(1, projection.selectedDay.tasks.size)
        assertEquals("Today due:2026-04-15", projection.selectedDay.tasks.first().task.text)
    }
}
