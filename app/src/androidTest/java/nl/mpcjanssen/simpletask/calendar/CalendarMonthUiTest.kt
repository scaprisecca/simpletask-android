package nl.mpcjanssen.simpletask.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import nl.mpcjanssen.simpletask.CalendarTestSupport
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class CalendarMonthUiTest {
    @Test
    fun launchSimpletaskForCalendarMonthUi() {
        CalendarTestSupport.launchSimpletask().close()
    }
}
