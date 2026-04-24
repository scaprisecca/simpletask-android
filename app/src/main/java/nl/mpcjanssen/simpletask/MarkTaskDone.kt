package nl.mpcjanssen.simpletask

import android.content.Intent
import android.util.Log
import android.app.Service
import android.os.IBinder
import kotlin.concurrent.thread

class MarkTaskDone : Service() {
    val TAG = "MarkTaskDone"

    public override fun onStartCommand (intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")
        val taskKey = intent.getStringExtra(Constants.EXTRA_PINNED_TASK_KEY)
        if (taskKey == null) {
            Log.e(TAG, "'${Constants.EXTRA_PINNED_TASK_KEY}' not found in intent: $intent")
            return START_STICKY_COMPATIBILITY
        }
        Log.d(TAG, "task key: $taskKey")
        thread(start = true) {
            if (!TodoApplication.pinnedTaskNotifications.completeTaskFromNotification(taskKey)) {
                Log.e(TAG, "task with key '$taskKey' not found in todo list")
            }
            stopSelfResult(startId)
        }
        return START_STICKY_COMPATIBILITY
    }

    public override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
