package nl.mpcjanssen.simpletask.calendar

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import nl.mpcjanssen.simpletask.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

private data class CalendarCell(
    val date: String?,
    val dayOfMonth: Int? = null
)

class CalendarMonthCellAdapter(
    private val month: String,
    private val selectedDate: String?,
    private val indicatorsByDate: Map<String, CalendarDateIndicator>,
    private val onDaySelected: (String) -> Unit
) : RecyclerView.Adapter<CalendarMonthCellAdapter.CalendarDayViewHolder>() {

    private val cells = buildCells(month)
    private val displayDateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())

    class CalendarDayViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarDayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.calendar_day_cell, parent, false)
        return CalendarDayViewHolder(view)
    }

    override fun getItemCount(): Int = cells.size

    override fun onBindViewHolder(holder: CalendarDayViewHolder, position: Int) {
        val cell = cells[position]
        val context = holder.itemView.context
        val dayNumber = holder.itemView.findViewById<TextView>(R.id.calendar_day_number)
        val dueDot = holder.itemView.findViewById<View>(R.id.calendar_due_dot)
        val thresholdDot = holder.itemView.findViewById<View>(R.id.calendar_threshold_dot)

        if (cell.date == null || cell.dayOfMonth == null) {
            holder.itemView.isClickable = false
            holder.itemView.isFocusable = false
            holder.itemView.isActivated = false
            holder.itemView.contentDescription = null
            dayNumber.text = ""
            dueDot.visibility = View.GONE
            thresholdDot.visibility = View.GONE
            return
        }

        val indicator = indicatorsByDate[cell.date] ?: CalendarDateIndicator()
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.isActivated = cell.date == selectedDate
        holder.itemView.setOnClickListener { onDaySelected(cell.date) }
        dayNumber.text = cell.dayOfMonth.toString()
        dueDot.visibility = if (indicator.hasDue) View.VISIBLE else View.GONE
        thresholdDot.visibility = if (indicator.hasThreshold) View.VISIBLE else View.GONE
        dueDot.background = dotDrawable(context.getColorCompat(R.color.calendar_due_dot))
        thresholdDot.background = dotDrawable(context.getColorCompat(R.color.calendar_threshold_dot))
        holder.itemView.contentDescription = buildContentDescription(context, cell.date, indicator, holder.itemView.isActivated)
    }

    private fun buildContentDescription(
        context: android.content.Context,
        isoDate: String,
        indicator: CalendarDateIndicator,
        selected: Boolean
    ): String {
        val calendar = parseDate(isoDate)
        val base = displayDateFormat.format(calendar.time)
        val state = when {
            indicator.hasDue && indicator.hasThreshold -> context.getString(R.string.calendar_day_content_both, base)
            indicator.hasDue -> context.getString(R.string.calendar_day_content_due, base)
            indicator.hasThreshold -> context.getString(R.string.calendar_day_content_threshold, base)
            else -> context.getString(R.string.calendar_day_content_none, base)
        }
        return if (selected) {
            context.getString(R.string.calendar_day_content_selected, state)
        } else {
            state
        }
    }

    private fun parseDate(isoDate: String): Calendar {
        val year = isoDate.substring(0, 4).toInt()
        val month = isoDate.substring(5, 7).toInt()
        val day = isoDate.substring(8, 10).toInt()
        return GregorianCalendar(year, month - 1, day)
    }

    private fun dotDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun android.content.Context.getColorCompat(colorRes: Int): Int {
        return ContextCompat.getColor(this, colorRes)
    }

    private fun buildCells(month: String): List<CalendarCell> {
        val year = month.substring(0, 4).toInt()
        val monthNumber = month.substring(5, 7).toInt()
        val firstDay = GregorianCalendar(year, monthNumber - 1, 1)
        val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
        val leadingEmpty = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val cells = ArrayList<CalendarCell>(42)
        repeat(leadingEmpty) {
            cells.add(CalendarCell(date = null))
        }
        for (day in 1..daysInMonth) {
            cells.add(
                CalendarCell(
                    date = String.format(Locale.US, "%04d-%02d-%02d", year, monthNumber, day),
                    dayOfMonth = day
                )
            )
        }
        while (cells.size < 42) {
            cells.add(CalendarCell(date = null))
        }
        return cells
    }
}
