package app.habitrpg.data

import androidx.room.Room
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.repo.ChecklistDraft
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskRepositoryTest : RepoTestBase() {

    @Test fun createReadEditDelete() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = " Buy milk ", dueDate = LocalDate.of(2026, 4, 1),
            checklist = listOf(ChecklistDraft("2 litres"), ChecklistDraft("  "))))
        val item = tasks.get(id)!!
        assertEquals("Buy milk", item.title)
        assertEquals(LocalDate.of(2026, 4, 1), item.dueDate)
        assertEquals(listOf("2 litres"), item.checklist.map { it.text })

        tasks.save(TaskDraft(id = id, type = TaskType.TODO, title = "Buy oat milk", difficulty = Difficulty.HARD))
        assertEquals("Buy oat milk", tasks.get(id)!!.title)
        assertEquals(Difficulty.HARD, tasks.get(id)!!.task.difficulty)
        assertTrue(tasks.get(id)!!.checklist.isEmpty())

        tasks.delete(id)
        assertNull(tasks.get(id))
    }

    @Test fun saveAllStoresEveryDraftInOrder() = runTest {
        val ids = tasks.saveAll(listOf(
            TaskDraft(type = TaskType.TODO, title = " first "),
            TaskDraft(type = TaskType.TODO, title = "second"),
            TaskDraft(type = TaskType.DAILY, title = "stretch", schedule = Schedule.Weekdays.EVERY_DAY),
        ))
        assertEquals(3, ids.size)
        assertEquals(listOf("first", "second"), tasks.observe(TaskType.TODO).first().map { it.title })
        assertEquals(Schedule.Weekdays.EVERY_DAY, tasks.get(ids[2])!!.task.schedule)
    }

    @Test fun dailySchedulesRoundTrip() = runTest {
        val w = tasks.save(TaskDraft(type = TaskType.DAILY, title = "Gym",
            schedule = Schedule.Weekdays.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))
        val n = tasks.save(TaskDraft(type = TaskType.DAILY, title = "Water plants",
            schedule = Schedule.EveryNDays(3, LocalDate.of(2026, 3, 1))))
        assertEquals(Schedule.Weekdays.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), tasks.get(w)!!.task.schedule)
        assertEquals(Schedule.EveryNDays(3, LocalDate.of(2026, 3, 1)), tasks.get(n)!!.task.schedule)
    }

    @Test fun listsAreSplitByTypeAndOrderedByInsertion() = runTest {
        tasks.save(TaskDraft(type = TaskType.HABIT, title = "a"))
        tasks.save(TaskDraft(type = TaskType.HABIT, title = "b"))
        tasks.save(TaskDraft(type = TaskType.TODO, title = "c"))
        assertEquals(listOf("a", "b"), tasks.observe(TaskType.HABIT).first().map { it.title })
        assertEquals(listOf("c"), tasks.observe(TaskType.TODO).first().map { it.title })
    }

    @Test fun reorderPersists() = runTest {
        val a = tasks.save(TaskDraft(type = TaskType.HABIT, title = "a"))
        val b = tasks.save(TaskDraft(type = TaskType.HABIT, title = "b"))
        val c = tasks.save(TaskDraft(type = TaskType.HABIT, title = "c"))
        tasks.reorder(listOf(c, a, b))
        assertEquals(listOf("c", "a", "b"), tasks.observe(TaskType.HABIT).first().map { it.title })
    }

    @Test fun editingKeepsEngineOwnedFields() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Walk"))
        game.dispatch(app.habitrpg.domain.Event.HabitPlus(id))
        tasks.save(TaskDraft(id = id, type = TaskType.HABIT, title = "Walk far"))
        assertEquals(1, tasks.get(id)!!.task.plusCount)
    }

    @Test fun dataSurvivesReopeningTheDatabase() = runTest {
        // A file-backed database stands in for an app restart.
        val file = File.createTempFile("restart", ".db")
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath).allowMainThreadQueries().build()
        var database = open()
        val id = TaskRepository(database, app.habitrpg.data.repo.NoopReminderScheduler).save(TaskDraft(type = TaskType.TODO, title = "Persist me"))
        val other = TaskRepository(database, app.habitrpg.data.repo.NoopReminderScheduler).save(TaskDraft(type = TaskType.TODO, title = "Second"))
        TaskRepository(database, app.habitrpg.data.repo.NoopReminderScheduler).reorder(listOf(other, id))
        database.close()

        database = open()
        assertEquals(listOf("Second", "Persist me"), TaskRepository(database, app.habitrpg.data.repo.NoopReminderScheduler).observe(TaskType.TODO).first().map { it.title })
        TaskRepository(database, app.habitrpg.data.repo.NoopReminderScheduler).delete(id)
        database.close()

        database = open()
        assertEquals(listOf("Second"), TaskRepository(database, app.habitrpg.data.repo.NoopReminderScheduler).observe(TaskType.TODO).first().map { it.title })
        database.close()
        file.delete()
    }
}
