package app.habitrpg.feature.character

import app.habitrpg.data.repo.DayActivity
import app.habitrpg.data.repo.HistoryMath
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** One day on the calendar. [level] is 0 to 4 and drives the colour; [tasks] is what the cell says in words. */
data class CalendarCell(val date: LocalDate, val tasks: Int, val level: Int, val isToday: Boolean, val isFuture: Boolean)

object CalendarGrid {
    /** The seven weekdays in the order a week is drawn, starting at [first]. */
    fun weekdayOrder(first: DayOfWeek): List<DayOfWeek> = List(7) { first.plus(it.toLong()) }

    /** Rows of seven. `null` pads before the 1st and after the last day so every row is full. */
    fun build(month: YearMonth, first: DayOfWeek, today: LocalDate, activity: Map<LocalDate, DayActivity>): List<List<CalendarCell?>> {
        val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
        val cells = MutableList<CalendarCell?>(lead) { null }
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val tasks = activity[date]?.tasks ?: 0
            cells += CalendarCell(date, tasks, HistoryMath.heatLevel(tasks), date == today, date.isAfter(today))
        }
        while (cells.size % 7 != 0) cells += null
        return cells.chunked(7)
    }
}
