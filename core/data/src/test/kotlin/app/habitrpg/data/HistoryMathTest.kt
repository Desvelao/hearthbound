package app.habitrpg.data

import app.habitrpg.data.repo.HistoryMath
import app.habitrpg.data.repo.LineKind
import app.habitrpg.data.repo.LoggedEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMathTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val d1 = LocalDate.of(2026, 3, 2)
    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(ZoneOffset.UTC)

    private fun ev(day: LocalDate, hour: Int, kind: String, task: String = "t", title: String = "Task",
                   xp: Int = 0, gold: Int = 0, health: Int = 0, minute: Int = 0) =
        LoggedEvent(task, title, at(day, hour, minute), kind, xp, gold, health)
    private fun done(day: LocalDate, hour: Int = 10, task: String = "t") = ev(day, hour, "DONE", task, xp = 10, gold = 4)
    private fun undone(day: LocalDate, hour: Int = 11, task: String = "t") = ev(day, hour, "UNDONE", task, xp = -10, gold = -4)

    private fun build(events: List<LoggedEvent>, today: LocalDate = d1, dayStart: Int = 0) =
        HistoryMath.build(events, utc, dayStart, today)

    // ---- counting ----

    @Test fun plusAndTicksCountButMinusAndMissedDoNot() {
        val h = build(listOf(
            ev(d1, 9, "PLUS", xp = 5), done(d1, 10), ev(d1, 11, "MINUS", health = -2), ev(d1, 12, "MISSED", health = -2),
        ))
        assertEquals(2, h.activity.getValue(d1).tasks)
    }

    @Test fun anUndoTakesBackExactlyOneCompletion() {
        assertEquals(1, build(listOf(done(d1, 9), done(d1, 10, "u"), undone(d1, 11))).activity.getValue(d1).tasks)
    }

    @Test fun undoingAMinusDoesNotSubtractACompletion() {
        val h = build(listOf(done(d1, 9), ev(d1, 10, "MINUS", health = -2), ev(d1, 11, "UNDONE", health = 2)))
        assertEquals(1, h.activity.getValue(d1).tasks)
    }

    @Test fun aDayNeverShowsANegativeCount() {
        assertEquals(0, build(listOf(undone(d1))).activity.getValue(d1).tasks)
    }

    @Test fun xpAndGoldAreSummedNetOfUndos() {
        val a = build(listOf(done(d1, 9), done(d1, 10, "u"), undone(d1, 11, "u"))).activity.getValue(d1)
        assertEquals(10, a.xp); assertEquals(4, a.gold)
    }

    @Test fun theDayStartHourDecidesWhichDayAnEventBelongsTo() {
        val late = ev(d1.plusDays(1), 2, "DONE", xp = 10) // 02:00 with a 04:00 start is still day 1
        assertTrue(d1 in build(listOf(late), dayStart = 4).activity)
        assertTrue(d1.plusDays(1) in build(listOf(late), dayStart = 0).activity)
    }

    // ---- streaks ----

    private fun days(vararg offsets: Int) = offsets.map { d1.plusDays(it.toLong()) }.toSet()

    @Test fun bestStreakIsTheLongestRunOfConsecutiveDays() {
        assertEquals(0, HistoryMath.bestStreak(emptySet()))
        assertEquals(1, HistoryMath.bestStreak(days(0)))
        assertEquals(3, HistoryMath.bestStreak(days(0, 1, 2, 4, 5)))
        assertEquals(4, HistoryMath.bestStreak(days(0, 1, 3, 4, 5, 6)))
    }

    @Test fun currentStreakCountsBackFromTodayWhenTodayIsDone() {
        assertEquals(3, HistoryMath.currentStreak(days(-2, -1, 0), d1))
    }

    @Test fun anUnfinishedTodayDoesNotBreakTheStreak() {
        assertEquals(2, HistoryMath.currentStreak(days(-2, -1), d1))
    }

    @Test fun aMissedDayEndsTheStreak() {
        assertEquals(0, HistoryMath.currentStreak(days(-3, -2), d1))
        assertEquals(1, HistoryMath.currentStreak(days(-3, 0), d1))
    }

    @Test fun streaksComeOutOfTheEventLog() {
        val events = (0..3).map { done(d1.plusDays(it.toLong())) } + done(d1.plusDays(6))
        val h = build(events, today = d1.plusDays(6))
        assertEquals(4, h.bestStreak)
        assertEquals(1, h.currentStreak)
    }

    // ---- heat map ----

    @Test fun heatLevelStepsUpWithBusierDays() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4, 4), listOf(0, 1, 2, 3, 4, 5, 6, 7, 20).map(HistoryMath::heatLevel))
    }

    // ---- week summaries ----

    @Test fun thisWeekAndLastWeekAreSevenDayWindowsEndingToday() {
        val today = d1.plusDays(13)
        val events = listOf(done(today), done(today.minusDays(6)), done(today.minusDays(7)), done(today.minusDays(7), 12, "u"), done(today.minusDays(14)))
        val h = build(events, today)
        assertEquals(2, h.tasksThisWeek)
        assertEquals(2, h.tasksLastWeek)
    }

    @Test fun healthLostCountsTheWeekAndMissedDailies() {
        val today = d1.plusDays(10)
        val h = build(listOf(
            ev(today, 9, "MINUS", health = -2), ev(today.minusDays(1), 0, "MISSED", health = -4),
            ev(today.minusDays(9), 0, "MISSED", health = -6), // older than a week
        ), today)
        assertEquals(6, h.weekHealthLost)
        assertEquals(1, h.weekMissed)
    }

    @Test fun undoingAMinusGivesTheHealthBack() {
        val h = build(listOf(ev(d1, 9, "MINUS", health = -2), ev(d1, 10, "UNDONE", health = 2)))
        assertEquals(0, h.weekHealthLost)
    }

    @Test fun goldEarnedIsNetOfUndos() {
        assertEquals(4, build(listOf(done(d1, 9), done(d1, 10, "u"), undone(d1, 11, "u"))).goldEarned)
    }

    // ---- day detail ----

    @Test fun consecutivePlusTapsOnOneTaskFoldIntoOneRow() {
        val lines = HistoryMath.dayLines(listOf(
            ev(d1, 9, "PLUS", "w", "Water", xp = 5), ev(d1, 10, "PLUS", "w", "Water", xp = 5), ev(d1, 11, "PLUS", "w", "Water", xp = 5),
            done(d1, 12), ev(d1, 13, "PLUS", "w", "Water", xp = 5),
        ))
        assertEquals(listOf(LineKind.PLUS, LineKind.DONE, LineKind.PLUS), lines.map { it.kind })
        assertEquals(listOf(1, 1, 3), lines.map { it.count }) // latest first
        assertEquals(15, lines.last().xp)
    }

    @Test fun consecutiveMinusTapsFoldTooAndAddUpTheirHealth() {
        val lines = HistoryMath.dayLines((0..7).map { ev(d1, 9, "MINUS", "t", "test", health = -2, minute = it) })
        assertEquals(1, lines.size)
        assertEquals(8, lines.single().count)
        assertEquals(-16, lines.single().health)
    }

    @Test fun aPlusAfterAMinusIsNotFoldedIntoIt() {
        val lines = HistoryMath.dayLines(listOf(ev(d1, 9, "MINUS", "t", health = -2), ev(d1, 10, "PLUS", "t", xp = 5), ev(d1, 11, "MINUS", "t", health = -2)))
        assertEquals(listOf(LineKind.MINUS, LineKind.PLUS, LineKind.MINUS), lines.map { it.kind })
    }

    @Test fun plusTapsOnDifferentTasksStaySeparate() {
        val lines = HistoryMath.dayLines(listOf(ev(d1, 9, "PLUS", "a", "A", xp = 5), ev(d1, 10, "PLUS", "b", "B", xp = 5)))
        assertEquals(2, lines.size)
    }

    @Test fun aDeletedTaskStillGetsALabelAndUnknownKindsAreSkipped() {
        val lines = HistoryMath.dayLines(listOf(ev(d1, 9, "DONE", title = ""), ev(d1, 10, "FUTURE_KIND")))
        assertEquals(listOf("(deleted task)"), lines.map { it.title })
    }

    @Test fun lineCarriesEffectsAndIsSortedLatestFirst() {
        val lines = HistoryMath.dayLines(listOf(done(d1, 9), ev(d1, 11, "MISSED", "m", "Read", health = -2)))
        assertEquals(listOf(LineKind.MISSED, LineKind.DONE), lines.map { it.kind })
        assertEquals(-2, lines.first().health)
    }
}
