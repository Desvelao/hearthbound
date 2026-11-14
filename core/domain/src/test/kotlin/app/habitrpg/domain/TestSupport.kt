package app.habitrpg.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the tests can move. */
class FakeClock(var now: Instant, private var tz: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = tz
    fun setZone(zone: ZoneId) { tz = zone }
    override fun withZone(zone: ZoneId): Clock = FakeClock(now, zone)
    override fun instant(): Instant = now
    fun at(date: LocalDate, hour: Int = 12) { now = date.atTime(hour, 0).atZone(tz).toInstant() }
}

val D0: LocalDate = LocalDate.of(2026, 3, 2) // a Monday

fun clockAt(date: LocalDate = D0, hour: Int = 12, zone: ZoneId = ZoneOffset.UTC) =
    FakeClock(date.atTime(hour, 0).atZone(zone).toInstant(), zone)

fun daily(id: String = "d", diff: Difficulty = Difficulty.EASY, streak: Int = 0, done: Boolean = false,
          schedule: Schedule = Schedule.Weekdays.EVERY_DAY) =
    Task(id, TaskType.DAILY, diff, schedule = schedule, streak = streak, doneToday = done)

fun habit(id: String = "h", diff: Difficulty = Difficulty.EASY) = Task(id, TaskType.HABIT, diff)
fun todo(id: String = "t", diff: Difficulty = Difficulty.EASY) = Task(id, TaskType.TODO, diff)

fun stateOf(vararg tasks: Task, character: Character = Character(lastRolloverDate = D0), paused: Boolean = false,
            dayStartHour: Int = 0) =
    GameState(character, tasks.associateBy { it.id }, paused, dayStartHour)
