package nl.mpcjanssen.simpletask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PinnedNotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Log.i(TAG, "Starting pinned notification restore for ${intent.action}")
        TodoApplication.pinnedTaskNotifications.restorePinnedNotifications(
            reason = "Pinned notification restore: ${intent.action}",
            onComplete = {
                Log.i(TAG, "Finished pinned notification restore for ${intent.action}")
                pendingResult.finish()
            }
        )
    }

    companion object {
        private const val TAG = "PinnedNotificationBoot"
    }
}
