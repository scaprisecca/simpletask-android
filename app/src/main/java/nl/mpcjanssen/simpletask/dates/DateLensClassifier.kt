package nl.mpcjanssen.simpletask.dates

import nl.mpcjanssen.simpletask.task.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class WeekStartMode(val storageValue: String) {
    LOCALE("locale"),
    SUNDAY("sunday"),
    MONDAY("monday");

    companion object {
        fun fromStoredValue(value: String?): WeekStartMode {
            return values().firstOrNull { it.storageValue == value } ?: LOCALE
        }
    }
}

enum class DateLensMatchReason {
    DUE,
    THRESHOLD
}

enum class OverdueDateType {
    DUE,
    THRESHOLD
}

data class DateLensClassification(
    val primaryLens: DateLens?,
    val reason: DateLensMatchReason?,
    val overdueDateType: OverdueDateType? = null
) {
    fun matches(lens: DateLens): Boolean = lens == DateLens.ALL || primaryLens == lens
}

class DateLensClassifier(
    private val today: String,
    private val weekStartMode: WeekStartMode = WeekStartMode.LOCALE,
    private val upcomingWindowDays: Int = DEFAULT_UPCOMING_WINDOW_DAYS,
    private val locale: Locale = Locale.getDefault(),
    private val timeZone: TimeZone = TimeZone.getDefault()
) {
    private val weekRange = weekRange(today, weekStartMode, locale, timeZone)
    private val upcomingEnd = addDays(today, upcomingWindowDays.coerceAtLeast(1))

    fun classify(task: Task): DateLensClassification {
        val dueDate = task.dueDate.takeIf { it.isIsoDate() }
        val thresholdDate = task.thresholdDate.takeIf { it.isIsoDate() }

        if (dueDate != null && dueDate < today) {
            return DateLensClassification(DateLens.OVERDUE, DateLensMatchReason.DUE, OverdueDateType.DUE)
        }
        if (thresholdDate != null && thresholdDate < today) {
            return DateLensClassification(DateLens.OVERDUE, DateLensMatchReason.THRESHOLD, OverdueDateType.THRESHOLD)
        }
        if (dueDate == today) {
            return DateLensClassification(DateLens.TODAY, DateLensMatchReason.DUE)
        }
        if (thresholdDate == today) {
            return DateLensClassification(DateLens.TODAY, DateLensMatchReason.THRESHOLD)
        }
        if (dueDate != null && dueDate in weekRange) {
            return DateLensClassification(DateLens.THIS_WEEK, DateLensMatchReason.DUE)
        }
        if (thresholdDate != null && thresholdDate in weekRange) {
            return DateLensClassification(DateLens.THIS_WEEK, DateLensMatchReason.THRESHOLD)
        }
        if (dueDate != null && dueDate > weekRange.endInclusive && dueDate <= upcomingEnd) {
            return DateLensClassification(DateLens.UPCOMING, DateLensMatchReason.DUE)
        }
        if (thresholdDate != null && thresholdDate > weekRange.endInclusive && thresholdDate <= upcomingEnd) {
            return DateLensClassification(DateLens.UPCOMING, DateLensMatchReason.THRESHOLD)
        }
        return DateLensClassification(null, null)
    }

    fun matches(task: Task, lens: DateLens): Boolean = classify(task).matches(lens)

    companion object {
        const val DEFAULT_UPCOMING_WINDOW_DAYS = 14
        const val MIN_UPCOMING_WINDOW_DAYS = 1
        const val MAX_UPCOMING_WINDOW_DAYS = 365

        fun normalizeUpcomingWindowDays(days: Int): Int {
            return days.coerceIn(MIN_UPCOMING_WINDOW_DAYS, MAX_UPCOMING_WINDOW_DAYS)
        }

        private fun weekRange(
            today: String,
            weekStartMode: WeekStartMode,
            locale: Locale,
            timeZone: TimeZone
        ): ClosedRange<String> {
            val calendar = calendarFor(today, locale, timeZone)
            calendar.firstDayOfWeek = when (weekStartMode) {
                WeekStartMode.SUNDAY -> Calendar.SUNDAY
                WeekStartMode.MONDAY -> Calendar.MONDAY
                WeekStartMode.LOCALE -> Calendar.getInstance(locale).firstDayOfWeek
            }
            while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
                calendar.add(Calendar.DATE, -1)
            }
            val start = format(calendar)
            calendar.add(Calendar.DATE, 6)
            return start..format(calendar)
        }

        private fun addDays(date: String, days: Int): String {
            val calendar = calendarFor(date, Locale.US, TimeZone.getDefault())
            calendar.add(Calendar.DATE, days)
            return format(calendar)
        }

        private fun calendarFor(date: String, locale: Locale, timeZone: TimeZone): Calendar {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            parser.isLenient = false
            parser.timeZone = timeZone
            return Calendar.getInstance(timeZone, locale).apply {
                time = parser.parse(date)!!
            }
        }

        private fun format(calendar: Calendar): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            formatter.timeZone = calendar.timeZone
            return formatter.format(calendar.time)
        }
    }
}

private fun String?.isIsoDate(): Boolean {
    if (this == null) {
        return false
    }
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    parser.isLenient = false
    return try {
        parser.parse(this)
        true
    } catch (e: Exception) {
        false
    }
}

