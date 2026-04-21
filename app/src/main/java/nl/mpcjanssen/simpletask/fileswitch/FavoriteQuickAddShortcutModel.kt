package nl.mpcjanssen.simpletask.fileswitch

data class FavoriteQuickAddShortcutSpec(
    val favorite: FavoriteTodoFile,
    val label: String
)

object FavoriteQuickAddShortcutModel {
    fun buildSpecs(favorites: List<FavoriteTodoFile>): List<FavoriteQuickAddShortcutSpec> {
        val duplicateCounts = favorites.groupingBy { it.fileName }.eachCount()
        return favorites.map { favorite ->
            val label = if ((duplicateCounts[favorite.fileName] ?: 0) > 1) {
                "${favorite.fileName} — ${favorite.parentPath.ifBlank { "/" }}"
            } else {
                favorite.fileName
            }
            FavoriteQuickAddShortcutSpec(favorite = favorite, label = label)
        }
    }
}
