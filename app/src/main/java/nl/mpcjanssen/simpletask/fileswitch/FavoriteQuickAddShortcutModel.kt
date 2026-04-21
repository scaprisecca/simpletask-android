package nl.mpcjanssen.simpletask.fileswitch

data class FavoriteQuickAddShortcutSpec(
    val favorite: FavoriteTodoFile,
    val label: String
)

object FavoriteQuickAddShortcutModel {
    fun buildSpecs(favorites: List<FavoriteTodoFile>): List<FavoriteQuickAddShortcutSpec> {
        val duplicateCounts = favorites.groupingBy { it.displayName }.eachCount()
        return favorites.map { favorite ->
            val label = if ((duplicateCounts[favorite.displayName] ?: 0) > 1) {
                listOf(favorite.displayName, favorite.detailText.ifBlank { "/" })
                    .filter { it.isNotBlank() }
                    .joinToString(" — ")
            } else {
                favorite.displayName
            }
            FavoriteQuickAddShortcutSpec(favorite = favorite, label = label)
        }
    }
}
