package app.habitrpg.data

import app.habitrpg.data.repo.RewardRepository
import app.habitrpg.data.repo.StatsRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Event
import app.habitrpg.domain.TaskType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest : RepoTestBase() {
    private val d1 = LocalDate.of(2026, 3, 2)
    private fun stats() = StatsRepository(db, settings, clock)

    @Test fun historyIsBuiltFromWhatWasActuallyPlayed() = runTest {
        val walk = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Walk"))
        val todo = tasks.save(TaskDraft(type = TaskType.TODO, title = "Tax", difficulty = Difficulty.MEDIUM))
        game.dispatch(Event.Rollover)
        game.dispatch(Event.HabitPlus(walk)); game.dispatch(Event.HabitPlus(walk))
        game.dispatch(Event.CompleteTodo(todo))
        val h = stats().observeHistory().first()
        assertEquals(3, h.activity.getValue(d1).tasks)
        assertEquals(1, h.currentStreak)
        assertEquals(1, h.bestStreak)
        assertEquals(3, h.tasksThisWeek)
        assertEquals(listOf("Done", "Plus ×2"), h.lines.getValue(d1).map { l -> if (l.count > 1) "Plus ×${l.count}" else "Done" })
    }

    @Test fun anUndoneTickLeavesNoTraceInTheCounts() = runTest {
        val todo = tasks.save(TaskDraft(type = TaskType.TODO, title = "Tax"))
        game.dispatch(Event.CompleteTodo(todo)); game.uncomplete(todo)
        val h = stats().observeHistory().first()
        assertEquals(0, h.activity.getValue(d1).tasks)
        assertEquals(0, h.currentStreak)
        assertEquals(0, h.goldEarned)
    }

    @Test fun streaksFollowConsecutiveGameDays() = runTest {
        val walk = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Walk"))
        game.dispatch(Event.Rollover)
        for (offset in listOf(0L, 1L, 2L, 4L)) { // a gap on the fourth day
            clock.at(d1.plusDays(offset)); game.dispatch(Event.Rollover); game.dispatch(Event.HabitPlus(walk))
        }
        val h = stats().observeHistory().first()
        assertEquals(3, h.bestStreak)
        assertEquals(1, h.currentStreak) // today (day 5) is the last active day, the gap before it ended the run
    }

    @Test fun goldSpentIsTheSumOfPurchases() = runTest {
        val rewards = RewardRepository(db, game, feedback, clock)
        val todo = tasks.save(TaskDraft(type = TaskType.TODO, title = "t", difficulty = Difficulty.HARD)) // 8 gold
        game.dispatch(Event.CompleteTodo(todo))
        assertEquals(0, stats().observeGoldSpent().first())
        assertTrue(rewards.buy(rewards.save(null, "Snack", 5)))
        assertEquals(5, stats().observeGoldSpent().first())
        assertEquals(8, stats().observeHistory().first().goldEarned) // earned is not reduced by spending
    }

    @Test fun healthLostAndMissedDailiesFeedTheHeroNumbers() = runTest {
        tasks.save(TaskDraft(type = TaskType.DAILY, title = "Read", schedule = app.habitrpg.domain.Schedule.Weekdays.EVERY_DAY))
        game.dispatch(Event.Rollover)
        clock.at(d1.plusDays(1)); game.dispatch(Event.Rollover)
        val h = stats().observeHistory().first()
        assertEquals(2, h.weekHealthLost)
        assertEquals(1, h.weekMissed)
    }
}
