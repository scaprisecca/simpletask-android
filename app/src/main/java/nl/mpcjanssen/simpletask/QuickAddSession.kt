package nl.mpcjanssen.simpletask

import nl.mpcjanssen.simpletask.task.Task
import java.io.File
import java.util.TreeSet

data class QuickAddMetadataSuggestions(
    val contexts: List<String>,
    val projects: List<String>
)

object QuickAddMetadata {
    fun collectSuggestions(lines: List<String>): QuickAddMetadataSuggestions {
        val contexts = TreeSet<String>()
        val projects = TreeSet<String>()
        lines.asSequence().map(::Task).forEach { task ->
            task.lists?.let { contexts.addAll(it) }
            task.tags?.let { projects.addAll(it) }
        }
        return QuickAddMetadataSuggestions(
            contexts = contexts.toList(),
            projects = projects.toList()
        )
    }
}

object QuickAddSession {
    fun shouldBlockLaunch(hasExplicitTarget: Boolean, activeEditorCount: Int): Boolean {
        return hasExplicitTarget && activeEditorCount > 0
    }

    fun usesIsolatedMetadata(targetResolution: QuickAddTargetResolution, activeFile: File): Boolean {
        return targetResolution.hasExplicitTarget && canonicalFile(targetResolution.targetFile) != canonicalFile(activeFile)
    }

    private fun canonicalFile(file: File): File = try {
        file.canonicalFile
    } catch (_: Exception) {
        file.absoluteFile
    }
}
