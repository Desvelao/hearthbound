package app.habitrpg.feature.character

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.TaskEventEntity
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.StatsRepository
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 13)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()
    private val prefs = File.createTempFile("histvm", ".preferences_pb").also { it.delete() }
    private lateinit var db: AppDatabase
    private lateinit var vm: HistoryViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .setQueryExecutor(Runnable::run).setTransactionExecutor(Runnable::run).build()
        val settings = SettingsRepository(PreferenceDataStoreFactory.create { prefs })
        vm = HistoryViewModel(StatsRepository(db, settings, clock), settings, clock)
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close(); prefs.delete() }

    private fun log(day: LocalDate, kind: String, title: String, xp: Int = 10, gold: Int = 4, hour: Int = 10) = runBlocking {
        db.eventDao().insertAll(listOf(TaskEventEntity(UUID.randomUUID().toString(), title, title, day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), kind, xp, gold, 0, null)))
    }

    /** Collects the state and waits for the first real value: DataStore reads on a real IO thread, so idling is not enough. */
    private suspend fun kotlinx.coroutines.test.TestScope.watch() {
        backgroundScope.launch { vm.state.collect {} }
        vm.state.first { it.loaded }
    }

    @Test fun opensOnTodayInTheCurrentMonthWithItsNumbers() = runTest(dispatcher) {
        log(today, "DONE", "Stretch"); log(today.minusDays(1), "DONE", "Read"); log(today.minusDays(1), "DONE", "Tidy", hour = 11)
        watch(); advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.loaded)
        assertEquals(YearMonth.of(2026, 11), s.month)
        assertEquals(today, s.selected)
        assertEquals(3, s.monthTasks)
        assertEquals(30, s.monthXp)
        assertEquals(2, s.bestStreak)
        assertFalse(s.canGoNext)
        assertEquals(listOf("Stretch"), s.dayLines.map { it.title })
    }

    @Test fun pickingADayShowsThatDaysLog() = runTest(dispatcher) {
        log(today, "DONE", "Stretch"); log(today.minusDays(3), "DONE", "Read")
        watch(); advanceUntilIdle()
        vm.select(today.minusDays(3)); advanceUntilIdle()
        assertEquals(today.minusDays(3), vm.state.value.selected)
        assertEquals(listOf("Read"), vm.state.value.dayLines.map { it.title })
    }

    @Test fun aFutureDayCannotBeSelected() = runTest(dispatcher) {
        watch(); advanceUntilIdle()
        vm.select(today.plusDays(2)); advanceUntilIdle()
        assertEquals(today, vm.state.value.selected)
    }

    @Test fun goingBackAMonthShowsThatMonthAndLandsOnItsLastBusyDay() = runTest(dispatcher) {
        val oct = LocalDate.of(2026, 10, 20)
        log(oct, "DONE", "Old one"); log(LocalDate.of(2026, 10, 5), "DONE", "Older")
        watch(); advanceUntilIdle()
        vm.previousMonth(); advanceUntilIdle()
        val s = vm.state.value
        assertEquals(YearMonth.of(2026, 10), s.month)
        assertEquals(oct, s.selected)
        assertEquals(2, s.monthTasks)
        assertTrue(s.canGoNext)
    }

    @Test fun nextMonthDoesNothingOnTheCurrentMonthAndWorksAfterGoingBack() = runTest(dispatcher) {
        watch(); advanceUntilIdle()
        vm.nextMonth(); advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        vm.previousMonth(); advanceUntilIdle(); vm.nextMonth(); advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        assertEquals(today, vm.state.value.selected)
    }

    @Test fun anEmptyEarlierMonthStillShowsACalendar() = runTest(dispatcher) {
        watch(); advanceUntilIdle()
        repeat(3) { vm.previousMonth(); advanceUntilIdle() }
        val s = vm.state.value
        assertEquals(YearMonth.of(2026, 8), s.month)
        assertEquals(0, s.monthTasks)
        assertTrue(s.weeks.isNotEmpty())
        assertEquals(LocalDate.of(2026, 8, 1), s.selected)
    }
}
