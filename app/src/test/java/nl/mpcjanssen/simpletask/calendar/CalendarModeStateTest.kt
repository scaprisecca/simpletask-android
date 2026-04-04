package nl.mpcjanssen.simpletask.calendar

import junit.framework.TestCase
import nl.mpcjanssen.simpletask.Query
import org.json.JSONObject

class CalendarModeStateTest : TestCase() {
    fun testScheduledDateVisibilityFallsBackToBoth() {
        assertEquals(ScheduledDateVisibility.BOTH, ScheduledDateVisibility.fromStoredValue(null))
        assertEquals(ScheduledDateVisibility.BOTH, ScheduledDateVisibility.fromStoredValue("bogus"))
    }

    fun testCalendarModeStateRoundTripsThroughMap() {
        val queryJson = Query("mainui").apply {
            search = "calendar"
            hideCompleted = true
        }.saveInJSON(JSONObject()).toString()
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
        assertEquals("calendar", restored?.listSnapshot?.restoreQuery()?.search)
        assertTrue(restored?.listSnapshot?.restoreQuery()?.hideCompleted == true)
    }
}
