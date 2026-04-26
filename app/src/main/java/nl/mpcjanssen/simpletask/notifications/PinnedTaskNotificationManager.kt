package nl.mpcjanssen.simpletask.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nl.mpcjanssen.simpletask.Constants
import nl.mpcjanssen.simpletask.MarkTaskDone
import nl.mpcjanssen.simpletask.PinnedNotificationDismissedReceiver
import nl.mpcjanssen.simpletask.R
import nl.mpcjanssen.simpletask.Simpletask
import nl.mpcjanssen.simpletask.TodoApplication
import nl.mpcjanssen.simpletask.UnpinTaskNotification
import nl.mpcjanssen.simpletask.remote.FileStore
import nl.mpcjanssen.simpletask.task.Priority
import nl.mpcjanssen.simpletask.task.Task
import nl.mpcjanssen.simpletask.util.broadcastRefreshWidgets
import nl.mpcjanssen.simpletask.util.todayAsString
import java.io.File
import java.util.Date
import java.util.concurrent.Executors

class PinnedTaskNotificationManager(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val taskResolver = PinnedTaskTaskResolver()
    private val alarmScheduler = PinnedTaskAlarmScheduler(context)

    @Volatile
    private var pinnedTaskKeys: Set<String> = emptySet()

    init {
        executor.execute {
            refreshPinnedTaskKeys()
        }
    }

    fun isPinned(task: Task): Boolean {
        val todoFilePath = currentTodoFilePath() ?: return false
        return pinnedTaskKeys.contains(PinnedTaskKey.from(todoFilePath, task.text))
    }

    fun pinTask(task: Task, triggerAtMillis: Long? = null) {
        val todoFilePath = currentTodoFilePath() ?: return
        val taskKey = PinnedTaskKey.from(todoFilePath, task.text)
        pinnedTaskKeys = pinnedTaskKeys + taskKey
        executor.execute {
            val dao = TodoApplication.db.pinnedTaskRecordDao()
            val existing = dao.get(taskKey)
            existing?.let { cancelAlarmAndNotification(it) }
            val baseRecord = createRecord(todoFilePath, task.text)
            val record = if (triggerAtMillis != null && triggerAtMillis > System.currentTimeMillis()) {
                baseRecord.asScheduledRecord(triggerAtMillis)
            } else {
                baseRecord.asImmediatePostedRecord(task.text)
            }
            dao.upsert(record)
            deliverRecord(record)
            refreshPinnedTaskKeys()
        }
    }

    fun unpinTask(task: Task) {
        val todoFilePath = currentTodoFilePath() ?: return
        unpinTaskByKey(PinnedTaskKey.from(todoFilePath, task.text))
    }

    fun unpinTaskByKey(taskKey: String) {
        pinnedTaskKeys = pinnedTaskKeys - taskKey
        executor.execute {
            val dao = TodoApplication.db.pinnedTaskRecordDao()
            val record = dao.get(taskKey)
            dao.deleteByTaskKey(taskKey)
            record?.let { cancelAlarmAndNotification(it) }
            refreshPinnedTaskKeys()
        }
    }

    fun retargetPinnedTaskForTaskEdit(originalTaskText: String, updatedTaskText: String) {
        val todoFilePath = currentTodoFilePath() ?: return
        executor.execute {
            val dao = TodoApplication.db.pinnedTaskRecordDao()
            val oldKey = PinnedTaskKey.from(todoFilePath, originalTaskText)
            val record = dao.get(oldKey) ?: return@execute
            val updated = PinnedTaskRecordEditor.retargetForTaskTextEdit(record, originalTaskText, updatedTaskText)
                ?: return@execute
            cancelAlarmAndNotification(record)
            dao.deleteByTaskKey(oldKey)
            dao.upsert(updated)
            pinnedTaskKeys = (pinnedTaskKeys - oldKey) + updated.taskKey
            deliverRecord(updated)
            refreshPinnedTaskKeys()
        }
    }

    fun reconcileWithCurrentTodoList(reason: String) {
        executor.execute {
            reconcileAllRecords(reason)
        }
    }

    fun restorePinnedNotifications(reason: String, onComplete: (() -> Unit)? = null) {
        executor.execute {
            try {
                reconcileAllRecords(reason)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun handleScheduledTrigger(taskKey: String?, onComplete: (() -> Unit)? = null) {
        if (taskKey.isNullOrEmpty()) {
            onComplete?.invoke()
            return
        }
        executor.execute {
            try {
                val dao = TodoApplication.db.pinnedTaskRecordDao()
                val record = dao.get(taskKey) ?: return@execute
                if (!record.isScheduledDelivery()) {
                    return@execute
                }
                val resolution = try {
                    resolveRecord(record)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to post scheduled pinned task $taskKey", e)
                    return@execute
                } ?: run {
                    dao.deleteByTaskKey(taskKey)
                    cancelAlarmAndNotification(record)
                    refreshPinnedTaskKeys()
                    return@execute
                }
                val updated = record.asPostedRecord(resolution.task.text)
                dao.upsert(updated)
                postNotification(updated)
                refreshPinnedTaskKeys()
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun reopenDismissedNotification(taskKey: String?) {
        if (taskKey.isNullOrEmpty()) {
            return
        }
        executor.execute {
            TodoApplication.db.pinnedTaskRecordDao().get(taskKey)?.let { record ->
                if (record.isPostedDelivery()) {
                    postNotification(record)
                }
            }
        }
    }

    fun completeTaskFromNotification(taskKey: String): Boolean {
        val dao = TodoApplication.db.pinnedTaskRecordDao()
        val record = dao.get(taskKey) ?: return false
        val resolution = try {
            resolveRecord(record)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to complete pinned task $taskKey", e)
            return false
        }
        if (resolution == null) {
            dao.deleteByTaskKey(taskKey)
            refreshPinnedTaskKeys()
            cancelAlarmAndNotification(record)
            return false
        }

        val completed = if (resolution.usesActiveTodoFile) {
            completeTaskInActiveTodoList(record, resolution.task)
        } else {
            completeTaskInExternalFile(record)
        }
        if (!completed) {
            return false
        }

        dao.deleteByTaskKey(taskKey)
        refreshPinnedTaskKeys()
        cancelAlarmAndNotification(record)
        broadcastRefreshWidgets(TodoApplication.app.localBroadCastManager)
        return true
    }

    fun findTaskForPinnedKey(taskKey: String): Task? {
        val record = TodoApplication.db.pinnedTaskRecordDao().get(taskKey) ?: return null
        return try {
            resolveRecord(record)?.task
        } catch (e: Exception) {
            Log.w(TAG, "Unable to find pinned task $taskKey", e)
            null
        }
    }

    private fun refreshPinnedTaskKeys() {
        pinnedTaskKeys = TodoApplication.db.pinnedTaskRecordDao().getAll().map { it.taskKey }.toSet()
    }

    private fun reconcileAllRecords(reason: String) {
        val dao = TodoApplication.db.pinnedTaskRecordDao()
        val records = dao.getAll()
        Log.i(TAG, "Reconciling pinned notifications for $reason")
        records.forEach { record ->
            try {
                val resolution = resolveRecord(record)
                if (resolution == null) {
                    dao.deleteByTaskKey(record.taskKey)
                    cancelAlarmAndNotification(record)
                } else {
                    val updated = record.copy(
                        taskText = resolution.task.text,
                        lastKnownText = resolution.task.text
                    )
                    if (updated != record) {
                        dao.upsert(updated)
                    }
                    deliverRecord(updated)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping pinned-task reconcile for ${record.taskKey}", e)
            }
        }
        refreshPinnedTaskKeys()
    }

    private fun resolveRecord(record: PinnedTaskRecord): PinnedTaskResolution? {
        return taskResolver.resolve(record)
    }

    private fun deliverRecord(record: PinnedTaskRecord) {
        cancelAlarmAndNotification(record)
        if (record.isScheduledForFuture()) {
            alarmScheduler.schedule(record)
            return
        }
        val postedRecord = if (record.isPostedDelivery()) {
            record
        } else {
            val updated = record.asPostedRecord(record.lastKnownText)
            TodoApplication.db.pinnedTaskRecordDao().upsert(updated)
            updated
        }
        postNotification(postedRecord)
    }

    private fun createRecord(todoFilePath: String, taskText: String): PinnedTaskRecord {
        return PinnedTaskRecord(
            taskKey = PinnedTaskKey.from(todoFilePath, taskText),
            todoFilePath = todoFilePath,
            taskText = taskText,
            createdAt = Date().time,
            lastKnownText = taskText,
            triggerMode = PinnedTaskRecord.TRIGGER_MODE_IMMEDIATE
        )
    }

    private fun currentTodoFilePath(): String? {
        val todoFile = TodoApplication.config.todoFile
        return try {
            todoFile.canonicalPath
        } catch (_: Exception) {
            todoFile.absolutePath
        }
    }

    private fun completeTaskInActiveTodoList(record: PinnedTaskRecord, task: Task): Boolean {
        TodoApplication.todoList.complete(
            listOf(task),
            TodoApplication.config.hasKeepPrio,
            TodoApplication.config.hasAppendAtEnd
        )
        if (TodoApplication.config.isAutoArchive) {
            TodoApplication.todoList.archive(
                TodoApplication.config.todoFile,
                TodoApplication.config.doneFile,
                listOf(task),
                TodoApplication.config.eol
            )
        } else {
            TodoApplication.todoList.notifyTasklistChanged(TodoApplication.config.todoFile, save = true)
        }
        return true
    }

    private fun completeTaskInExternalFile(record: PinnedTaskRecord): Boolean {
        val todoFile = File(record.todoFilePath)
        val tasks = FileStore.loadTasksFromFile(todoFile).map(::Task).toMutableList()
        val taskIndex = tasks.indexOfFirst {
            !it.isCompleted() && PinnedTaskKey.from(record.todoFilePath, it.text) == record.taskKey
        }
        if (taskIndex == -1) {
            return false
        }

        val task = tasks[taskIndex]
        val extra = task.markComplete(todayAsString)
        if (!TodoApplication.config.hasKeepPrio) {
            task.priority = Priority.NONE
        }

        if (TodoApplication.config.isAutoArchive) {
            FileStore.appendTaskToFile(doneFileFor(todoFile), listOf(task.inFileFormat(TodoApplication.config.useUUIDs)), TodoApplication.config.eol)
            tasks.removeAt(taskIndex)
        } else {
            tasks[taskIndex] = task
        }

        extra?.let {
            if (TodoApplication.config.hasAppendAtEnd) {
                tasks.add(it)
            } else {
                tasks.add(0, it)
            }
        }
        FileStore.saveTasksToFile(todoFile, tasks.map { it.inFileFormat(TodoApplication.config.useUUIDs) }, TodoApplication.config.eol)
        return true
    }

    private fun doneFileFor(todoFile: File): File {
        val fileName = if (FileStore.isEncrypted) "done.txt.jenc" else "done.txt"
        return File(todoFile.parentFile, fileName)
    }

    private fun postNotification(record: PinnedTaskRecord) {
        val editIntent = Intent(context, Simpletask::class.java).let {
            it.putExtra(Constants.EXTRA_PINNED_TASK_KEY, record.taskKey)
            it.putExtra(Constants.EXTRA_OPEN_PINNED_TASK, true)
            it.putExtra(Constants.EXTRA_TARGET_TODO_FILE, record.todoFilePath)
            it.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PendingIntent.getActivity(
                context,
                record.notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val doneIntent = Intent(context, MarkTaskDone::class.java).let {
            it.putExtra(Constants.EXTRA_PINNED_TASK_KEY, record.taskKey)
            PendingIntent.getService(
                context,
                record.notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val unpinIntent = Intent(context, UnpinTaskNotification::class.java).let {
            it.putExtra(Constants.EXTRA_PINNED_TASK_KEY, record.taskKey)
            PendingIntent.getService(
                context,
                record.notificationId + 1,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val dismissedIntent = Intent(context, PinnedNotificationDismissedReceiver::class.java).let {
            it.putExtra(Constants.EXTRA_PINNED_TASK_KEY, record.taskKey)
            PendingIntent.getBroadcast(
                context,
                record.notificationId + 2,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, Constants.PINNED_TASK_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_push_pin_white_24dp)
            .setContentTitle(record.lastKnownText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(record.lastKnownText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(editIntent)
            .setDeleteIntent(dismissedIntent)
            .addAction(R.drawable.ic_done_white_24dp, context.getString(R.string.done), doneIntent)
            .addAction(R.drawable.ic_push_pin_white_24dp, context.getString(R.string.unpin), unpinIntent)

        NotificationManagerCompat.from(context).notify(record.notificationId, builder.build())
    }

    private fun cancelNotification(record: PinnedTaskRecord) {
        NotificationManagerCompat.from(context).cancel(record.notificationId)
    }

    private fun cancelAlarmAndNotification(record: PinnedTaskRecord) {
        alarmScheduler.cancel(record)
        cancelNotification(record)
    }

    companion object {
        private const val TAG = "PinnedTaskNotifications"
    }
}
