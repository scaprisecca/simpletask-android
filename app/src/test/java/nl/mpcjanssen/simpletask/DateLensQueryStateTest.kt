package nl.mpcjanssen.simpletask

import junit.framework.TestCase
import nl.mpcjanssen.simpletask.dates.DateLens

class DateLensQueryStateTest : TestCase() {
    fun testDateLensStoredValueParsesSafely() {
        assertEquals(DateLens.TODAY, DateLens.fromStoredValue("today"))
        assertEquals(DateLens.ALL, DateLens.fromStoredValue(null))
        assertEquals(DateLens.ALL, DateLens.fromStoredValue("unknown"))
    }

    fun testMissingDateLensDefaultsToAll() {
        val query = Query("test")

        assertEquals(DateLens.ALL, query.dateLens)
        assertFalse(query.hasFilter())
    }

    fun testClearRestoresAllLens() {
        val query = Query("test")
        query.dateLens = DateLens.UPCOMING

        query.clear()

        assertEquals(DateLens.ALL, query.dateLens)
        assertFalse(query.hasFilter())
    }

    fun testTitleShowsActiveDateLens() {
        val query = Query("test")
        query.dateLens = DateLens.OVERDUE

        val title = query.getTitle(
            visible = 2,
            total = 5,
            prio = "Priority",
            tag = "Tag",
            list = "List",
            search = "Search",
            script = "Script",
            dateLensTitle = "Overdue",
            filterApplied = "Filter applied",
            noFilter = "No filter"
        )

        assertEquals("(2/5) Filter applied Overdue", title)
    }
}

