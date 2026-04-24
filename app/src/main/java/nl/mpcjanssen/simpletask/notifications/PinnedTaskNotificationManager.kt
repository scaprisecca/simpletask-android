package nl.mpcjanssen.simpletask.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nl.mpcjanssen.simpletask.AddTask
import nl.mpcjanssen.simpletask.Constants
import nl.mpcjanssen.simpletask.MarkTaskDone
import nl.mpcjanssen.simpletask.R
import nl.mpcjanssen.simpletask.TodoApplication
import nl.mpcjanssen.simpletask.UnpinTaskNotification
import nl.mpcjanssen.simpletask.PinnedNotificationDismissedReceiver
import nl.mpcjanssen.simpletask.task.Task
import java.util.Date
import java.util.concurrent.Executors

class PinnedTaskNotificationManager(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

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

    fun pinTask(task: Task) {
        val todoFilePath = currentTodoFilePath() ?: return
        val taskKey = PinnedTaskKey.from(todoFilePath, task.text)
        pinnedTaskKeys = pinnedTaskKeys + taskKey
        executor.execute {
            val record = createRecord(todoFilePath, task.text)
            TodoApplication.db.pinnedTaskRecordDao().upsert(record)
            refreshPinnedTaskKeys()
            postNotification(record)
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
            refreshPinnedTaskKeys()
            record?.let { cancelNotification(it) }
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
            pinnedTaskKeys = (pinnedTaskKeys - oldKey) + updated.taskKey
            dao.deleteByTaskKey(oldKey)
            dao.upsert(updated)
            refreshPinnedTaskKeys()
        }
    }

    fun reconcileWithCurrentTodoList(reason: String) {
        executor.execute {
            val todoFilePath = currentTodoFilePath() ?: return@execute
            val dao = TodoApplication.db.pinnedTaskRecordDao()
            val records = dao.getAll()
            val currentTasks = TodoApplication.todoList.allTasks()
            Log.i(TAG, "Reconciling pinned notifications for $reason")

            records.filter { it.todoFilePath != todoFilePath }.forEach { cancelNotification(it) }
            records.filter { it.todoFilePath == todoFilePath }.forEach { record ->
                val task = findTaskForRecord(record, currentTasks, todoFilePath)
                if (task == null) {
                    dao.deleteByTaskKey(record.taskKey)
                    cancelNotification(record)
                } else {
                    val currentText = task.text
                    val updatedRecord = if (record.taskText != currentText || record.lastKnownText != currentText) {
                        record.copy(taskText = currentText, lastKnownText = currentText)
                    } else {
                        record
                    }
                    if (updatedRecord != record) {
                        dao.upsert(updatedRecord)
                    }
                    postNotification(updatedRecord)
                }
            }
            refreshPinnedTaskKeys()
        }
    }

    fun reopenDismissedNotification(taskKey: String?) {
        if (taskKey.isNullOrEmpty()) {
            return
        }
        executor.execute {
            if (TodoApplication.db.pinnedTaskRecordDao().get(taskKey) != null) {
                reconcileWithCurrentTodoList("notification dismissed")
            }
        }
    }

    fun completeTaskFromNotification(taskKey: String): Boolean {
        val todoFilePath = currentTodoFilePath() ?: return false
        val dao = TodoApplication.db.pinnedTaskRecordDao()
        val record = dao.get(taskKey) ?: return false
        val task = findTaskForRecord(record, TodoApplication.todoList.allTasks(), todoFilePath)
        if (task == null) {
            dao.deleteByTaskKey(taskKey)
            refreshPinnedTaskKeys()
            cancelNotification(record)
            return false
        }

        TodoApplication.todoList.complete(
            listOf(task),
            TodoApplication.config.hasKeepPrio,
            TodoApplication.config.hasAppendAtEnd
        )
        dao.deleteByTaskKey(taskKey)
        refreshPinnedTaskKeys()
        cancelNotification(record)

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

    fun findTaskForPinnedKey(taskKey: String): Task? {
        val todoFilePath = currentTodoFilePath() ?: return null
        return TodoApplication.todoList.allTasks().firstOrNull {
            !it.isCompleted() && PinnedTaskKey.from(todoFilePath, it.text) == taskKey
        }
    }

    private fun refreshPinnedTaskKeys() {
        pinnedTaskKeys = TodoApplication.db.pinnedTaskRecordDao().getAll().map { it.taskKey }.toSet()
    }

    private fun findTaskForRecord(
        record: PinnedTaskRecord,
        tasks: List<Task>,
        currentTodoFilePath: String
    ): Task? {
        if (record.todoFilePath != currentTodoFilePath) {
            return null
        }
        return tasks.firstOrNull { !it.isCompleted() && PinnedTaskKey.from(currentTodoFilePath, it.text) == record.taskKey }
    }

    private fun createRecord(todoFilePath: String, taskText: String): PinnedTaskRecord {
        return PinnedTaskRecord(
            taskKey = PinnedTaskKey.from(todoFilePath, taskText),
            todoFilePath = todoFilePath,
            taskText = taskText,
            createdAt = Date().time,
            lastKnownText = taskText
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

    private fun postNotification(record: PinnedTaskRecord) {
        val editIntent = Intent(context, AddTask::class.java).let {
            it.putExtra(Constants.EXTRA_PINNED_TASK_KEY, record.taskKey)
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
                record.notificationId,
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
            .setOngoing(true)
            .setContentIntent(editIntent)
            .setDeleteIntent(dismissedIntent)
            .addAction(R.drawable.ic_done_white_24dp, context.getString(R.string.done), doneIntent)
            .addAction(R.drawable.ic_push_pin_white_24dp, context.getString(R.string.unpin_notification), unpinIntent)

        NotificationManagerCompat.from(context).notify(record.notificationId, builder.build())
    }

    private fun cancelNotification(record: PinnedTaskRecord) {
        NotificationManagerCompat.from(context).cancel(record.notificationId)
    }

    companion object {
        private const val TAG = "PinnedTaskNotifications"
    }
}
