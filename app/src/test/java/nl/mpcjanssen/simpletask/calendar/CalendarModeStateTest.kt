package nl.mpcjanssen.simpletask.calendar

import junit.framework.TestCase

class CalendarModeStateTest : TestCase() {
    fun testScheduledDateVisibilityFallsBackToBoth() {
        assertEquals(ScheduledDateVisibility.BOTH, ScheduledDateVisibility.fromStoredValue(null))
        assertEquals(ScheduledDateVisibility.BOTH, ScheduledDateVisibility.fromStoredValue("bogus"))
    }

    fun testCalendarModeStateRoundTripsThroughMap() {
        val queryJson = """
            {
              "query":"calendar",
              "hide_completed":true,
              "hide_hidden":true
            }
        """.trimIndent()
        val original = CalendarModeState(
            active = true,
            selectedDate = "2026-04-04",
            visibleMonth = "2026-04",
            listSnapshot = CalendarListSnapshot(
                mainQueryJson = queryJson,
                scrollPosition = 7,
                scrollOffset = 21
            )
        )

        val restored = CalendarModeState.fromMap(original.toMap())

        assertEquals(original, restored)
        assertEquals(queryJson, restored?.listSnapshot?.mainQueryJson)
    }

    fun testExitClearsActiveCalendarStateAndReturnsListSnapshot() {
        val snapshot = CalendarListSnapshot(
            mainQueryJson = "{}",
            scrollPosition = 3,
            scrollOffset = 14
        )
        val state = CalendarModeState(
            active = true,
            selectedDate = "2026-04-21",
            visibleMonth = "2026-04",
            listSnapshot = snapshot
        )

        val result = CalendarModeTransitions.exit(state)

        assertEquals(CalendarModeState(), result.nextState)
        assertEquals(snapshot, result.restoredListSnapshot)
    }
}
