package app.habitrpg.feature.tasks.today

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.NoopReminderScheduler
import app.habitrpg.data.repo.PlayFeedback
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-03-02T12:00:00Z"), ZoneOffset.UTC) // a Monday
    private val dispatcher = StandardTestDispatcher()
    private val prefs = File.createTempFile("todayvm", ".preferences_pb").also { it.delete() }
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var game: GameRepository
    private lateinit var vm: TodayViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .setQueryExecutor(Runnable::run).setTransactionExecutor(Runnable::run).build()
        val settings = SettingsRepository(PreferenceDataStoreFactory.create { prefs })
        repo = TaskRepository(db, NoopReminderScheduler)
        game = GameRepository(db, settings, clock, PlayFeedback())
        vm = TodayViewModel(repo, game, settings, clock)
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close(); prefs.delete() }

    private suspend fun daily(title: String = "Stretch") =
        repo.save(TaskDraft(type = TaskType.DAILY, title = title, schedule = Schedule.Weekdays.EVERY_DAY))

    private fun kotlinx.coroutines.test.TestScope.watch() = backgroundScope.launch { vm.state.collect {} }

    @Test fun tickingADailyMovesItToDoneAndUntickingBringsItBack() = runTest(dispatcher) {
        daily(); watch()
        val open = vm.state.first { it.loaded && it.dueNow.size == 1 }
        assertEquals(0, open.finished); assertEquals(1, open.total)

        vm.setDone(open.dueNow.single().item, true)
        val done = vm.state.first { it.done.size == 1 }
        assertTrue(done.dueNow.isEmpty())
        assertEquals(1, done.finished); assertEquals(1, done.total)

        vm.setDone(done.done.single().item, false)
        val back = vm.state.first { it.dueNow.size == 1 }
        assertEquals(0, back.finished)
    }

    @Test fun tickingATodoIsCountedAndRewarded() = runTest(dispatcher) {
        repo.save(TaskDraft(type = TaskType.TODO, title = "Tax")); watch()
        val open = vm.state.first { it.loaded && it.dueNow.size == 1 }
        vm.setDone(open.dueNow.single().item, true)
        vm.state.first { it.finished == 1 }
        assertEquals(10, game.observeCharacter().first().xp)
    }

    @Test fun plusAndMinusOnAHabitUpdateItsCountsAndTheCharacter() = runTest(dispatcher) {
        val id = repo.save(TaskDraft(type = TaskType.HABIT, title = "Water")); watch()
        vm.state.first { it.loaded && it.habits.size == 1 }
        vm.plus(id); vm.plus(id); vm.minus(id)
        val s = vm.state.first { it.habits.singleOrNull()?.item?.task?.plusCount == 2 }
        assertEquals(1, s.habits.single().item.task.minusCount)
        assertEquals(0, s.total) // habits are not part of the day's count
        assertEquals(20 - 0, game.observeCharacter().first().xp)
    }

    @Test fun theFilterChangesWhatIsShownButNotTheCount() = runTest(dispatcher) {
        daily(); repo.save(TaskDraft(type = TaskType.HABIT, title = "Water")); watch()
        val all = vm.state.first { it.loaded && it.dueNow.size == 1 && it.habits.size == 1 }
        vm.setFilter(TodayFilter.HABITS)
        val habits = vm.state.first { it.filter == TodayFilter.HABITS }
        assertTrue(habits.dueNow.isEmpty()); assertEquals(1, habits.habits.size)
        assertEquals(all.total, habits.total)
        vm.setFilter(TodayFilter.DAILIES)
        val dailies = vm.state.first { it.filter == TodayFilter.DAILIES }
        assertEquals(1, dailies.dueNow.size); assertTrue(dailies.habits.isEmpty())
    }

    @Test fun aDailyNotDueTodayIsNotListed() = runTest(dispatcher) {
        repo.save(TaskDraft(type = TaskType.DAILY, title = "Tuesdays", schedule = Schedule.Weekdays.of(java.time.DayOfWeek.TUESDAY)))
        watch()
        val s = vm.state.first { it.loaded }
        assertTrue(s.dueNow.isEmpty()); assertEquals(0, s.total)
    }
}
