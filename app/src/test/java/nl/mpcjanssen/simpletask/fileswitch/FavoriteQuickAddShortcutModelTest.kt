package nl.mpcjanssen.simpletask.fileswitch

import junit.framework.TestCase

class FavoriteQuickAddShortcutModelTest : TestCase() {
    fun testBuildSpecsUsesFileNameWhenUnique() {
        val specs = FavoriteQuickAddShortcutModel.buildSpecs(
            listOf(FavoriteTodoFile("/lists/inbox.txt"))
        )

        assertEquals(listOf("inbox.txt"), specs.map { it.label })
    }

    fun testBuildSpecsUsesFavoriteLabelWhenPresent() {
        val specs = FavoriteQuickAddShortcutModel.buildSpecs(
            listOf(FavoriteTodoFile("/lists/inbox.txt", label = "Groceries"))
        )

        assertEquals(listOf("Groceries"), specs.map { it.label })
    }

    fun testBuildSpecsIncludesParentPathForDuplicateNames() {
        val specs = FavoriteQuickAddShortcutModel.buildSpecs(
            listOf(
                FavoriteTodoFile("/lists/home/inbox.txt"),
                FavoriteTodoFile("/lists/work/inbox.txt")
            )
        )

        assertEquals(
            listOf(
                "inbox.txt — /lists/home",
                "inbox.txt — /lists/work"
            ),
            specs.map { it.label }
        )
    }
}
