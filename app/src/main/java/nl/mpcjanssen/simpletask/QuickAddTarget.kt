package nl.mpcjanssen.simpletask

import nl.mpcjanssen.simpletask.fileswitch.FavoriteTodoFile
import java.io.File

data class QuickAddTargetResolution(
    val targetFile: File,
    val hasExplicitTarget: Boolean
)

internal object QuickAddTarget {
    fun resolve(intentTargetPath: String?, fallback: File): QuickAddTargetResolution {
        val trimmedPath = intentTargetPath?.trim().orEmpty()
        if (trimmedPath.isEmpty()) {
            return QuickAddTargetResolution(
                targetFile = fallback,
                hasExplicitTarget = false
            )
        }

        val explicitFile = canonicalFile(File(trimmedPath))
        return QuickAddTargetResolution(
            targetFile = explicitFile,
            hasExplicitTarget = true
        )
    }

    fun mergeExistingAndNewLines(existingLines: List<String>, newLines: List<String>, appendAtEnd: Boolean): List<String> {
        return if (appendAtEnd) {
            existingLines + newLines
        } else {
            newLines + existingLines
        }
    }

    fun isExplicitTargetAllowed(targetFile: File, favorites: List<FavoriteTodoFile>): Boolean {
        val allowedPaths = favorites.map { canonicalFile(File(it.path)).path }.toSet()
        return canonicalFile(targetFile).path in allowedPaths
    }

    private fun canonicalFile(file: File): File = try {
        file.canonicalFile
    } catch (_: Exception) {
        file.absoluteFile
    }
}
