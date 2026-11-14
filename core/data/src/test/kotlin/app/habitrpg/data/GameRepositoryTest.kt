package app.habitrpg.data

import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Event
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameRepositoryTest : RepoTestBase() {

    @Test fun completingATodoPersistsCharacterTaskAndEvent() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "t", difficulty = Difficulty.MEDIUM))
        game.dispatch(Event.CompleteTodo(id))
        val c = game.observeCharacter().first()
        assertEquals(15, c.xp)
        assertEquals(6, c.gold)
        assertNotNull(tasks.get(id)!!.completedAt)
        val events = db.eventDao().getAll()
        assertEquals(listOf("DONE"), events.map { it.kind })
        assertEquals(15, events.single().xp)
    }

    @Test fun undoRestoresStateAndAppendsToTheLog() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "t"))
        val done = game.dispatch(Event.CompleteTodo(id))
        game.dispatch(Event.Undo(done.changes.single()))
        assertEquals(0, game.observeCharacter().first().xp)
        assertNull(tasks.get(id)!!.completedAt)
        assertEquals(listOf("DONE", "UNDONE"), db.eventDao().getAll().map { it.kind })
    }

    @Test fun rolloverPersistsDamageOnceAndIsIdempotent() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.DAILY, title = "d", schedule = Schedule.Weekdays.EVERY_DAY))
        game.dispatch(Event.Rollover) // first run anchors the date
        clock.at(LocalDate.of(2026, 3, 3))
        game.dispatch(Event.Rollover)
        game.dispatch(Event.Rollover)
        assertEquals(48, game.observeCharacter().first().health)
        assertEquals(1, db.eventDao().getAll().count { it.kind == "MISSED" })
        assertEquals(0, tasks.get(id)!!.task.streak)
    }

    @Test fun pauseSettingStopsDamage() = runTest {
        tasks.save(TaskDraft(type = TaskType.DAILY, title = "d", schedule = Schedule.Weekdays.EVERY_DAY))
        game.dispatch(Event.Rollover)
        settings.setPaused(true)
        clock.at(LocalDate.of(2026, 3, 3))
        game.dispatch(Event.Rollover)
        assertEquals(50, game.observeCharacter().first().health)
    }

    @Test fun habitCountsResetAtRollover() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "h"))
        game.dispatch(Event.Rollover)
        game.dispatch(Event.HabitPlus(id))
        game.dispatch(Event.HabitPlus(id))
        assertEquals(2, tasks.get(id)!!.task.plusCount)
        clock.at(LocalDate.of(2026, 3, 3))
        game.dispatch(Event.Rollover)
        assertEquals(0, tasks.get(id)!!.task.plusCount)
    }
}
