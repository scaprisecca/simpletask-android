package nl.mpcjanssen.simpletask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PinnedNotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TodoApplication.app.loadTodoList("Pinned notification restore: ${intent.action}")
    }
}
