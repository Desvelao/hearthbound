package app.habitrpg.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    @Test fun weekdayMask() {
        val s = Schedule.Weekdays.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        assertTrue(s.isDue(D0))                  // Monday
        assertFalse(s.isDue(D0.plusDays(1)))
        assertTrue(s.isDue(D0.plusDays(4)))      // Friday
    }

    @Test fun everyNDays() {
        val s = Schedule.EveryNDays(3, D0)
        assertTrue(s.isDue(D0))
        assertFalse(s.isDue(D0.plusDays(1)))
        assertTrue(s.isDue(D0.plusDays(3)))
        assertTrue(s.isDue(D0.plusDays(6)))
        assertFalse(s.isDue(D0.minusDays(3)))    // before the start date
    }

    @Test fun gameDayRespectsDayStartHour() {
        val night = clockAt(LocalDate.of(2026, 3, 3), hour = 2)
        org.junit.Assert.assertEquals(LocalDate.of(2026, 3, 2), GameDay.today(night, 4))
        org.junit.Assert.assertEquals(LocalDate.of(2026, 3, 3), GameDay.today(night, 0))
    }
}
