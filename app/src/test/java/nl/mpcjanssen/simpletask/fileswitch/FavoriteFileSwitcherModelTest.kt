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

    fun testBuildRowsUsesCanonicalPathForActiveComparison() {
        val favorites = listOf(FavoriteTodoFile(File("/tmp/favorites/../favorites/todo.txt").canonicalPath))

        val rows = FavoriteFileSwitcherModel.buildRows(favorites, File("/tmp/favorites/todo.txt"))

        assertTrue(rows.single().isActive)
    }
}
