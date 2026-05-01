package nl.mpcjanssen.simpletask.notifications

import junit.framework.TestCase

class PinnedTaskFileEditTest : TestCase() {
    fun testFindTaskByKeyAndTextReturnsMatchingPinnedTask() {
        val secondOccurrenceKey = PinnedTaskKey.from("/tmp/todo.txt", "Review release notes", occurrenceIndex = 1)

        val task = findPinnedTask(
            taskKey = secondOccurrenceKey,
            taskText = "Review release notes",
            tasks = listOf(
                nl.mpcjanssen.simpletask.task.Task("Review release notes"),
                nl.mpcjanssen.simpletask.task.Task("Review release notes"),
                nl.mpcjanssen.simpletask.task.Task("Plan sprint")
            )
        )

        assertNotNull(task)
        assertEquals("Review release notes", task!!.text)
        assertEquals(1, PinnedTaskKey.occurrenceIndex(secondOccurrenceKey))
    }

    fun testFindTaskInFileLinesReturnsMatchingPinnedTask() {
        val record = PinnedTaskRecord(
            taskKey = PinnedTaskKey.from("/tmp/todo.txt", "Review release notes"),
            todoFilePath = "/tmp/todo.txt",
            taskText = "Review release notes",
            createdAt = 1L
        )

        val task = findPinnedTaskInFileLines(record, listOf("Plan sprint", "Review release notes"))

        assertNotNull(task)
        assertEquals("Review release notes", task!!.text)
    }

    fun testReplaceTaskInFileLinesUpdatesOnlyThePinnedTask() {
        val record = PinnedTaskRecord(
            taskKey = PinnedTaskKey.from("/tmp/todo.txt", "Review release notes"),
            todoFilePath = "/tmp/todo.txt",
            taskText = "Review release notes",
            createdAt = 1L
        )

        val updatedLines = replacePinnedTaskInFileLines(
            record = record,
            fileLines = listOf("Plan sprint", "Review release notes"),
            updatedTaskText = "Review release notes +release"
        )

        assertEquals(listOf("Plan sprint", "Review release notes +release"), updatedLines)
    }
}
