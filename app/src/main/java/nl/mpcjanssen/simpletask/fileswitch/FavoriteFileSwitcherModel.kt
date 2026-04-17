package nl.mpcjanssen.simpletask.fileswitch

import java.io.File

data class FavoriteFileSwitcherRow(
    val favorite: FavoriteTodoFile,
    val isActive: Boolean
)

object FavoriteFileSwitcherModel {
    fun buildRows(favorites: List<FavoriteTodoFile>, activeFile: File): List<FavoriteFileSwitcherRow> {
        val activePath = canonicalPath(activeFile)
        return favorites.map {
            FavoriteFileSwitcherRow(
                favorite = it,
                isActive = it.path == activePath
            )
        }
    }

    private fun canonicalPath(file: File): String = try {
        file.canonicalPath
    } catch (_: Exception) {
        file.absolutePath
    }
}
