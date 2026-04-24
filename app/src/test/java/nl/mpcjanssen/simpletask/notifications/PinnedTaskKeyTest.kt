package nl.mpcjanssen.simpletask.notifications

import junit.framework.TestCase

class PinnedTaskKeyTest : TestCase() {
    fun testFromCanonicalizesTodoFilePath() {
        val key = PinnedTaskKey.from("/tmp/simpletasks/../simpletasks/todo.txt", "Review release notes")

        assertEquals("/tmp/simpletasks/todo.txt\nReview release notes", key)
    }

    fun testFromChangesWhenTaskTextChanges() {
        val original = PinnedTaskKey.from("/tmp/simpletasks/todo.txt", "Call mom")
        val updated = PinnedTaskKey.from("/tmp/simpletasks/todo.txt", "Call mom tonight")

        assertFalse(original == updated)
    }

    fun testRetargetForTaskTextEditRekeysPinnedRecord() {
        val record = PinnedTaskRecord(
            taskKey = PinnedTaskKey.from("/tmp/simpletasks/todo.txt", "Read chapter 1"),
            todoFilePath = "/tmp/simpletasks/todo.txt",
            taskText = "Read chapter 1",
            createdAt = 10L,
            lastKnownText = "Read chapter 1"
        )

        val updated = PinnedTaskRecordEditor.retargetForTaskTextEdit(
            record = record,
            originalTaskText = "Read chapter 1",
            updatedTaskText = "Read chapter 2"
        )

        assertNotNull(updated)
        val updatedRecord = updated!!
        assertEquals(PinnedTaskKey.from("/tmp/simpletasks/todo.txt", "Read chapter 2"), updatedRecord.taskKey)
        assertEquals("Read chapter 2", updatedRecord.taskText)
        assertEquals("Read chapter 2", updatedRecord.lastKnownText)
    }

    fun testRetargetForTaskTextEditIgnoresUnrelatedTask() {
        val record = PinnedTaskRecord(
            taskKey = PinnedTaskKey.from("/tmp/simpletasks/todo.txt", "Pinned task"),
            todoFilePath = "/tmp/simpletasks/todo.txt",
            taskText = "Pinned task",
            createdAt = 10L,
            lastKnownText = "Pinned task"
        )

        val updated = PinnedTaskRecordEditor.retargetForTaskTextEdit(
            record = record,
            originalTaskText = "Other task",
            updatedTaskText = "Other task updated"
        )

        assertNull(updated)
    }
}
