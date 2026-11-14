package app.habitrpg.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RolloverTest {
    private fun roll(s: GameState, c: FakeClock) = Engine.reduce(s, Event.Rollover, c)

    @Test fun missedDailyDamagesAndResetsStreak() {
        val clock = clockAt(D0.plusDays(1))
        val r = roll(stateOf(daily(diff = Difficulty.MEDIUM, streak = 7)), clock)
        assertEquals(Constants.MAX_HEALTH - 3, r.state.character.health)
        assertEquals(0, r.state.tasks.getValue("d").streak)
        assertEquals(ChangeKind.MISSED, r.changes.single().kind)
        assertEquals(D0.plusDays(1), r.state.character.lastRolloverDate)
    }

    @Test fun completedDailyKeepsStreakAndResetsDoneFlag() {
        val r = roll(stateOf(daily(streak = 3, done = true)), clockAt(D0.plusDays(1)))
        assertEquals(Constants.MAX_HEALTH, r.state.character.health)
        assertEquals(3, r.state.tasks.getValue("d").streak)
        assertEquals(false, r.state.tasks.getValue("d").doneToday)
    }

    @Test fun notDueDailyIsNotPunished() {
        val s = stateOf(daily(schedule = Schedule.Weekdays.of(java.time.DayOfWeek.WEDNESDAY), streak = 2))
        val r = roll(s, clockAt(D0.plusDays(1))) // Monday ended, due only Wednesday
        assertEquals(Constants.MAX_HEALTH, r.state.character.health)
        assertEquals(2, r.state.tasks.getValue("d").streak)
    }

    @Test fun runningTwiceOnTheSameDayDoesNothing() {
        val clock = clockAt(D0.plusDays(1))
        val first = roll(stateOf(daily()), clock)
        val second = roll(first.state, clock)
        assertEquals(first.state, second.state)
        assertTrue(second.changes.isEmpty())
    }

    @Test fun twoMissedDaysDealDamageTwice() {
        val r = roll(stateOf(daily()), clockAt(D0.plusDays(2)))
        assertEquals(Constants.MAX_HEALTH - 4, r.state.character.health)
        assertEquals(2, r.changes.size)
    }

    @Test fun threeDayAbsenceIsTreatedAsPaused() {
        val r = roll(stateOf(daily(streak = 9)), clockAt(D0.plusDays(3)))
        assertEquals(Constants.MAX_HEALTH, r.state.character.health)
        assertEquals(9, r.state.tasks.getValue("d").streak)
        assertTrue(r.changes.isEmpty())
        assertEquals(D0.plusDays(3), r.state.character.lastRolloverDate)
    }

    @Test fun pauseModeSkipsDamageAndFreezesStreak() {
        val r = roll(stateOf(daily(streak = 5), paused = true), clockAt(D0.plusDays(1)))
        assertEquals(Constants.MAX_HEALTH, r.state.character.health)
        assertEquals(5, r.state.tasks.getValue("d").streak)
    }

    @Test fun habitCountsResetAtRollover() {
        val h = habit().copy(plusCount = 4, minusCount = 2)
        val r = roll(stateOf(h), clockAt(D0.plusDays(1)))
        assertEquals(0, r.state.tasks.getValue("h").plusCount)
        assertEquals(0, r.state.tasks.getValue("h").minusCount)
    }

    @Test fun firstRunOnlyAnchorsTheDate() {
        val s = stateOf(daily(), character = Character())
        val r = roll(s, clockAt(D0))
        assertEquals(D0, r.state.character.lastRolloverDate)
        assertTrue(r.changes.isEmpty())
    }

    @Test fun beforeDayStartHourNothingRolls() {
        val clock = clockAt(D0.plusDays(1), hour = 2) // day starts at 04:00
        val r = roll(stateOf(daily(), dayStartHour = 4), clock)
        assertTrue(r.changes.isEmpty())
        assertEquals(D0, r.state.character.lastRolloverDate)
        clock.at(D0.plusDays(1), 5)
        assertEquals(1, roll(stateOf(daily(), dayStartHour = 4), clock).changes.size)
    }

    @Test fun clockMovedBackwardsChangesNothing() {
        val s = stateOf(daily(), character = Character(lastRolloverDate = D0.plusDays(5)))
        val r = roll(s, clockAt(D0))
        assertTrue(r.changes.isEmpty())
        // The stored date never moves back, or the days in between would be scored twice.
        assertEquals(D0.plusDays(5), r.state.character.lastRolloverDate)
    }

    @Test fun clockBackThenForwardNeverScoresADayTwice() {
        val clock = clockAt(D0.plusDays(1))
        val first = roll(stateOf(daily()), clock)
        assertEquals(1, first.changes.size)
        clock.at(D0)                                  // clock set back a day
        val back = roll(first.state, clock)
        clock.at(D0.plusDays(1))                      // and forward again
        val forward = roll(back.state, clock)
        assertTrue(back.changes.isEmpty() && forward.changes.isEmpty())
        assertEquals(Constants.MAX_HEALTH - 2, forward.state.character.health)
    }

    @Test fun timeZoneChangeNeverDoubleCounts() {
        val clock = clockAt(D0.plusDays(1), hour = 23, zone = ZoneId.of("Pacific/Auckland"))
        val first = roll(stateOf(daily()), clock)
        clock.setZone(ZoneId.of("America/Los_Angeles")) // travelling west: still the same local evening or earlier
        val second = roll(first.state, clock)
        assertEquals(1, first.changes.size)
        assertTrue(second.changes.isEmpty())
    }

    @Test fun daylightSavingShortDayStillCountsAsOneDay() {
        val zone = ZoneId.of("Europe/Madrid")
        val springForward = LocalDate.of(2026, 3, 29)
        val s = stateOf(daily(), character = Character(lastRolloverDate = springForward))
        val r = roll(s, clockAt(springForward.plusDays(1), zone = zone))
        assertEquals(1, r.changes.size)
        assertEquals(Constants.MAX_HEALTH - 2, r.state.character.health)
    }

    @Test fun lethalRolloverTriggersHealthZeroOnce() {
        val c = Character(level = 4, health = 2, gold = 20, lastRolloverDate = D0)
        val r = roll(stateOf(daily(), character = c), clockAt(D0.plusDays(1)))
        assertEquals(3, r.state.character.level)
        assertEquals(10, r.state.character.gold)
        assertEquals(listOf(Milestone.HEALTH_ZERO), r.milestones)
    }
}
