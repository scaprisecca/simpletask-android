package nl.mpcjanssen.simpletask.calendar

import nl.mpcjanssen.simpletask.task.Task
import nl.mpcjanssen.simpletask.util.toDateTime

class CalendarTaskProjector {
    fun project(
        tasks: List<Task>,
        selectedDate: String,
        visibleMonth: String,
        visibility: ScheduledDateVisibility
    ): CalendarProjection {
        val indicators = linkedMapOf<String, CalendarDateIndicator>()
        val selectedDayTasks = linkedMapOf<Task, CalendarDayTask>()

        tasks.asSequence()
            .filterNot(Task::isCompleted)
            .filterNot(Task::isHidden)
            .forEach { task ->
                val dueDate = task.dueDate.takeIf { visibility.showsDue() && it.isCalendarDate() }
                val thresholdDate = task.thresholdDate.takeIf { visibility.showsThreshold() && it.isCalendarDate() }

                if (dueDate != null && dueDate.startsWith(visibleMonth)) {
                    indicators[dueDate] = indicators[dueDate].merge(hasDue = true)
                }
                if (thresholdDate != null && thresholdDate.startsWith(visibleMonth)) {
                    indicators[thresholdDate] = indicators[thresholdDate].merge(hasThreshold = true)
                }

                val matchesDue = dueDate == selectedDate
                val matchesThreshold = thresholdDate == selectedDate
                if (matchesDue || matchesThreshold) {
                    selectedDayTasks[task] = CalendarDayTask(
                        task = task,
                        matchesDue = matchesDue,
                        matchesThreshold = matchesThreshold
                    )
                }
            }

        return CalendarProjection(
            visibleMonth = visibleMonth,
            indicatorsByDate = indicators,
            selectedDay = CalendarDayModel(
                date = selectedDate,
                tasks = selectedDayTasks.values.toList(),
                indicator = indicators[selectedDate] ?: CalendarDateIndicator()
            )
        )
    }

    private fun String?.isCalendarDate(): Boolean {
        return this?.toDateTime() != null
    }

    private fun CalendarDateIndicator?.merge(
        hasDue: Boolean = false,
        hasThreshold: Boolean = false
    ): CalendarDateIndicator {
        val current = this ?: CalendarDateIndicator()
        return CalendarDateIndicator(
            hasDue = current.hasDue || hasDue,
            hasThreshold = current.hasThreshold || hasThreshold
        )
    }
}
