package nl.mpcjanssen.simpletask.notifications

import junit.framework.TestCase
import nl.mpcjanssen.simpletask.task.Task
import java.io.File

class PinnedTaskTaskResolverTest : TestCase() {
    fun testResolveUsesActiveTasksWhenTodoFileMatches() {
        val record = PinnedTaskRecord(
            taskKey = PinnedTaskKey.from("/tmp/todo.txt", "Review PR"),
            todoFilePath = "/tmp/todo.txt",
            taskText = "Review PR",
            createdAt = 1L
        )
        val activeTask = Task("Review PR")
        val resolver = PinnedTaskTaskResolver(
            activeTodoFileProvider = { File("/tmp/todo.txt") },
            activeTasksProvider = { listOf(activeTask) },
            loadTasksFromFile = { error("should not load external file") }
        )

        val resolution = resolver.resolve(record)

        assertNotNull(resolution)
        assertTrue(resolution!!.usesActiveTodoFile)
        assertSame(activeTask, resolution.task)
    }

    fun testResolveLoadsOtherTodoFileWhenNeeded() {
        val record = PinnedTaskRecord(
            taskKey = PinnedTaskKey.from("/tmp/other.txt", "Plan sprint"),
            todoFilePath = "/tmp/other.txt",
            taskText = "Plan sprint",
            createdAt = 1L
        )
        val resolver = PinnedTaskTaskResolver(
            activeTodoFileProvider = { File("/tmp/todo.txt") },
            activeTasksProvider = { emptyList() },
            loadTasksFromFile = { listOf("Plan sprint", "Write retro") }
        )

        val resolution = resolver.resolve(record)

        assertNotNull(resolution)
        assertFalse(resolution!!.usesActiveTodoFile)
        assertEquals("Plan sprint", resolution.task.text)
    }

    fun testResolveReturnsNullWhenTaskIsMissing() {
        val record = PinnedTaskRecord(
            taskKey = PinnedTaskKey.from("/tmp/todo.txt", "Missing task"),
            todoFilePath = "/tmp/todo.txt",
            taskText = "Missing task",
            createdAt = 1L
        )
        val resolver = PinnedTaskTaskResolver(
            activeTodoFileProvider = { File("/tmp/todo.txt") },
            activeTasksProvider = { listOf(Task("Another task")) }
        )

        assertNull(resolver.resolve(record))
    }

    fun testResolveReturnsMatchingDuplicateByOccurrenceIndex() {
        val first = Task("Buy batteries")
        val second = Task("Buy batteries")
        val record = PinnedTaskRecord(
            taskKey = PinnedTaskKey.from("/tmp/todo.txt", "Buy batteries", occurrenceIndex = 1),
            todoFilePath = "/tmp/todo.txt",
            taskText = "Buy batteries",
            createdAt = 1L
        )
        val resolver = PinnedTaskTaskResolver(
            activeTodoFileProvider = { File("/tmp/todo.txt") },
            activeTasksProvider = { listOf(first, second) }
        )

        val resolution = resolver.resolve(record)

        assertNotNull(resolution)
        assertSame(second, resolution!!.task)
    }

    fun testResolveLoadsMatchingFileWhenActiveListShouldNotBeTrusted() {
        val record = PinnedTaskRecord(
            taskKey = PinnedTaskKey.from("/tmp/todo.txt", "Correct task"),
            todoFilePath = "/tmp/todo.txt",
            taskText = "Correct task",
            createdAt = 1L
        )
        val resolver = PinnedTaskTaskResolver(
            activeTodoFileProvider = { File("/tmp/todo.txt") },
            activeTasksProvider = { listOf(Task("Stale cached task")) },
            loadTasksFromFile = { listOf("Correct task") }
        )

        val resolution = resolver.resolve(record, preferActiveTodoList = false)

        assertNotNull(resolution)
        assertFalse(resolution!!.usesActiveTodoFile)
        assertEquals("Correct task", resolution.task.text)
    }
}
