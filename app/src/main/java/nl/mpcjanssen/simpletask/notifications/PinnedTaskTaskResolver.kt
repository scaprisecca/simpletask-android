package nl.mpcjanssen.simpletask.notifications

import nl.mpcjanssen.simpletask.TodoApplication
import nl.mpcjanssen.simpletask.remote.FileStore
import nl.mpcjanssen.simpletask.task.Task
import java.io.File

data class PinnedTaskResolution(
    val record: PinnedTaskRecord,
    val todoFile: File,
    val task: Task,
    val usesActiveTodoFile: Boolean
)

class PinnedTaskTaskResolver(
    private val activeTodoFileProvider: () -> File = { TodoApplication.config.todoFile },
    private val activeTasksProvider: () -> List<Task> = { TodoApplication.todoList.allTasks() },
    private val loadTasksFromFile: (File) -> List<String> = { FileStore.loadTasksFromFile(it) }
) {
    fun resolve(record: PinnedTaskRecord, preferActiveTodoList: Boolean = true): PinnedTaskResolution? {
        val activeTodoFile = activeTodoFileProvider()
        val recordFile = File(record.todoFilePath)
        val usesActiveTodoFile = preferActiveTodoList && canonicalPath(activeTodoFile) == canonicalPath(recordFile)
        val task = if (usesActiveTodoFile) {
            findTask(record, activeTasksProvider(), record.todoFilePath)
        } else {
            findTask(record, loadTasksFromFile(recordFile).map(::Task), record.todoFilePath)
        } ?: return null
        return PinnedTaskResolution(
            record = record,
            todoFile = recordFile,
            task = task,
            usesActiveTodoFile = usesActiveTodoFile
        )
    }

    companion object {
        fun findTask(record: PinnedTaskRecord, tasks: List<Task>, todoFilePath: String): Task? {
            return findPinnedTask(record, tasks)
        }

        fun canonicalPath(file: File): String {
            return try {
                file.canonicalPath
            } catch (_: Exception) {
                file.absolutePath
            }
        }
    }
}
