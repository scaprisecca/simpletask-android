package nl.mpcjanssen.simpletask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PinnedNotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TodoApplication.pinnedTaskNotifications.reconcileWithCurrentTodoList(
            "Pinned notification restore from cached task list: ${intent.action}"
        )
        TodoApplication.app.loadTodoList("Pinned notification restore: ${intent.action}")
    }
}
