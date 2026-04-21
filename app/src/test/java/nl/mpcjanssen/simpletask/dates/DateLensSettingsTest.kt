package nl.mpcjanssen.simpletask.dates

import junit.framework.TestCase

class DateLensSettingsTest : TestCase() {
    fun testWeekStartModeDefaultsSafely() {
        assertEquals(WeekStartMode.LOCALE, WeekStartMode.fromStoredValue(null))
        assertEquals(WeekStartMode.LOCALE, WeekStartMode.fromStoredValue("unknown"))
        assertEquals(WeekStartMode.SUNDAY, WeekStartMode.fromStoredValue("sunday"))
        assertEquals(WeekStartMode.MONDAY, WeekStartMode.fromStoredValue("monday"))
    }

    fun testUpcomingWindowIsBounded() {
        assertEquals(1, DateLensClassifier.normalizeUpcomingWindowDays(-5))
        assertEquals(14, DateLensClassifier.normalizeUpcomingWindowDays(14))
        assertEquals(365, DateLensClassifier.normalizeUpcomingWindowDays(999))
    }
}

