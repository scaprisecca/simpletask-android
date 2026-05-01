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
import nl.mpcjanssen.simpletask.TodoApplication
import nl.mpcjanssen.simpletask.UnpinTaskNotification
import nl.mpcjanssen.simpletask.remote.FileStore
import nl.mpcjanssen.simpletask.task.Priority
import nl.mpcjanssen.simpletask.task.Task
import nl.mpcjanssen.simpletask.util.broadcastPinnedTasksChanged
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
    private var pinnedTaskDisplayStates: Map<String, PinnedTaskDisplayState> = emptyMap()

    init {
        executor.execute {
            refreshPinnedTaskKeys()
        }
    }

    fun isPinned(task: Task): Boolean {
        return displayStateFor(task) != PinnedTaskDisplayState.NONE
    }

    fun decorateTaskText(task: Task, text: String): String {
        return decoratePinnedTaskText(text, displayStateFor(task))
    }

    private fun displayStateFor(task: Task): PinnedTaskDisplayState {
        val taskKey = activeTaskKey(task) ?: return PinnedTaskDisplayState.NONE
        return pinnedTaskDisplayStates[taskKey] ?: PinnedTaskDisplayState.NONE
    }

    fun pinTask(task: Task, triggerAtMillis: Long? = null) {
        val taskKey = activeTaskKey(task) ?: return
        val todoFilePath = currentTodoFilePath() ?: return
        executor.execute {
            val dao = TodoApplication.db.pinnedTaskRecordDao()
            val existing = dao.get(taskKey)
            existing?.let { cancelAlarmAndNotification(it) }
            val baseRecord = createRecord(todoFilePath, task.text, PinnedTaskKey.occurrenceIndex(taskKey))
            val record = if (triggerAtMillis != null && triggerAtMillis > System.currentTimeMillis()) {
                baseRecord.asScheduledRecord(triggerAtMillis)
            } else {
                baseRecord.asImmediatePostedRecord(task.text)
            }
            dao.upsert(record)
            deliverRecord(record)
            refreshPinnedTaskKeysAndNotify()
        }
    }

    fun unpinTask(task: Task) {
        activeTaskKey(task)?.let { unpinTaskByKey(it) }
    }

    fun unpinTaskByKey(taskKey: String) {
        executor.execute {
            val dao = TodoApplication.db.pinnedTaskRecordDao()
            val record = dao.get(taskKey)
            dao.deleteByTaskKey(taskKey)
            record?.let { cancelAlarmAndNotification(it) }
            refreshPinnedTaskKeysAndNotify()
        }
    }

    fun retargetPinnedTaskForTaskEdit(task: Task, updatedTaskText: String) {
        val taskKey = activeTaskKey(task) ?: return
        retargetPinnedTaskForTaskEdit(taskKey, updatedTaskText)
    }

    fun retargetPinnedTaskForTaskEdit(taskKey: String, updatedTaskText: String) {
        executor.execute {
            val dao = TodoApplication.db.pinnedTaskRecordDao()
            val record = dao.get(taskKey) ?: return@execute
            val updated = PinnedTaskRecordEditor.retargetForTaskTextEdit(record, record.taskText, updatedTaskText)
                ?: return@execute
            cancelAlarmAndNotification(record)
            dao.deleteByTaskKey(taskKey)
            dao.upsert(updated)
            deliverRecord(updated)
            refreshPinnedTaskKeysAndNotify()
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
                    resolveRecord(record, preferActiveTodoList = false)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to post scheduled pinned task $taskKey", e)
                    return@execute
                } ?: run {
                    dao.deleteByTaskKey(taskKey)
                    cancelAlarmAndNotification(record)
                    refreshPinnedTaskKeysAndNotify()
                    return@execute
                }
                val updated = record.asPostedRecord(resolution.task.text)
                dao.upsert(updated)
                postNotification(updated)
                refreshPinnedTaskKeysAndNotify()
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
            refreshPinnedTaskKeysAndNotify()
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
        refreshPinnedTaskKeysAndNotify()
        cancelAlarmAndNotification(record)
        broadcastRefreshWidgets(TodoApplication.app.localBroadCastManager)
        return true
    }

    fun findTaskForPinnedKey(taskKey: String, preferActiveTodoList: Boolean = true): Task? {
        val record = TodoApplication.db.pinnedTaskRecordDao().get(taskKey) ?: return null
        return try {
            resolveRecord(record, preferActiveTodoList)?.task
        } catch (e: Exception) {
            Log.w(TAG, "Unable to find pinned task $taskKey", e)
            null
        }
    }

    private fun refreshPinnedTaskKeys() {
        pinnedTaskDisplayStates = TodoApplication.db.pinnedTaskRecordDao()
            .getAll()
            .associate { it.taskKey to it.displayState() }
    }

    private fun refreshPinnedTaskKeysAndNotify() {
        refreshPinnedTaskKeys()
        broadcastPinnedTasksChanged(TodoApplication.app.localBroadCastManager)
    }

    private fun reconcileAllRecords(reason: String) {
        val dao = TodoApplication.db.pinnedTaskRecordDao()
        val records = dao.getAll()
        Log.i(TAG, "Reconciling pinned notifications for $reason")
        var updatedCount = 0
        var deletedCount = 0
        var skippedCount = 0
        records.forEach { record ->
            try {
                val resolution = resolveRecord(record, preferActiveTodoList = false)
                if (resolution == null) {
                    dao.deleteByTaskKey(record.taskKey)
                    cancelAlarmAndNotification(record)
                    deletedCount += 1
                } else {
                    val updated = record.copy(
                        taskText = resolution.task.text,
                        lastKnownText = resolution.task.text
                    )
                    if (updated != record) {
                        dao.upsert(updated)
                        updatedCount += 1
                    }
                    deliverRecord(updated)
                }
            } catch (e: Exception) {
                skippedCount += 1
                Log.w(TAG, "Skipping pinned-task reconcile for ${record.taskKey}", e)
            }
        }
        refreshPinnedTaskKeysAndNotify()
        Log.i(
            TAG,
            "Pinned notification reconcile complete for $reason: records=${records.size}, updated=$updatedCount, deleted=$deletedCount, skipped=$skippedCount"
        )
    }

    private fun resolveRecord(record: PinnedTaskRecord, preferActiveTodoList: Boolean = true): PinnedTaskResolution? {
        return taskResolver.resolve(record, preferActiveTodoList)
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

    private fun createRecord(todoFilePath: String, taskText: String, occurrenceIndex: Int = 0): PinnedTaskRecord {
        return PinnedTaskRecord(
            taskKey = PinnedTaskKey.from(todoFilePath, taskText, occurrenceIndex),
            todoFilePath = todoFilePath,
            taskText = taskText,
            createdAt = Date().time,
            lastKnownText = taskText,
            triggerMode = PinnedTaskRecord.TRIGGER_MODE_IMMEDIATE
        )
    }

    private fun activeTaskKey(task: Task): String? {
        val todoFilePath = currentTodoFilePath() ?: return null
        val occurrenceIndex = occurrenceIndexForActiveTask(task)
        return PinnedTaskKey.from(todoFilePath, task.text, occurrenceIndex)
    }

    private fun occurrenceIndexForActiveTask(task: Task): Int {
        var matchIndex = 0
        TodoApplication.todoList.allTasks().forEach { candidate ->
            if (!candidate.isCompleted() && candidate.text == task.text) {
                if (candidate.id == task.id) {
                    return matchIndex
                }
                matchIndex += 1
            }
        }
        return 0
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
        val targetTask = findPinnedTask(record, tasks) ?: return false
        val taskIndex = tasks.indexOf(targetTask)
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
        val editIntent = Intent(context, nl.mpcjanssen.simpletask.Simpletask::class.java).let {
            it.action = Intent.ACTION_VIEW
            it.putExtra(Constants.EXTRA_OPEN_PINNED_TASK, true)
            it.putExtra(Constants.EXTRA_PINNED_TASK_KEY, record.taskKey)
            it.putExtra(Constants.EXTRA_TARGET_TODO_FILE, record.todoFilePath)
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
