package app.habitrpg.data

import app.habitrpg.data.backup.BackupRepository
import app.habitrpg.data.backup.ImportResult
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Event
import app.habitrpg.domain.TaskType
import java.io.ByteArrayInputStream
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskValueRepositoryTest : RepoTestBase() {

    @Test fun valueIsSavedWithEveryTapAndRestoredByUndo() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Water", difficulty = Difficulty.HARD))
        game.dispatch(Event.HabitPlus(id))
        game.dispatch(Event.HabitPlus(id))
        assertEquals(2.0, db.taskDao().get(id)!!.value, 0.0)
        val second = db.eventDao().getAll().last()
        assertEquals("the second tap is paid at value 1 (x0.95)", 19, second.xp)
        game.dispatch(Event.HabitMinus(id))
        assertEquals(1.0, db.taskDao().get(id)!!.value, 0.0)
    }

    @Test fun rolloverSavesTheDriftOfIdleHabitsAndOpenTodos() = runTest {
        val habit = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Idle"))
        val todo = tasks.save(TaskDraft(type = TaskType.TODO, title = "Open"))
        game.dispatch(Event.Rollover)                       // anchors today
        clock.at(LocalDate.of(2026, 3, 3))
        game.dispatch(Event.Rollover)
        assertEquals(-1.0, db.taskDao().get(habit)!!.value, 0.0)
        assertEquals(-0.25, db.taskDao().get(todo)!!.value, 0.0)
        game.dispatch(Event.Rollover)                       // same day again: nothing moves
        assertEquals(-1.0, db.taskDao().get(habit)!!.value, 0.0)
    }

    @Test fun editingATaskKeepsItsValue() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Water"))
        game.dispatch(Event.HabitPlus(id))
        tasks.save(TaskDraft(id = id, type = TaskType.HABIT, title = "Drink water"))
        assertEquals(1.0, db.taskDao().get(id)!!.value, 0.0)
    }

    @Test fun anAbsurdValueInABackupIsClampedNotTrusted() = runTest {
        val task = """{"id":"t","type":"HABIT","title":"x","difficulty":"EASY","sortOrder":0,"value":99999.0}"""
        val backup = BackupRepository(db, settings, scheduler, clock)
        val r = backup.import(ByteArrayInputStream("""{"app":"habit-rpg","version":1,"tasks":[$task]}""".toByteArray()))
        assertTrue(r.toString(), r is ImportResult.Success)
        assertEquals(10.0, db.taskDao().get("t")!!.value, 0.0)
    }
}
