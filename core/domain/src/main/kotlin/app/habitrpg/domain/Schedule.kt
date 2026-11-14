package app.habitrpg.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed interface Schedule {
    fun isDue(date: LocalDate): Boolean

    /** Bit 0 = Monday ... bit 6 = Sunday. */
    data class Weekdays(val mask: Int) : Schedule {
        override fun isDue(date: LocalDate) = mask and (1 shl (date.dayOfWeek.value - 1)) != 0

        companion object {
            fun of(vararg days: DayOfWeek) = Weekdays(days.fold(0) { m, d -> m or (1 shl (d.value - 1)) })
            val EVERY_DAY = Weekdays(0b1111111)
        }
    }

    data class EveryNDays(val n: Int, val start: LocalDate) : Schedule {
        init { require(n >= 1) { "n must be at least 1" } }

        override fun isDue(date: LocalDate): Boolean {
            val d = ChronoUnit.DAYS.between(start, date)
            return d >= 0 && d % n == 0L
        }
    }
}
