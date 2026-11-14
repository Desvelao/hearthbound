package app.habitrpg.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimesTest {
    private val utc = ZoneId.of("UTC")
    private val EVERY = 0b1111111
    private fun at(date: LocalDate, h: Int, m: Int = 0, zone: ZoneId = utc) = ZonedDateTime.of(date, java.time.LocalTime.of(h, m), zone)

    @Test fun laterTodayWhenTheTimeHasNotPassed() {
        assertEquals(at(D0, 18), ReminderTimes.next(at(D0, 9), 18 * 60, EVERY))
    }

    @Test fun tomorrowWhenTheTimeHasPassed() {
        assertEquals(at(D0.plusDays(1), 8), ReminderTimes.next(at(D0, 9), 8 * 60, EVERY))
    }

    @Test fun exactlyNowMeansTheNextOccurrenceNotNow() {
        assertEquals(at(D0.plusDays(1), 9), ReminderTimes.next(at(D0, 9), 9 * 60, EVERY))
    }

    @Test fun skipsDaysNotInTheMask() {
        val monAndThu = Schedule.Weekdays.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY).mask
        assertEquals(at(D0.plusDays(3), 8), ReminderTimes.next(at(D0, 9), 8 * 60, monAndThu)) // Mon 09:00 -> Thu
        assertEquals(at(D0.plusDays(7), 8), ReminderTimes.next(at(D0.plusDays(3), 9), 8 * 60, monAndThu)) // Thu -> next Mon
    }

    @Test fun emptyMaskNeverFires() {
        assertNull(ReminderTimes.next(at(D0, 9), 8 * 60, 0))
    }

    @Test fun nonexistentDaylightSavingTimeMovesForward() {
        val madrid = ZoneId.of("Europe/Madrid")
        val day = LocalDate.of(2026, 3, 29) // 02:00 -> 03:00
        val next = ReminderTimes.next(at(day.minusDays(1), 12, zone = madrid), 2 * 60 + 30, EVERY)!!
        assertEquals(day, next.toLocalDate())
        assertEquals(3, next.hour) // 02:30 does not exist; java.time shifts it to 03:30
    }

    @Test fun staysAtWallClockTimeAcrossDaylightSaving() {
        val madrid = ZoneId.of("Europe/Madrid")
        val next = ReminderTimes.next(at(LocalDate.of(2026, 3, 28), 12, zone = madrid), 8 * 60, EVERY)!!
        val after = ReminderTimes.next(next, 8 * 60, EVERY)!! // crosses the change
        assertEquals(8, next.hour)
        assertEquals(8, after.hour)
    }
}

class ReminderPolicyTest {
    @Test fun dailyOnlyWhenDueAndNotDone() {
        assertTrue(ReminderPolicy.shouldNotify(daily(), D0))
        assertFalse(ReminderPolicy.shouldNotify(daily(done = true), D0))
        assertFalse(ReminderPolicy.shouldNotify(daily(schedule = Schedule.Weekdays.of(DayOfWeek.FRIDAY)), D0))
    }

    @Test fun todoOnlyWhileOpenAndHabitsAlways() {
        assertTrue(ReminderPolicy.shouldNotify(todo(), D0))
        assertFalse(ReminderPolicy.shouldNotify(todo().copy(completed = true), D0))
        assertTrue(ReminderPolicy.shouldNotify(habit(), D0))
    }
}

class AnchorTodayTest {
    @Test fun changingTheDayStartNeverCausesDamage() {
        // 02:00 on day 2 with a 04:00 day start still belongs to day 1. Moving the start to 00:00 makes "today" day 2.
        val s0 = stateOf(daily(), dayStartHour = 4)
        val clock = clockAt(D0.plusDays(1), hour = 2)
        val s1 = s0.copy(dayStartHour = 0)
        assertEquals(1, Engine.reduce(s1, Event.Rollover, clock).changes.size) // a plain rollover would punish...
        val anchored = Engine.reduce(s1, Event.AnchorToday, clock)             // ...anchoring does not
        assertEquals(D0.plusDays(1), anchored.state.character.lastRolloverDate)
        assertTrue(Engine.reduce(anchored.state, Event.Rollover, clock).changes.isEmpty())
        assertEquals(Constants.MAX_HEALTH, anchored.state.character.health)
    }

    @Test fun anchoringNeverMovesTheDateBackwards() {
        // 02:00 on day 2, start 00:00 -> day 2 already scored. Moving the start to 04:00 makes "today" day 1 again,
        // but the stored date must stay on day 2 or day 1 would be scored a second time.
        val clock = clockAt(D0.plusDays(1), hour = 2)
        val s = stateOf(daily(), character = Character(lastRolloverDate = D0.plusDays(1)), dayStartHour = 4)
        val anchored = Engine.reduce(s, Event.AnchorToday, clock)
        assertEquals(D0.plusDays(1), anchored.state.character.lastRolloverDate)
        assertTrue(Engine.reduce(anchored.state, Event.Rollover, clock).changes.isEmpty())
    }

    @Test fun anchoringTwiceIsANoOp() {
        val clock = clockAt(D0)
        val once = Engine.reduce(stateOf(daily()), Event.AnchorToday, clock)
        assertEquals(once.state, Engine.reduce(once.state, Event.AnchorToday, clock).state)
    }
}
