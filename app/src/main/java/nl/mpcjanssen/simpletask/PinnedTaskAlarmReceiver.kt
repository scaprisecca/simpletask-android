package nl.mpcjanssen.simpletask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PinnedTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        TodoApplication.pinnedTaskNotifications.handleScheduledTrigger(
            taskKey = intent.getStringExtra(Constants.EXTRA_PINNED_TASK_KEY),
            onComplete = { pendingResult.finish() }
        )
    }
}
