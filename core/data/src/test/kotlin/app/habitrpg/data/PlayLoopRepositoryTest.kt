package app.habitrpg.data

import app.habitrpg.data.repo.ChecklistDraft
import app.habitrpg.data.repo.Feedback
import app.habitrpg.data.repo.RewardRepository
import app.habitrpg.data.repo.StatsRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Event
import app.habitrpg.domain.Milestone
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayLoopRepositoryTest : RepoTestBase() {

    @Test fun uncompleteReversesTheTickFromTheLogAndIsRepeatSafe() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "t", difficulty = Difficulty.MEDIUM))
        game.dispatch(Event.CompleteTodo(id))
        assertNotNull(game.uncomplete(id))
        assertEquals(0, game.observeCharacter().first().xp)
        assertNull(tasks.get(id)!!.completedAt)
        assertNull("second un-tick must do nothing", game.uncomplete(id))
        assertEquals(0, game.observeCharacter().first().gold)
        // tick again after un-ticking works and can be un-ticked again
        game.dispatch(Event.CompleteTodo(id))
        assertNotNull(game.uncomplete(id))
        assertEquals(listOf("DONE", "UNDONE", "DONE", "UNDONE"), db.eventDao().getAll().map { it.kind })
    }

    @Test fun uncompleteDailyKeepsStreakConsistent() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.DAILY, title = "d", schedule = Schedule.Weekdays.EVERY_DAY))
        game.dispatch(Event.CompleteDaily(id))
        assertEquals(1, tasks.get(id)!!.task.streak)
        game.uncomplete(id)
        assertEquals(0, tasks.get(id)!!.task.streak)
        assertFalse(tasks.get(id)!!.task.doneToday)
    }

    @Test fun eventsKeepTheTitleAfterTheTaskIsDeleted() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "Gone soon"))
        game.dispatch(Event.CompleteTodo(id))
        tasks.delete(id)
        assertEquals("Gone soon", db.eventDao().getAll().single().taskTitle)
    }

    @Test fun feedbackIsEmittedWithUndoForASingleTick() = runTest(UnconfinedTestDispatcher()) {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Walk"))
        val got = async { feedback.events.first() }
        game.dispatch(Event.HabitPlus(id))
        val f = got.await() as Feedback.Scored
        assertEquals("Walk", f.title)
        assertEquals(10, f.xp)
        assertNotNull(f.undo)
    }

    @Test fun undoingViaTheFeedbackSnapshotRestoresState() = runTest(UnconfinedTestDispatcher()) {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Walk"))
        val got = async { feedback.events.first() as Feedback.Scored }
        game.dispatch(Event.HabitPlus(id))
        game.dispatch(Event.Undo(got.await().undo!!))
        assertEquals(0, game.observeCharacter().first().xp)
        assertEquals(0, tasks.get(id)!!.task.plusCount)
    }

    @Test fun levelUpAndZeroHealthMilestonesReachFeedback() = runTest(UnconfinedTestDispatcher()) {
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "Big", difficulty = Difficulty.HARD))
        repeat(4) { game.dispatch(Event.CompleteTodo(tasks.save(TaskDraft(type = TaskType.TODO, title = "x", difficulty = Difficulty.HARD)))) }
        val got = async { feedback.events.first() as Feedback.Scored }
        game.dispatch(Event.CompleteTodo(id)) // 5 x 20 xp = 100 -> level 2
        assertTrue(Milestone.LEVEL_UP in got.await().milestones)
        assertEquals(2, game.observeCharacter().first().level)
    }

    @Test fun undoAndSpendAreSilent() = runTest(UnconfinedTestDispatcher()) {
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "t"))
        val seen = mutableListOf<Feedback>()
        val job = async { feedback.events.collect { seen += it } }
        val done = game.dispatch(Event.CompleteTodo(id))
        game.dispatch(Event.Undo(done.changes.single()))
        game.dispatch(Event.Spend(0))
        job.cancel()
        assertEquals(1, seen.size)
    }

    @Test fun buyingARewardNeedsEnoughGoldAndRecordsAPurchase() = runTest {
        val rewards = RewardRepository(db, game, feedback, clock)
        val tv = rewards.save(null, "Watch an episode", 6)
        assertFalse("no gold yet", rewards.buy(tv))
        val t = tasks.save(TaskDraft(type = TaskType.TODO, title = "t", difficulty = Difficulty.MEDIUM)) // 6 gold
        game.dispatch(Event.CompleteTodo(t))
        assertTrue(rewards.buy(tv))
        assertEquals(0, game.observeCharacter().first().gold)
        assertEquals(1, db.rewardDao().getPurchases().size)
        assertFalse("gold is spent", rewards.buy(tv))
    }

    @Test fun dailyChecklistResetsAtNewDayButTodoChecklistDoesNot() = runTest {
        val d = tasks.save(TaskDraft(type = TaskType.DAILY, title = "d", schedule = Schedule.Weekdays.EVERY_DAY,
            checklist = listOf(ChecklistDraft("a", done = true))))
        val t = tasks.save(TaskDraft(type = TaskType.TODO, title = "t", checklist = listOf(ChecklistDraft("b", done = true))))
        game.dispatch(Event.Rollover)
        clock.at(LocalDate.of(2026, 3, 3))
        game.dispatch(Event.Rollover)
        assertFalse(tasks.get(d)!!.checklist.single().done)
        assertTrue(tasks.get(t)!!.checklist.single().done)
    }

    @Test fun weeklyStatsSumNetXpPerDay() = runTest {
        val stats = StatsRepository(db, settings, clock)
        val a = tasks.save(TaskDraft(type = TaskType.TODO, title = "a")) // 10 xp
        val b = tasks.save(TaskDraft(type = TaskType.TODO, title = "b"))
        game.dispatch(Event.CompleteTodo(a))
        game.dispatch(Event.CompleteTodo(b))
        game.uncomplete(b)
        val week = stats.observeWeek().first()
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 3, 2), week.last().date)
        assertEquals(10, week.last().xp)
        assertEquals(0, week.first().xp)
        val recent = stats.observeRecent().first()
        assertEquals(listOf("UNDONE", "DONE", "DONE"), recent.map { it.kind })
    }
}

class HeldRolloverFeedbackTest {
    private fun scored(health: Int, milestones: List<Milestone> = emptyList()) = Feedback.Scored(
        title = null, xp = 0, gold = 0, health = health, undo = null, milestones = milestones,
        characterAfter = app.habitrpg.domain.Character(), fromRollover = true,
    )

    @Test fun aRolloverResultIsHeldUntilAScreenConsumesIt() {
        val feedback = app.habitrpg.data.repo.PlayFeedback()
        feedback.emit(scored(-2)) // nobody is collecting yet: the app is still starting
        assertEquals(-2, feedback.rollover.value!!.health)
        feedback.consumeRollover()
        assertNull(feedback.rollover.value)
    }

    @Test fun twoRolloversBeforeAnyoneLooksAreAddedUpAndKeepEveryDialog() {
        val feedback = app.habitrpg.data.repo.PlayFeedback()
        feedback.emit(scored(-2, listOf(Milestone.HEALTH_ZERO)))
        feedback.emit(scored(-4, listOf(Milestone.LEVEL_UP)))
        val held = feedback.rollover.value!!
        assertEquals(-6, held.health)
        assertEquals(listOf(Milestone.HEALTH_ZERO, Milestone.LEVEL_UP), held.milestones)
    }
}
