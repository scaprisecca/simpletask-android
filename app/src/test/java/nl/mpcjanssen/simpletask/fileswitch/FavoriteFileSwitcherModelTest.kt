package nl.mpcjanssen.simpletask.fileswitch

import junit.framework.TestCase
import java.io.File

class FavoriteFileSwitcherModelTest : TestCase() {
    fun testBuildRowsMarksActiveFavorite() {
        val favorites = listOf(
            FavoriteTodoFile("/tmp/home.txt"),
            FavoriteTodoFile("/tmp/work.txt")
        )

        val rows = FavoriteFileSwitcherModel.buildRows(favorites, File("/tmp/work.txt"))

        assertFalse(rows[0].isActive)
        assertTrue(rows[1].isActive)
    }

    fun testBuildRowsUsesLabelAsPrimaryTitle() {
        val favorites = listOf(
            FavoriteTodoFile("/tmp/home/todo.txt", label = "Home"),
        )

        val rows = FavoriteFileSwitcherModel.buildRows(favorites, File("/tmp/home/todo.txt"))

        assertEquals("Home", rows.single().title)
        assertEquals("todo.txt — /tmp/home", rows.single().subtitle)
    }

    fun testBuildRowsUsesCanonicalPathForActiveComparison() {
        val favorites = listOf(FavoriteTodoFile(File("/tmp/favorites/../favorites/todo.txt").canonicalPath))

        val rows = FavoriteFileSwitcherModel.buildRows(favorites, File("/tmp/favorites/todo.txt"))

        assertTrue(rows.single().isActive)
    }

    fun testBuildRowsPrefersLoadedFilePathWhenConfiguredFileIsAheadOfUi() {
        val favorites = listOf(
            FavoriteTodoFile("/tmp/file_a.txt"),
            FavoriteTodoFile("/tmp/file_b.txt")
        )

        val rows = FavoriteFileSwitcherModel.buildRows(
            favorites = favorites,
            activeFile = File("/tmp/file_a.txt"),
            loadedFilePath = "/tmp/file_b.txt"
        )

        assertFalse(rows[0].isActive)
        assertTrue(rows[1].isActive)
    }
}
