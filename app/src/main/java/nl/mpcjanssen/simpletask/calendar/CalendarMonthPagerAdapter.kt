package nl.mpcjanssen.simpletask.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nl.mpcjanssen.simpletask.R
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

class CalendarMonthPagerAdapter(
    private val onDaySelected: (String) -> Unit,
    private val indicatorsForMonth: (String) -> Map<String, CalendarDateIndicator>
) : RecyclerView.Adapter<CalendarMonthPagerAdapter.CalendarMonthPageViewHolder>() {

    var selectedDate: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class CalendarMonthPageViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarMonthPageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.calendar_month_page, parent, false)
        return CalendarMonthPageViewHolder(view)
    }

    override fun getItemCount(): Int = MONTH_COUNT

    override fun onBindViewHolder(holder: CalendarMonthPageViewHolder, position: Int) {
        val month = monthForPosition(position)
        val recyclerView = holder.itemView.findViewById<RecyclerView>(R.id.calendar_month_grid)
        if (recyclerView.layoutManager == null) {
            recyclerView.layoutManager = GridLayoutManager(holder.itemView.context, 7)
        }
        recyclerView.adapter = CalendarMonthCellAdapter(
            month = month,
            selectedDate = selectedDate,
            indicatorsByDate = indicatorsForMonth(month),
            onDaySelected = onDaySelected
        )
    }

    companion object {
        private const val MONTH_COUNT = 2400
        private const val BASE_POSITION = MONTH_COUNT / 2
        private val baseMonth = GregorianCalendar().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }

        fun monthForPosition(position: Int): String {
            val offset = position - BASE_POSITION
            val calendar = baseMonth.clone() as Calendar
            calendar.add(Calendar.MONTH, offset)
            return String.format(
                Locale.US,
                "%04d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1
            )
        }

        fun positionForMonth(month: String): Int {
            val target = parseMonth(month)
            val yearOffset = target.get(Calendar.YEAR) - baseMonth.get(Calendar.YEAR)
            val monthOffset = target.get(Calendar.MONTH) - baseMonth.get(Calendar.MONTH)
            return BASE_POSITION + (yearOffset * 12) + monthOffset
        }

        fun titleForMonth(month: String): String {
            val calendar = parseMonth(month)
            val monthName = DateFormatSymbols.getInstance().months[calendar.get(Calendar.MONTH)]
            return "$monthName ${calendar.get(Calendar.YEAR)}"
        }

        fun clampDayToMonth(month: String, selectedDate: String?): String {
            val target = parseMonth(month)
            val preferredDay = selectedDate?.takeIf { it.length >= 10 }?.substring(8, 10)?.toIntOrNull() ?: target.get(Calendar.DAY_OF_MONTH)
            val maxDay = target.getActualMaximum(Calendar.DAY_OF_MONTH)
            val clampedDay = minOf(preferredDay, maxDay)
            return String.format(
                Locale.US,
                "%04d-%02d-%02d",
                target.get(Calendar.YEAR),
                target.get(Calendar.MONTH) + 1,
                clampedDay
            )
        }

        private fun parseMonth(month: String): Calendar {
            val year = month.substring(0, 4).toInt()
            val monthNumber = month.substring(5, 7).toInt()
            return GregorianCalendar(year, monthNumber - 1, 1)
        }
    }
}
