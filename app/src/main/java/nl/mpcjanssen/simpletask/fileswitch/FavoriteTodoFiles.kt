package nl.mpcjanssen.simpletask.fileswitch

import org.json.JSONArray
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale

data class FavoriteTodoFile(
    val path: String,
    val label: String? = null
) {
    val fileName: String
        get() = File(path).name

    val parentPath: String
        get() = File(path).parent ?: ""

    val normalizedLabel: String?
        get() = label?.trim()?.takeIf { it.isNotEmpty() }

    val displayName: String
        get() = normalizedLabel ?: fileName

    val detailText: String
        get() = if (normalizedLabel != null) {
            listOf(fileName, parentPath)
                .filter { it.isNotBlank() }
                .joinToString(" — ")
        } else {
            parentPath
        }
}

object FavoriteTodoFiles {
    fun fromStoredValue(value: String?): List<FavoriteTodoFile> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            val trimmed = value.trim()
            if (trimmed.startsWith("[")) {
                parseLegacyJson(trimmed)
            } else if (trimmed.startsWith("{")) {
                emptyList()
            } else {
                parseEncodedLines(trimmed)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toStoredValue(files: List<FavoriteTodoFile>): String {
        return normalizeFavorites(files).joinToString(separator = "\n") { favorite ->
            favorite.normalizedLabel?.let { label ->
                "${favorite.path}\t$label"
            } ?: favorite.path
        }
    }

    fun add(existing: List<FavoriteTodoFile>, file: File, label: String? = null): List<FavoriteTodoFile> {
        val canonicalPath = canonicalPath(file)
        if (existing.any { it.path == canonicalPath }) {
            return normalizeFavorites(existing)
        }
        return normalizeFavorites(existing + FavoriteTodoFile(canonicalPath, label))
    }

    fun remove(existing: List<FavoriteTodoFile>, file: File): List<FavoriteTodoFile> {
        val removedPath = canonicalPath(file)
        return normalizeFavorites(existing.filterNot { it.path == removedPath })
    }

    fun relabel(existing: List<FavoriteTodoFile>, file: File, label: String?): List<FavoriteTodoFile> {
        val targetPath = canonicalPath(file)
        return normalizeFavorites(
            existing.map { favorite ->
                if (favorite.path == targetPath) {
                    favorite.copy(label = label)
                } else {
                    favorite
                }
            }
        )
    }

    private fun normalizeFavorites(favorites: List<FavoriteTodoFile>): List<FavoriteTodoFile> {
        val deduped = LinkedHashMap<String, FavoriteTodoFile>()
        favorites.forEach { favorite ->
            val trimmedPath = favorite.path.trim()
            if (trimmedPath.isEmpty()) {
                return@forEach
            }
            val normalizedFavorite = FavoriteTodoFile(
                path = canonicalPath(File(trimmedPath)),
                label = favorite.normalizedLabel
            )
            deduped.putIfAbsent(normalizedFavorite.path, normalizedFavorite)
        }
        return deduped.values.sortedWith(
            compareBy<FavoriteTodoFile>(
                { it.displayName.toLowerCase(Locale.ROOT) },
                { it.fileName.toLowerCase(Locale.ROOT) },
                { it.path.toLowerCase(Locale.ROOT) }
            )
        )
    }

    private fun canonicalPath(file: File): String = try {
        file.canonicalPath
    } catch (_: Exception) {
        file.absolutePath
    }

    private fun parseLegacyJson(value: String): List<FavoriteTodoFile> {
        val favorites = mutableListOf<FavoriteTodoFile>()
        val json = JSONArray(value)
        for (i in 0 until json.length()) {
            val encoded = json.optString(i).orEmpty().trim()
            if (encoded.isNotEmpty()) {
                favorites.add(decodeFavorite(encoded))
            }
        }
        return normalizeFavorites(favorites)
    }

    private fun parseEncodedLines(value: String): List<FavoriteTodoFile> {
        return normalizeFavorites(
            value.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map(::decodeFavorite)
                .toList()
        )
    }

    private fun decodeFavorite(encoded: String): FavoriteTodoFile {
        val separatorIndex = encoded.indexOf('\t')
        return if (separatorIndex >= 0) {
            FavoriteTodoFile(
                encoded.substring(0, separatorIndex),
                encoded.substring(separatorIndex + 1)
            )
        } else {
            FavoriteTodoFile(encoded)
        }
    }
}
