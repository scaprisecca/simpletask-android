package nl.mpcjanssen.simpletask.fileswitch

import junit.framework.TestCase
import java.io.File

class FavoriteTodoFilesTest : TestCase() {
    fun testRoundTripSortsAlphabeticallyByFileName() {
        val stored = FavoriteTodoFiles.toStoredValue(
            listOf(
                FavoriteTodoFile("/tmp/work/zeta.txt"),
                FavoriteTodoFile("/tmp/home/alpha.txt")
            )
        )

        val restored = FavoriteTodoFiles.fromStoredValue(stored)

        assertEquals(listOf("alpha.txt", "zeta.txt"), restored.map { it.fileName })
    }

    fun testRoundTripPreservesOptionalLabel() {
        val stored = FavoriteTodoFiles.toStoredValue(
            listOf(FavoriteTodoFile("/tmp/home/todo.txt", label = "Personal"))
        )

        val restored = FavoriteTodoFiles.fromStoredValue(stored)

        assertEquals("Personal", restored.single().label)
    }

    fun testAddDeduplicatesCanonicalPaths() {
        val current = listOf(FavoriteTodoFile(File("/tmp/favorites/../favorites/todo.txt").canonicalPath))

        val updated = FavoriteTodoFiles.add(current, File("/tmp/favorites/todo.txt"))

        assertEquals(1, updated.size)
        assertEquals(File("/tmp/favorites/todo.txt").canonicalPath, updated.single().path)
    }

    fun testSameNamedFilesInDifferentFoldersRemainDistinct() {
        val stored = FavoriteTodoFiles.toStoredValue(
            listOf(
                FavoriteTodoFile("/tmp/work/todo.txt"),
                FavoriteTodoFile("/tmp/home/todo.txt")
            )
        )

        val restored = FavoriteTodoFiles.fromStoredValue(stored)

        assertEquals(2, restored.size)
        assertEquals(listOf("/tmp/home/todo.txt", "/tmp/work/todo.txt"), restored.map { it.path })
    }

    fun testMalformedStoredValueFallsBackToEmptyList() {
        assertTrue(FavoriteTodoFiles.fromStoredValue("{not-json").isEmpty())
    }

    fun testRemoveOnlyChangesFavoritesMembership() {
        val existing = listOf(
            FavoriteTodoFile("/tmp/home/todo.txt"),
            FavoriteTodoFile("/tmp/work/todo.txt")
        )

        val updated = FavoriteTodoFiles.remove(existing, File("/tmp/home/todo.txt"))

        assertEquals(listOf("/tmp/work/todo.txt"), updated.map { it.path })
    }
}
