package nl.mpcjanssen.simpletask.notifications

import junit.framework.TestCase

class PinnedTaskDeliveryStateTest : TestCase() {
    fun testScheduledRecordTracksFutureDelivery() {
        val record = PinnedTaskRecord(
            taskKey = "task",
            todoFilePath = "/tmp/todo.txt",
            taskText = "Pay rent",
            createdAt = 1L
        ).asScheduledRecord(triggerAtMillis = 5_000L)

        assertTrue(record.isScheduledDelivery())
        assertTrue(record.isScheduledForFuture(nowMillis = 4_000L))
        assertFalse(record.shouldPostNow(nowMillis = 4_000L))
    }

    fun testScheduledRecordPostsWhenTriggerTimeHasPassed() {
        val record = PinnedTaskRecord(
            taskKey = "task",
            todoFilePath = "/tmp/todo.txt",
            taskText = "Call bank",
            createdAt = 1L
        ).asScheduledRecord(triggerAtMillis = 5_000L)

        assertTrue(record.shouldPostNow(nowMillis = 5_000L))
        assertTrue(record.shouldPostNow(nowMillis = 6_000L))
    }

    fun testPostedRecordPreservesScheduledTriggerMetadata() {
        val record = PinnedTaskRecord(
            taskKey = "task",
            todoFilePath = "/tmp/todo.txt",
            taskText = "Renew license",
            createdAt = 1L
        ).asScheduledRecord(triggerAtMillis = 7_000L).asPostedRecord("Renew license")

        assertTrue(record.isPostedDelivery())
        assertEquals(PinnedTaskRecord.TRIGGER_MODE_SCHEDULED, record.triggerMode)
        assertEquals(7_000L, record.triggerAtMillis)
    }

    fun testDisplayStateUsesScheduledMarkerOnlyForFuturePins() {
        val scheduled = PinnedTaskRecord(
            taskKey = "task",
            todoFilePath = "/tmp/todo.txt",
            taskText = "Call bank",
            createdAt = 1L
        ).asScheduledRecord(triggerAtMillis = 5_000L)
        val posted = scheduled.asPostedRecord("Call bank")

        assertEquals(PinnedTaskDisplayState.SCHEDULED, scheduled.displayState(nowMillis = 4_000L))
        assertEquals(PinnedTaskDisplayState.PINNED, posted.displayState(nowMillis = 4_000L))
    }

    fun testDecorateTaskTextAddsVisualIndicatorWithoutChangingUnderlyingTask() {
        assertEquals("📌 Check smoke alarms", decoratePinnedTaskText("Check smoke alarms", PinnedTaskDisplayState.PINNED))
        assertEquals("⏰ Check smoke alarms", decoratePinnedTaskText("Check smoke alarms", PinnedTaskDisplayState.SCHEDULED))
        assertEquals("Check smoke alarms", decoratePinnedTaskText("Check smoke alarms", PinnedTaskDisplayState.NONE))
    }
}
