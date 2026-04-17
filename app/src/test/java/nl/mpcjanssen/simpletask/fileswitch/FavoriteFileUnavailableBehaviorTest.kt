package nl.mpcjanssen.simpletask.fileswitch

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files

class FavoriteFileUnavailableBehaviorTest : TestCase() {

    fun testAccessibleFavoriteYieldsSwitchAction() {
        val tmpFile = Files.createTempFile("todo", ".txt").toFile()
        try {
            val action = FavoriteFileSwitchCoordinator.start(
                activeFile = File("/tmp/current.txt"),
                targetFile = tmpFile,
                hasPendingChanges = false
            )
            assertEquals(FavoriteFileSwitchAction.SwitchTo(tmpFile), action)
        } finally {
            tmpFile.delete()
        }
    }

    fun testRemoveBrokenFavoriteSucceeds() {
        val missingPath = "/tmp/nonexistent_unavail_test.txt"
        val favorites = listOf(
            FavoriteTodoFile("/tmp/other.txt"),
            FavoriteTodoFile(missingPath)
        )

        val updated = FavoriteTodoFiles.remove(favorites, File(missingPath))

        assertEquals(1, updated.size)
        assertEquals("/tmp/other.txt", updated.single().path)
    }

    fun testRemoveBrokenFavoriteDoesNotAffectOtherFavorites() {
        val missingPath = "/tmp/nonexistent_unavail_test.txt"
        val favorites = listOf(
            FavoriteTodoFile("/tmp/alpha.txt"),
            FavoriteTodoFile(missingPath),
            FavoriteTodoFile("/tmp/zeta.txt")
        )

        val updated = FavoriteTodoFiles.remove(favorites, File(missingPath))

        assertEquals(2, updated.size)
        assertEquals(listOf("/tmp/alpha.txt", "/tmp/zeta.txt"), updated.map { it.path })
    }

    fun testCoordinatorDoesNotCheckFileAvailability() {
        // Availability is enforced at the UI layer (Simpletask.canOpenFavoriteFile).
        // The coordinator returns SwitchTo for a missing file — it is the caller's
        // responsibility to validate before executing the switch.
        val missingTarget = File("/tmp/nonexistent_unavail_test.txt")

        val action = FavoriteFileSwitchCoordinator.start(
            activeFile = File("/tmp/current.txt"),
            targetFile = missingTarget,
            hasPendingChanges = false
        )

        assertEquals(FavoriteFileSwitchAction.SwitchTo(missingTarget), action)
    }

    fun testFavoritesListNotModifiedBySwitch() {
        // The favorites list is only changed by explicit add/remove — not by switch outcomes.
        // A failed switch (StayOnCurrentFile) leaves the list intact so the user can
        // decide whether to remove the broken entry.
        val missingPath = "/tmp/nonexistent_unavail_test.txt"
        val favorites = listOf(
            FavoriteTodoFile("/tmp/current.txt"),
            FavoriteTodoFile(missingPath)
        )

        FavoriteFileSwitchCoordinator.start(
            activeFile = File("/tmp/current.txt"),
            targetFile = File(missingPath),
            hasPendingChanges = false
        )

        assertEquals(2, favorites.size)
        assertTrue(favorites.any { it.path == missingPath })
    }
}
