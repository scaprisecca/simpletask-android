package nl.mpcjanssen.simpletask.fileswitch

import java.io.File

data class FavoriteFileSwitcherRow(
    val favorite: FavoriteTodoFile,
    val isActive: Boolean,
    val title: String,
    val subtitle: String
)

object FavoriteFileSwitcherModel {
    fun buildRows(
        favorites: List<FavoriteTodoFile>,
        activeFile: File,
        loadedFilePath: String? = null
    ): List<FavoriteFileSwitcherRow> {
        val activePath = loadedFilePath?.let { canonicalPath(File(it)) } ?: canonicalPath(activeFile)
        return favorites.map {
            FavoriteFileSwitcherRow(
                favorite = it,
                isActive = canonicalPath(File(it.path)) == activePath,
                title = it.displayName,
                subtitle = it.detailText
            )
        }
    }

    private fun canonicalPath(file: File): String = try {
        file.canonicalPath
    } catch (_: Exception) {
        file.absolutePath
    }
}
