package nl.mpcjanssen.simpletask.dates

import junit.framework.TestCase
import nl.mpcjanssen.simpletask.task.Task
import java.util.Locale
import java.util.TimeZone

class DateLensClassifierTest : TestCase() {
    private fun classifier(
        today: String = "2026-04-21",
        weekStartMode: WeekStartMode = WeekStartMode.MONDAY,
        upcomingWindowDays: Int = 14
    ) = DateLensClassifier(
        today = today,
        weekStartMode = weekStartMode,
        upcomingWindowDays = upcomingWindowDays,
        locale = Locale.US,
        timeZone = TimeZone.getTimeZone("UTC")
    )

    fun testOverdueDueDateOutranksThresholdMatches() {
        val result = classifier().classify(Task("Mixed due:2026-04-20 t:2026-04-21"))

        assertEquals(DateLens.OVERDUE, result.primaryLens)
        assertEquals(DateLensMatchReason.DUE, result.reason)
        assertEquals(OverdueDateType.DUE, result.overdueDateType)
    }

    fun testOverdueThresholdIsDistinguished() {
        val result = classifier().classify(Task("Start t:2026-04-20"))

        assertEquals(DateLens.OVERDUE, result.primaryLens)
        assertEquals(DateLensMatchReason.THRESHOLD, result.reason)
        assertEquals(OverdueDateType.THRESHOLD, result.overdueDateType)
    }

    fun testTodayIncludesDueAndThresholdToday() {
        assertEquals(DateLens.TODAY, classifier().classify(Task("Due due:2026-04-21")).primaryLens)
        assertEquals(DateLens.TODAY, classifier().classify(Task("Start t:2026-04-21 due:2026-04-30")).primaryLens)
    }

    fun testThisWeekExcludesOverdueAndIncludesDatedTasksOnly() {
        val lens = classifier()

        assertEquals(DateLens.THIS_WEEK, lens.classify(Task("Friday due:2026-04-24")).primaryLens)
        assertEquals(DateLens.THIS_WEEK, lens.classify(Task("Friday start t:2026-04-24")).primaryLens)
        assertEquals(DateLens.OVERDUE, lens.classify(Task("Old due:2026-04-20")).primaryLens)
        assertNull(lens.classify(Task("No date +work")).primaryLens)
    }

    fun testUpcomingStartsAfterThisWeekAndUsesConfiguredWindow() {
        val lens = classifier(upcomingWindowDays = 14)

        assertEquals(DateLens.UPCOMING, lens.classify(Task("Next week due:2026-04-28")).primaryLens)
        assertEquals(DateLens.UPCOMING, lens.classify(Task("Window edge t:2026-05-05")).primaryLens)
        assertNull(lens.classify(Task("Too far due:2026-05-06")).primaryLens)
    }

    fun testWeekStartOverrideChangesThisWeekBoundary() {
        val sundayWeek = classifier(today = "2026-04-19", weekStartMode = WeekStartMode.SUNDAY)
        val mondayWeek = classifier(today = "2026-04-19", weekStartMode = WeekStartMode.MONDAY)

        assertEquals(DateLens.THIS_WEEK, sundayWeek.classify(Task("Saturday due:2026-04-25")).primaryLens)
        assertEquals(DateLens.UPCOMING, mondayWeek.classify(Task("Saturday due:2026-04-25")).primaryLens)
    }

    fun testDueOutranksThresholdWhenBothMatchDifferentFutureLenses() {
        val result = classifier().classify(Task("Due this week, starts upcoming due:2026-04-24 t:2026-04-28"))

        assertEquals(DateLens.THIS_WEEK, result.primaryLens)
        assertEquals(DateLensMatchReason.DUE, result.reason)
    }

    fun testAllLensMatchesEveryTask() {
        assertTrue(classifier().matches(Task("No date"), DateLens.ALL))
        assertFalse(classifier().matches(Task("No date"), DateLens.TODAY))
    }
}

