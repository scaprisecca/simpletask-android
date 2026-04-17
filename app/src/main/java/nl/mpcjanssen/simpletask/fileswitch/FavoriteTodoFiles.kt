package nl.mpcjanssen.simpletask.fileswitch

import org.json.JSONArray
import java.io.File
import java.util.Locale

data class FavoriteTodoFile(
    val path: String
) {
    val fileName: String
        get() = File(path).name

    val parentPath: String
        get() = File(path).parent ?: ""
}

object FavoriteTodoFiles {
    fun fromStoredValue(value: String?): List<FavoriteTodoFile> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            val paths = mutableListOf<String>()
            val json = JSONArray(value)
            for (i in 0 until json.length()) {
                val path = json.optString(i).orEmpty().trim()
                if (path.isNotEmpty()) {
                    paths.add(path)
                }
            }
            normalizePaths(paths).map(::FavoriteTodoFile)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toStoredValue(files: List<FavoriteTodoFile>): String {
        val normalized = normalizePaths(files.map { it.path })
        return JSONArray(normalized).toString()
    }

    fun add(existing: List<FavoriteTodoFile>, file: File): List<FavoriteTodoFile> {
        return normalizePaths(existing.map { it.path } + canonicalPath(file)).map(::FavoriteTodoFile)
    }

    fun remove(existing: List<FavoriteTodoFile>, file: File): List<FavoriteTodoFile> {
        val removedPath = canonicalPath(file)
        return normalizePaths(existing.map { it.path }.filterNot { it == removedPath }).map(::FavoriteTodoFile)
    }

    private fun normalizePaths(paths: List<String>): List<String> {
        return paths
            .mapNotNull { rawPath ->
                val trimmed = rawPath.trim()
                if (trimmed.isEmpty()) {
                    null
                } else {
                    canonicalPath(File(trimmed))
                }
            }
            .distinct()
            .sortedWith(compareBy<String>({ File(it).name.toLowerCase(Locale.ROOT) }, { it.toLowerCase(Locale.ROOT) }))
    }

    private fun canonicalPath(file: File): String = try {
        file.canonicalPath
    } catch (_: Exception) {
        file.absolutePath
    }
}
