package nl.mpcjanssen.simpletask

import androidx.test.core.app.ActivityScenario

object CalendarTestSupport {
    fun launchSimpletask(): ActivityScenario<Simpletask> {
        return ActivityScenario.launch(Simpletask::class.java)
    }
}
