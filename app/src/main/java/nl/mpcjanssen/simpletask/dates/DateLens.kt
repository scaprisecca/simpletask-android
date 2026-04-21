package nl.mpcjanssen.simpletask.dates

enum class DateLens(val storageValue: String, val displayName: String) {
    ALL("all", "All"),
    OVERDUE("overdue", "Overdue"),
    TODAY("today", "Today"),
    THIS_WEEK("this_week", "This Week"),
    UPCOMING("upcoming", "Upcoming");

    fun isActiveFilter(): Boolean = this != ALL

    companion object {
        fun fromStoredValue(value: String?): DateLens {
            return values().firstOrNull { it.storageValue == value } ?: ALL
        }
    }
}

