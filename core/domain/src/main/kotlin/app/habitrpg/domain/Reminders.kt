package app.habitrpg.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object ReminderTimes {
    /**
     * The next moment strictly after [now] at wall-clock [minuteOfDay] on a weekday in [weekdayMask]
     * (bit 0 = Monday). Null when no weekday is selected. A time that does not exist on a daylight-saving
     * day moves forward by the gap, as `java.time` does.
     */
    fun next(now: ZonedDateTime, minuteOfDay: Int, weekdayMask: Int): ZonedDateTime? {
        if (weekdayMask and 0b1111111 == 0) return null
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (weekdayMask and (1 shl (date.dayOfWeek.value - 1)) == 0) continue
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }
}

object ReminderPolicy {
    /** A reminder for something already done, or not due today, would only be noise. */
    fun shouldNotify(task: Task, today: LocalDate): Boolean = when (task.type) {
        TaskType.HABIT -> true
        TaskType.DAILY -> !task.doneToday && (task.schedule?.isDue(today) ?: false)
        TaskType.TODO -> !task.completed
    }
}
