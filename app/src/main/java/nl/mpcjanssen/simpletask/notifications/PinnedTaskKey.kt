package nl.mpcjanssen.simpletask.notifications

import java.io.File

object PinnedTaskKey {
    private const val OCCURRENCE_PREFIX = "[occurrence="
    private const val OCCURRENCE_SUFFIX = "]"

    fun canonicalTodoFilePath(todoFilePath: String): String {
        val file = File(todoFilePath)
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
    }

    fun from(todoFilePath: String, taskText: String, occurrenceIndex: Int = 0): String {
        val canonicalPath = canonicalTodoFilePath(todoFilePath)
        return if (occurrenceIndex <= 0) {
            "${canonicalPath}\n$taskText"
        } else {
            "${canonicalPath}\n${OCCURRENCE_PREFIX}${occurrenceIndex}${OCCURRENCE_SUFFIX}\n$taskText"
        }
    }

    fun occurrenceIndex(taskKey: String): Int {
        val lines = taskKey.split('\n')
        if (lines.size < 3) {
            return 0
        }
        val marker = lines[1]
        if (!marker.startsWith(OCCURRENCE_PREFIX) || !marker.endsWith(OCCURRENCE_SUFFIX)) {
            return 0
        }
        return marker.removePrefix(OCCURRENCE_PREFIX).removeSuffix(OCCURRENCE_SUFFIX).toIntOrNull() ?: 0
    }
}

object PinnedTaskRecordEditor {
    fun retargetForTaskTextEdit(
        record: PinnedTaskRecord,
        originalTaskText: String,
        updatedTaskText: String
    ): PinnedTaskRecord? {
        val occurrenceIndex = PinnedTaskKey.occurrenceIndex(record.taskKey)
        val expectedKey = PinnedTaskKey.from(record.todoFilePath, originalTaskText, occurrenceIndex)
        if (record.taskKey != expectedKey) {
            return null
        }
        return record.copy(
            taskKey = PinnedTaskKey.from(record.todoFilePath, updatedTaskText, occurrenceIndex),
            taskText = updatedTaskText,
            lastKnownText = updatedTaskText
        )
    }
}
