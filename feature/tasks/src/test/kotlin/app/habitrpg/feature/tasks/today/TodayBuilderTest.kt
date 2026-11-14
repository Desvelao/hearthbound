package app.habitrpg.feature.tasks.today

import app.habitrpg.data.repo.ReminderItem
import app.habitrpg.data.repo.TaskItem
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.Task
import app.habitrpg.domain.TaskType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayBuilderTest {
    private val today = LocalDate.of(2026, 11, 13) // a Friday
    private val zone = ZoneOffset.UTC

    private fun item(task: Task, title: String = task.id, due: LocalDate? = null, doneAt: Instant? = null, reminders: List<ReminderItem> = emptyList()) =
        TaskItem(task, title, "", 0, due, doneAt, emptyList(), emptySet(), reminders)

    private fun daily(id: String, schedule: Schedule = Schedule.Weekdays.EVERY_DAY, done: Boolean = false, streak: Int = 0, d: Difficulty = Difficulty.EASY) =
        item(Task(id, TaskType.DAILY, d, schedule = schedule, streak = streak, doneToday = done))

    private fun todo(id: String, due: LocalDate? = null, done: Boolean = false, doneAt: Instant? = null, d: Difficulty = Difficulty.EASY) =
        item(Task(id, TaskType.TODO, d, completed = done), due = due, doneAt = doneAt)

    private fun habit(id: String) = item(Task(id, TaskType.HABIT))

    private fun build(
        habits: List<TaskItem> = emptyList(), dailies: List<TaskItem> = emptyList(), todos: List<TaskItem> = emptyList(),
        filter: TodayFilter = TodayFilter.ALL, nowMinute: Int = 12 * 60, dayStart: Int = 0,
    ) = TodayBuilder.build(habits, dailies, todos, today, nowMinute, zone, dayStart, filter)

    private fun ids(rows: List<TodayRow>) = rows.map { it.item.id }

    // ---- dailies ----

    @Test fun aDailyDueTodayIsDueNowUntilItIsDone() {
        val open = build(dailies = listOf(daily("a"), daily("b", done = true)))
        assertEquals(listOf("a"), ids(open.dueNow))
        assertEquals(listOf("b"), ids(open.done))
    }

    @Test fun aDailyNotDueTodayDoesNotAppearAnywhere() {
        val s = build(dailies = listOf(daily("mon", Schedule.Weekdays.of(DayOfWeek.MONDAY)), daily("every3", Schedule.EveryNDays(3, today.minusDays(1)))))
        assertTrue(s.dueNow.isEmpty() && s.done.isEmpty())
        assertEquals(0, s.total)
    }

    // ---- to-dos ----

    @Test fun todosAreInPlayWhenUndatedOrDueByToday() {
        val s = build(todos = listOf(todo("future", today.plusDays(1)), todo("today", today), todo("undated"), todo("overdue", today.minusDays(3))))
        assertEquals(listOf("overdue", "today", "undated"), ids(s.dueNow)) // overdue first, undated last
    }

    @Test fun aTodoCompletedTodayCountsAsDoneButOneFinishedYesterdayDoesNot() {
        val s = build(todos = listOf(
            todo("now", done = true, doneAt = Instant.parse("2026-11-13T09:00:00Z")),
            todo("old", done = true, doneAt = Instant.parse("2026-11-12T09:00:00Z")),
        ))
        assertEquals(listOf("now"), ids(s.done))
        assertEquals(1, s.total)
    }

    @Test fun theDayStartHourDecidesWhatCountsAsDoneToday() {
        val after = Instant.parse("2026-11-14T02:00:00Z") // 02:00 belongs to the 13th when the day starts at 04:00
        val todos = listOf(todo("late", done = true, doneAt = after))
        assertEquals(listOf("late"), ids(build(todos = todos, dayStart = 4).done))
        assertTrue(build(todos = todos, dayStart = 0).done.isEmpty())
    }

    // ---- counting and habits ----

    @Test fun theRingCountsDailiesAndTodosAndLeavesHabitsOut() {
        val s = build(
            habits = listOf(habit("h")),
            dailies = listOf(daily("a"), daily("b", done = true)),
            todos = listOf(todo("t"), todo("u", done = true, doneAt = Instant.parse("2026-11-13T08:00:00Z"))),
        )
        assertEquals(4, s.total)
        assertEquals(2, s.finished)
        assertEquals(listOf("h"), ids(s.habits))
    }

    // ---- filters ----

    private val mixed get() = build(
        habits = listOf(habit("h")), dailies = listOf(daily("d"), daily("dd", done = true)), todos = listOf(todo("t")),
    )

    @Test fun filteringByDailiesKeepsOnlyDailies() {
        val s = build(habits = listOf(habit("h")), dailies = listOf(daily("d"), daily("dd", done = true)), todos = listOf(todo("t")), filter = TodayFilter.DAILIES)
        assertEquals(listOf("d"), ids(s.dueNow)); assertEquals(listOf("dd"), ids(s.done)); assertTrue(s.habits.isEmpty())
    }

    @Test fun filteringByTodosKeepsOnlyTodos() {
        val s = build(habits = listOf(habit("h")), dailies = listOf(daily("d")), todos = listOf(todo("t")), filter = TodayFilter.TODOS)
        assertEquals(listOf("t"), ids(s.dueNow)); assertTrue(s.habits.isEmpty())
    }

    @Test fun filteringByHabitsShowsOnlyHabits() {
        val s = build(habits = listOf(habit("h")), dailies = listOf(daily("d")), todos = listOf(todo("t")), filter = TodayFilter.HABITS)
        assertTrue(s.dueNow.isEmpty() && s.done.isEmpty()); assertEquals(listOf("h"), ids(s.habits))
    }

    @Test fun theRingDoesNotChangeWithTheFilter() {
        val all = mixed
        val habitsOnly = build(habits = listOf(habit("h")), dailies = listOf(daily("d"), daily("dd", done = true)), todos = listOf(todo("t")), filter = TodayFilter.HABITS)
        assertEquals(all.total, habitsOnly.total); assertEquals(all.finished, habitsOnly.finished)
    }

    @Test fun dailiesComeBeforeTodosInTheDueList() {
        assertEquals(listOf("d", "t"), ids(mixed.dueNow))
    }

    // ---- row details ----

    @Test fun xpPreviewUsesTheStreakBonusForDailies() {
        val s = build(dailies = listOf(daily("d", streak = 20, d = Difficulty.MEDIUM)), todos = listOf(todo("t", d = Difficulty.HARD)))
        assertEquals(21, s.dueNow.first { it.item.id == "d" }.xp) // 15 * 1.4
        assertEquals(20, s.dueNow.first { it.item.id == "t" }.xp)
    }

    @Test fun todoNotesSayOverdueOrDueToday() {
        val s = build(todos = listOf(todo("o", today.minusDays(1)), todo("t", today), todo("n")))
        assertEquals(listOf(RowNote.Overdue, RowNote.DueToday, null), s.dueNow.map { it.note })
    }

    @Test fun theNoteIsTheNextReminderStillToComeToday() {
        val rem = listOf(ReminderItem("a", 8 * 60, 0b1111111), ReminderItem("b", 20 * 60, 0b1111111), ReminderItem("c", 18 * 60, 0b1111111))
        val s = build(dailies = listOf(item(Task("d", TaskType.DAILY, schedule = Schedule.Weekdays.EVERY_DAY), reminders = rem)), nowMinute = 12 * 60)
        assertEquals(RowNote.Reminder(18 * 60), s.dueNow.single().note)
    }

    @Test fun remindersForOtherWeekdaysOrAlreadyPastGiveNoNote() {
        val sat = DayOfWeek.SATURDAY.let { 1 shl (it.value - 1) }
        val rem = listOf(ReminderItem("a", 8 * 60, 0b1111111), ReminderItem("b", 20 * 60, sat))
        val s = build(dailies = listOf(item(Task("d", TaskType.DAILY, schedule = Schedule.Weekdays.EVERY_DAY), reminders = rem)), nowMinute = 12 * 60)
        assertNull(s.dueNow.single().note)
    }

    // ---- gear and task value feed the XP preview ----

    @Test fun thePreviewUsesGearBonusesAndTaskValueLikeTheEngineDoes() {
        val farmed = item(Task("h", TaskType.HABIT, Difficulty.HARD, value = 4.0))      // x0.8
        val old = todo("t", d = Difficulty.HARD).let { it.copy(task = it.task.copy(value = -10.0)) } // x1.5
        val gear = app.habitrpg.domain.Catalog.bonusesFor(listOf("weapon_sword_gold")) // +15% XP
        val s = TodayBuilder.build(listOf(farmed), emptyList(), listOf(old), today, 0, zone, 0, TodayFilter.ALL, gear)
        assertEquals(18, s.habits.single().xp)             // 20 x 1.15 x 0.8
        assertEquals(35, s.dueNow.single().xp)             // 20 x 1.15 x 1.5 = 34.5, which rounds up like Engine.xpFor
        assertEquals(app.habitrpg.domain.Worth.LESS, s.habits.single().worth)
        assertEquals(app.habitrpg.domain.Worth.MORE, s.dueNow.single().worth)
    }

    @Test fun dailiesNeverCarryAWorthHint() {
        val d = item(Task("d", TaskType.DAILY, schedule = Schedule.Weekdays.EVERY_DAY, value = 9.0))
        assertNull(build(dailies = listOf(d)).dueNow.single().worth)
    }
}
