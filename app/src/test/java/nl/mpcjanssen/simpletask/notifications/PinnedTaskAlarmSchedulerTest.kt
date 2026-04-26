package nl.mpcjanssen.simpletask.notifications

import android.os.Build
import junit.framework.TestCase

class PinnedTaskAlarmSchedulerTest : TestCase() {
    fun testRequestCodeUsesTaskKeyHash() {
        assertEquals("todo\ntask".hashCode(), PinnedTaskAlarmScheduler.requestCodeFor("todo\ntask"))
    }

    fun testScheduleModeUsesExactAlarmWhenAllowedOnAndroid12Plus() {
        val mode = PinnedTaskAlarmScheduler.scheduleMode(
            apiLevel = Build.VERSION_CODES.S,
            canScheduleExactAlarms = true
        )

        assertEquals(PinnedTaskAlarmScheduleMode.EXACT_ALLOW_WHILE_IDLE, mode)
    }

    fun testScheduleModeFallsBackWhenExactAlarmUnavailableOnAndroid12Plus() {
        val mode = PinnedTaskAlarmScheduler.scheduleMode(
            apiLevel = Build.VERSION_CODES.S,
            canScheduleExactAlarms = false
        )

        assertEquals(PinnedTaskAlarmScheduleMode.ALLOW_WHILE_IDLE, mode)
    }
}
