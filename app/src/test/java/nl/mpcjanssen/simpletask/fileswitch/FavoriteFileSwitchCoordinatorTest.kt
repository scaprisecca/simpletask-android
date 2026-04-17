package nl.mpcjanssen.simpletask.fileswitch

import junit.framework.TestCase
import java.io.File

class FavoriteFileSwitchCoordinatorTest : TestCase() {
    fun testStartReturnsSwitchWhenNoPendingChanges() {
        val action = FavoriteFileSwitchCoordinator.start(
            activeFile = File("/tmp/current.txt"),
            targetFile = File("/tmp/next.txt"),
            hasPendingChanges = false
        )

        assertEquals(FavoriteFileSwitchAction.SwitchTo(File("/tmp/next.txt")), action)
    }

    fun testStartReturnsPromptWhenPendingChangesExist() {
        val action = FavoriteFileSwitchCoordinator.start(
            activeFile = File("/tmp/current.txt"),
            targetFile = File("/tmp/next.txt"),
            hasPendingChanges = true
        )

        assertEquals(FavoriteFileSwitchAction.PromptForPendingChanges, action)
    }

    fun testStartReturnsNoOpForSameFile() {
        val action = FavoriteFileSwitchCoordinator.start(
            activeFile = File("/tmp/favorites/../favorites/todo.txt"),
            targetFile = File("/tmp/favorites/todo.txt"),
            hasPendingChanges = false
        )

        assertEquals(FavoriteFileSwitchAction.NoOp, action)
    }

    fun testAfterPromptResolvesChoices() {
        assertEquals(
            FavoriteFileSwitchAction.SaveThenSwitch(File("/tmp/next.txt")),
            FavoriteFileSwitchCoordinator.afterPrompt(File("/tmp/next.txt"), FavoriteFileSwitchPromptChoice.SAVE)
        )
        assertEquals(
            FavoriteFileSwitchAction.SwitchTo(File("/tmp/next.txt")),
            FavoriteFileSwitchCoordinator.afterPrompt(File("/tmp/next.txt"), FavoriteFileSwitchPromptChoice.DISCARD)
        )
        assertEquals(
            FavoriteFileSwitchAction.StayOnCurrentFile,
            FavoriteFileSwitchCoordinator.afterPrompt(File("/tmp/next.txt"), FavoriteFileSwitchPromptChoice.CANCEL)
        )
    }

    fun testAfterSaveStaysOnCurrentFileWhenSaveFails() {
        val action = FavoriteFileSwitchCoordinator.afterSave(
            activeFileAfterSave = File("/tmp/current.txt"),
            targetFile = File("/tmp/next.txt"),
            saveSucceeded = false
        )

        assertEquals(FavoriteFileSwitchAction.StayOnCurrentFile, action)
    }

    fun testAfterSaveUsesUpdatedActiveFileAsSourceOfTruth() {
        val action = FavoriteFileSwitchCoordinator.afterSave(
            activeFileAfterSave = File("/tmp/renamed.txt"),
            targetFile = File("/tmp/renamed.txt"),
            saveSucceeded = true
        )

        assertEquals(FavoriteFileSwitchAction.NoOp, action)
    }
}
