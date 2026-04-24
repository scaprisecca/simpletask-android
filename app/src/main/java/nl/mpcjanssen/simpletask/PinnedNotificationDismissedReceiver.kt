package nl.mpcjanssen.simpletask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PinnedNotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TodoApplication.pinnedTaskNotifications.reopenDismissedNotification(
            intent.getStringExtra(Constants.EXTRA_PINNED_TASK_KEY)
        )
    }
}
