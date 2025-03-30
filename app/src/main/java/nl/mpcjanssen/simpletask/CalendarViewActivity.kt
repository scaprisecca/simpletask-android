package nl.mpcjanssen.simpletask

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import hirondelle.date4j.DateTime
import nl.mpcjanssen.simpletask.task.Task
import java.util.*

class CalendarViewActivity : AppCompatActivity() {
    private lateinit var calendarGrid: RecyclerView
    private lateinit var monthYearText: TextView
    private lateinit var currentDayText: TextView
    private var currentDate = DateTime.today(TimeZone.getDefault())
    private val calendarAdapter = CalendarAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.calendar_view)

        // Initialize views
        calendarGrid = findViewById(R.id.calendarGrid)
        monthYearText = findViewById(R.id.monthYearText)
        currentDayText = findViewById(R.id.currentDayText)

        // Set up RecyclerView
        calendarGrid.layoutManager = GridLayoutManager(this, 8) // 1 column for week number + 7 days
        calendarGrid.adapter = calendarAdapter

        // Set up click listeners
        monthYearText.setOnClickListener {
            // TODO: Show month picker
        }

        findViewById<View>(R.id.addTaskButton).setOnClickListener {
            // TODO: Launch add task activity
        }

        updateCalendarView()
    }

    private fun updateCalendarView() {
        // Update month/year display
        monthYearText.text = "${getMonthName(currentDate.month!!)} ${currentDate.year}"
        currentDayText.text = DateTime.today(TimeZone.getDefault()).day.toString()

        // Update calendar grid with tasks
        val tasks = TodoApplication.todoList.tasks
        calendarAdapter.updateCalendar(currentDate, tasks)
    }

    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "January"
            2 -> "February"
            3 -> "March"
            4 -> "April"
            5 -> "May"
            6 -> "June"
            7 -> "July"
            8 -> "August"
            9 -> "September"
            10 -> "October"
            11 -> "November"
            12 -> "December"
            else -> ""
        }
    }
}

class CalendarAdapter : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {
    private var days: List<CalendarDay> = emptyList()
    private var tasks: List<Task> = emptyList()
    
    fun updateCalendar(date: DateTime, tasks: List<Task>) {
        this.tasks = tasks
        days = generateCalendarDays(date)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.calendar_day_item, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val day = days[position]
        
        if (position % 8 == 0) {
            // Week number cell
            holder.dayNumber.text = day.weekNumber.toString()
            holder.taskIndicators.visibility = View.GONE
        } else {
            // Day cell
            holder.dayNumber.text = day.dayNumber.toString()
            
            if (day.isInCurrentMonth) {
                holder.dayNumber.alpha = 1.0f
                
                // Check for tasks on this day
                val dayDate = DateTime.forDateOnly(
                    day.year,
                    day.month,
                    day.dayNumber
                )
                
                var hasDueTask = false
                var hasThresholdTask = false
                
                for (task in tasks) {
                    if (!task.isCompleted()) {
                        task.dueDate?.let { dueDate ->
                            if (dueDate == dayDate.format("YYYY-MM-DD")) {
                                hasDueTask = true
                            }
                        }
                        
                        task.thresholdDate?.let { thresholdDate ->
                            if (thresholdDate == dayDate.format("YYYY-MM-DD")) {
                                hasThresholdTask = true
                            }
                        }
                    }
                }
                
                holder.dueDot.visibility = if (hasDueTask) View.VISIBLE else View.GONE
                holder.thresholdDot.visibility = if (hasThresholdTask) View.VISIBLE else View.GONE
                holder.taskIndicators.visibility = if (hasDueTask || hasThresholdTask) View.VISIBLE else View.GONE
            } else {
                // Fade out days from other months
                holder.dayNumber.alpha = 0.3f
                holder.taskIndicators.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = days.size

    private fun generateCalendarDays(date: DateTime): List<CalendarDay> {
        val calendar = mutableListOf<CalendarDay>()
        val firstDayOfMonth = date.getStartOfMonth()
        val lastDayOfMonth = date.getEndOfMonth()
        
        // Calculate the first Sunday to show (might be in previous month)
        var currentDay = firstDayOfMonth
        while (currentDay.weekDay != 1) { // Sunday is 1 in date4j
            currentDay = currentDay.minusDays(1)
        }

        // Generate 6 weeks of days (42 days + 6 week numbers = 48 total cells)
        repeat(6) { week ->
            val weekNumber = currentDay.weekOfYear
            calendar.add(CalendarDay(
                weekNumber = weekNumber,
                dayNumber = 0,
                month = currentDay.month!!,
                year = currentDay.year!!,
                isInCurrentMonth = false
            ))
            
            repeat(7) { dayOfWeek ->
                val isInCurrentMonth = currentDay.month == date.month
                calendar.add(CalendarDay(
                    weekNumber = weekNumber,
                    dayNumber = currentDay.day!!,
                    month = currentDay.month!!,
                    year = currentDay.year!!,
                    isInCurrentMonth = isInCurrentMonth
                ))
                currentDay = currentDay.plusDays(1)
            }
        }

        return calendar
    }

    class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayNumber: TextView = view.findViewById(R.id.dayNumber)
        val taskIndicators: View = view.findViewById(R.id.taskIndicators)
        val dueDot: View = view.findViewById(R.id.dueDot)
        val thresholdDot: View = view.findViewById(R.id.thresholdDot)
    }
}

data class CalendarDay(
    val weekNumber: Int,
    val dayNumber: Int,
    val month: Int,
    val year: Int,
    val isInCurrentMonth: Boolean
) 