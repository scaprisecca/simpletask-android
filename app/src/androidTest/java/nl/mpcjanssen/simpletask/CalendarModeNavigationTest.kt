package nl.mpcjanssen.simpletask

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class CalendarModeNavigationTest {
    @Test
    fun launchSimpletaskForCalendarNavigation() {
        CalendarTestSupport.launchSimpletask().close()
    }
}
