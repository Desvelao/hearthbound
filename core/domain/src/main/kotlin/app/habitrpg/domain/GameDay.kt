package app.habitrpg.domain

import java.time.Clock
import java.time.LocalDate

/** The game day starts at [dayStartHour], so 01:00 with a 04:00 start still belongs to yesterday. */
object GameDay {
    fun today(clock: Clock, dayStartHour: Int): LocalDate =
        clock.instant().atZone(clock.zone).minusHours(dayStartHour.toLong()).toLocalDate()
}
