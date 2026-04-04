package nl.mpcjanssen.simpletask.calendar

import nl.mpcjanssen.simpletask.task.Task

data class CalendarDateIndicator(
    val hasDue: Boolean = false,
    val hasThreshold: Boolean = false
)

data class CalendarDayTask(
    val task: Task,
    val matchesDue: Boolean = false,
    val matchesThreshold: Boolean = false
)

data class CalendarDayModel(
    val date: String,
    val tasks: List<CalendarDayTask>,
    val indicator: CalendarDateIndicator = CalendarDateIndicator()
) {
    val isEmpty: Boolean
        get() = tasks.isEmpty()
}

data class CalendarProjection(
    val visibleMonth: String,
    val indicatorsByDate: Map<String, CalendarDateIndicator>,
    val selectedDay: CalendarDayModel
)
