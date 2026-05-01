package nl.mpcjanssen.simpletask.notifications

import nl.mpcjanssen.simpletask.task.Task

fun findPinnedTask(taskKey: String, taskText: String, tasks: List<Task>): Task? {
    val occurrenceIndex = PinnedTaskKey.occurrenceIndex(taskKey)
    var currentIndex = 0
    tasks.forEach { task ->
        if (!task.isCompleted() && task.text == taskText) {
            if (currentIndex == occurrenceIndex) {
                return task
            }
            currentIndex += 1
        }
    }
    return null
}

fun findPinnedTask(record: PinnedTaskRecord, tasks: List<Task>): Task? {
    return findPinnedTask(record.taskKey, record.taskText, tasks)
}

fun findPinnedTaskInFileLines(record: PinnedTaskRecord, fileLines: List<String>): Task? {
    return findPinnedTask(record, fileLines.map(::Task))
}

fun replacePinnedTaskInFileLines(
    record: PinnedTaskRecord,
    fileLines: List<String>,
    updatedTaskText: String
): List<String>? {
    val tasks = fileLines.map(::Task)
    val targetTask = findPinnedTask(record, tasks) ?: return null
    val taskIndex = tasks.indexOf(targetTask)
    if (taskIndex == -1) {
        return null
    }

    val updatedLines = fileLines.toMutableList()
    updatedLines[taskIndex] = Task(updatedTaskText).text
    return updatedLines
}
