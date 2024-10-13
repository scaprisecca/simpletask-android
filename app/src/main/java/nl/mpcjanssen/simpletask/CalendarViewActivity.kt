package nl.mpcjanssen.simpletask

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.CalendarDay
import nl.mpcjanssen.simpletask.task.TodoList

class CalendarViewActivity : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar_view)

        calendarView = findViewById(R.id.calendarView)
        setupCalendar()
    }

    private fun setupCalendar() {
        calendarView.setOnMonthChangedListener { widget, date ->
            updateCalendarDecorators(date.month, date.year)
        }

        // Set current month
        val currentDate = CalendarDay.today()
        updateCalendarDecorators(currentDate.month, currentDate.year)
    }

    private fun updateCalendarDecorators(month: Int, year: Int) {
        calendarView.removeDecorators()

        val todoList = TodoApplication.todoList
        val datesWithTasks = getDatesWithTasks(todoList, month, year)

        val decorator = EventDecorator(datesWithTasks)
        calendarView.addDecorator(decorator)
    }

    private fun getDatesWithTasks(todoList: TodoList, month: Int, year: Int): List<CalendarDay> {
        return todoList.tasks.filter { task ->
            task.due?.let { dueDate ->
                dueDate.month == month && dueDate.year == year
            } ?: false
        }.map { task ->
            CalendarDay.from(task.due!!.year, task.due!!.month, task.due!!.dayOfMonth)
        }.distinct()
    }
}
