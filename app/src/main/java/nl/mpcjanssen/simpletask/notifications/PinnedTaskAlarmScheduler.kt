package nl.mpcjanssen.simpletask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import nl.mpcjanssen.simpletask.Constants
import nl.mpcjanssen.simpletask.PinnedTaskAlarmReceiver

enum class PinnedTaskAlarmScheduleMode {
    EXACT_ALLOW_WHILE_IDLE,
    ALLOW_WHILE_IDLE,
    EXACT,
    INEXACT
}

class PinnedTaskAlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
) {
    fun schedule(record: PinnedTaskRecord) {
        val triggerAtMillis = record.triggerAtMillis ?: return
        val pendingIntent = pendingIntent(record)
        when (scheduleMode(Build.VERSION.SDK_INT, canScheduleExactAlarms())) {
            PinnedTaskAlarmScheduleMode.EXACT_ALLOW_WHILE_IDLE ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            PinnedTaskAlarmScheduleMode.ALLOW_WHILE_IDLE ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            PinnedTaskAlarmScheduleMode.EXACT ->
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            PinnedTaskAlarmScheduleMode.INEXACT ->
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(record: PinnedTaskRecord) {
        val pendingIntent = pendingIntent(record)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(record: PinnedTaskRecord): PendingIntent {
        val intent = Intent(context, PinnedTaskAlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_PINNED_TASK_KEY, record.taskKey)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(record.taskKey),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    companion object {
        fun requestCodeFor(taskKey: String): Int = taskKey.hashCode()

        fun scheduleMode(apiLevel: Int, canScheduleExactAlarms: Boolean): PinnedTaskAlarmScheduleMode {
            return when {
                apiLevel >= Build.VERSION_CODES.S && canScheduleExactAlarms ->
                    PinnedTaskAlarmScheduleMode.EXACT_ALLOW_WHILE_IDLE
                apiLevel >= Build.VERSION_CODES.S ->
                    PinnedTaskAlarmScheduleMode.ALLOW_WHILE_IDLE
                apiLevel >= Build.VERSION_CODES.M ->
                    PinnedTaskAlarmScheduleMode.EXACT_ALLOW_WHILE_IDLE
                apiLevel >= Build.VERSION_CODES.KITKAT ->
                    PinnedTaskAlarmScheduleMode.EXACT
                else ->
                    PinnedTaskAlarmScheduleMode.INEXACT
            }
        }
    }
}
