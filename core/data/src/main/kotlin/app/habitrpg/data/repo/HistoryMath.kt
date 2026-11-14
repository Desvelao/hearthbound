package app.habitrpg.data.repo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One logged change reduced to what history needs. */
data class LoggedEvent(
    val taskId: String,
    val title: String,
    val at: Instant,
    val kind: String,
    val xp: Int,
    val gold: Int,
    val health: Int,
)

/** What happened on one game day. [tasks] counts plus taps and ticks, net of anything taken back. */
data class DayActivity(val date: LocalDate, val tasks: Int, val xp: Int, val gold: Int)

enum class LineKind { DONE, PLUS, MINUS, MISSED, UNDONE }

/** One row of a day's detail. Consecutive plus taps (or minus taps) on one task are folded into a single row with a [count]. */
data class DayLine(
    val title: String,
    val kind: LineKind,
    val count: Int,
    val xp: Int,
    val gold: Int,
    val health: Int,
    val at: Instant,
)

/** Everything the History and Hero screens show, computed in one pass over the event log. */
data class HistoryData(
    val activity: Map<LocalDate, DayActivity> = emptyMap(),
    /** Latest first. */
    val lines: Map<LocalDate, List<DayLine>> = emptyMap(),
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val goldEarned: Int = 0,
    val weekHealthLost: Int = 0,
    val weekMissed: Int = 0,
    val tasksThisWeek: Int = 0,
    val tasksLastWeek: Int = 0,
)

/** Pure functions over the event log, so streaks and counts can be tested without a database. */
object HistoryMath {

    fun gameDay(at: Instant, zone: ZoneId, dayStartHour: Int): LocalDate =
        at.atZone(zone).minusHours(dayStartHour.toLong()).toLocalDate()

    /** +1 for a plus tap or a tick, -1 for the undo of one, 0 for everything else. */
    fun completionDelta(e: LoggedEvent): Int = when {
        e.kind == "PLUS" || e.kind == "DONE" -> 1
        e.kind == "UNDONE" && (e.xp < 0 || e.gold < 0) -> -1 // an undone minus has positive health and no xp or gold
        else -> 0
    }

    /** The longest run of consecutive days that each have at least one completion. */
    fun bestStreak(activeDays: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in activeDays.sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /** Days in a row up to today. A day that is not over yet does not break the run, so counting starts from yesterday. */
    fun currentStreak(activeDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in activeDays) today else today.minusDays(1)
        var run = 0
        while (day in activeDays) {
            run++
            day = day.minusDays(1)
        }
        return run
    }

    /** 0 for nothing, then four steps for light to busy days. */
    fun heatLevel(tasks: Int): Int = when {
        tasks <= 0 -> 0
        tasks <= 2 -> 1
        tasks <= 4 -> 2
        tasks <= 6 -> 3
        else -> 4
    }

    fun build(events: List<LoggedEvent>, zone: ZoneId, dayStartHour: Int, today: LocalDate): HistoryData {
        val byDay = events.groupBy { gameDay(it.at, zone, dayStartHour) }
        val activity = byDay.mapValues { (day, list) ->
            DayActivity(day, list.sumOf(::completionDelta).coerceAtLeast(0), list.sumOf { it.xp }, list.sumOf { it.gold })
        }
        val active = activity.filterValues { it.tasks > 0 }.keys

        fun tasksBetween(from: LocalDate, to: LocalDate) =
            activity.values.filter { it.date in from..to }.sumOf { it.tasks }

        val weekStart = today.minusDays(6)
        val week = events.filter { gameDay(it.at, zone, dayStartHour) in weekStart..today }
        return HistoryData(
            activity = activity,
            lines = byDay.mapValues { (_, list) -> dayLines(list) },
            bestStreak = bestStreak(active),
            currentStreak = currentStreak(active, today),
            goldEarned = events.sumOf { it.gold }.coerceAtLeast(0),
            weekHealthLost = (-week.sumOf { it.health }).coerceAtLeast(0),
            weekMissed = week.count { it.kind == "MISSED" },
            tasksThisWeek = tasksBetween(weekStart, today),
            tasksLastWeek = tasksBetween(weekStart.minusDays(7), weekStart.minusDays(1)),
        )
    }

    /** Chronological input; latest-first output, with runs of plus taps (or minus taps) on one task folded together. */
    fun dayLines(dayEvents: List<LoggedEvent>): List<DayLine> {
        val lines = mutableListOf<DayLine>()
        var lastTask: String? = null
        for (e in dayEvents.sortedBy { it.at }) {
            val kind = LineKind.entries.firstOrNull { it.name == e.kind } ?: continue
            val last = lines.lastOrNull()
            val foldable = kind == LineKind.PLUS || kind == LineKind.MINUS
            if (foldable && last != null && last.kind == kind && lastTask == e.taskId) {
                lines[lines.lastIndex] = last.copy(
                    count = last.count + 1, xp = last.xp + e.xp, gold = last.gold + e.gold, health = last.health + e.health, at = e.at,
                )
            } else {
                lines += DayLine(e.title.ifBlank { "(deleted task)" }, kind, 1, e.xp, e.gold, e.health, e.at)
            }
            lastTask = e.taskId
        }
        return lines.reversed()
    }
}
