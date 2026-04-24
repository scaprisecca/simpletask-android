package nl.mpcjanssen.simpletask.notifications

import java.io.File

object PinnedTaskKey {
    fun canonicalTodoFilePath(todoFilePath: String): String {
        val file = File(todoFilePath)
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
    }

    fun from(todoFilePath: String, taskText: String): String {
        return "${canonicalTodoFilePath(todoFilePath)}\n$taskText"
    }
}

object PinnedTaskRecordEditor {
    fun retargetForTaskTextEdit(
        record: PinnedTaskRecord,
        originalTaskText: String,
        updatedTaskText: String
    ): PinnedTaskRecord? {
        val expectedKey = PinnedTaskKey.from(record.todoFilePath, originalTaskText)
        if (record.taskKey != expectedKey) {
            return null
        }
        return record.copy(
            taskKey = PinnedTaskKey.from(record.todoFilePath, updatedTaskText),
            taskText = updatedTaskText,
            lastKnownText = updatedTaskText
        )
    }
}
