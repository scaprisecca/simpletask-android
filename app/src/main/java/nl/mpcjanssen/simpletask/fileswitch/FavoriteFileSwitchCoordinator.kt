package nl.mpcjanssen.simpletask.fileswitch

import java.io.File

enum class FavoriteFileSwitchPromptChoice {
    SAVE,
    DISCARD,
    CANCEL
}

sealed class FavoriteFileSwitchAction {
    object NoOp : FavoriteFileSwitchAction()
    object PromptForPendingChanges : FavoriteFileSwitchAction()
    data class SwitchTo(val target: File) : FavoriteFileSwitchAction()
    data class SaveThenSwitch(val target: File) : FavoriteFileSwitchAction()
    object StayOnCurrentFile : FavoriteFileSwitchAction()
}

object FavoriteFileSwitchCoordinator {
    fun start(activeFile: File, targetFile: File, hasPendingChanges: Boolean): FavoriteFileSwitchAction {
        return if (sameFile(activeFile, targetFile)) {
            FavoriteFileSwitchAction.NoOp
        } else if (hasPendingChanges) {
            FavoriteFileSwitchAction.PromptForPendingChanges
        } else {
            FavoriteFileSwitchAction.SwitchTo(targetFile)
        }
    }

    fun afterPrompt(targetFile: File, choice: FavoriteFileSwitchPromptChoice): FavoriteFileSwitchAction {
        return when (choice) {
            FavoriteFileSwitchPromptChoice.SAVE -> FavoriteFileSwitchAction.SaveThenSwitch(targetFile)
            FavoriteFileSwitchPromptChoice.DISCARD -> FavoriteFileSwitchAction.SwitchTo(targetFile)
            FavoriteFileSwitchPromptChoice.CANCEL -> FavoriteFileSwitchAction.StayOnCurrentFile
        }
    }

    fun afterSave(activeFileAfterSave: File, targetFile: File, saveSucceeded: Boolean): FavoriteFileSwitchAction {
        if (!saveSucceeded) {
            return FavoriteFileSwitchAction.StayOnCurrentFile
        }
        return if (sameFile(activeFileAfterSave, targetFile)) {
            FavoriteFileSwitchAction.NoOp
        } else {
            FavoriteFileSwitchAction.SwitchTo(targetFile)
        }
    }

    private fun sameFile(first: File, second: File): Boolean {
        return canonicalPath(first) == canonicalPath(second)
    }

    private fun canonicalPath(file: File): String = try {
        file.canonicalPath
    } catch (_: Exception) {
        file.absolutePath
    }
}
