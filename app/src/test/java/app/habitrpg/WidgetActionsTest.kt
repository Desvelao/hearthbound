package app.habitrpg

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
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Event
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import app.habitrpg.widget.WidgetActions
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import app.habitrpg.widget.WidgetView
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class WidgetClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
    fun at(date: LocalDate, hour: Int = 12) { now = date.atTime(hour, 0).toInstant(ZoneOffset.UTC) }
}

@RunWith(RobolectricTestRunner::class)
class WidgetActionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val monday = LocalDate.of(2026, 3, 2)
    private val clock = WidgetClock(monday.atTime(12, 0).toInstant(ZoneOffset.UTC))
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var tasks: TaskRepository
    private lateinit var game: GameRepository
    private lateinit var widget: WidgetActions
    private val prefs = File.createTempFile("widget", ".preferences_pb").also { it.delete() }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { prefs })
        tasks = TaskRepository(db, NoopReminderScheduler)
        game = GameRepository(db, settings, clock, PlayFeedback())
        widget = WidgetActions(game, tasks, settings, clock)
    }

    @After fun tearDown() { db.close(); prefs.delete() }

    private suspend fun onboarded() { settings.setOnboardingDone(true); game.dispatch(Event.Rollover) }

    private suspend fun daily(title: String, schedule: Schedule = Schedule.Weekdays.EVERY_DAY) =
        tasks.save(TaskDraft(type = TaskType.DAILY, title = title, schedule = schedule, difficulty = Difficulty.MEDIUM))

    private suspend fun kinds() = db.eventDao().getAll().map { it.kind }

    @Test fun beforeOnboardingTheWidgetOnlyInvitesAndTouchesNothing() = runTest {
        val state = widget.load()
        assertFalse(state.ready)
        widget.toggle("anything")
        assertNull("no character row is created behind the onboarding screen", db.characterDao().get())
    }

    @Test fun itListsExactlyTheDailiesDueTodayOpenOnesFirst() = runTest {
        onboarded()
        daily("Done one"); daily("Open one")
        daily("Tuesdays only", Schedule.Weekdays(1 shl (DayOfWeek.TUESDAY.value - 1)))
        val done = db.taskDao().getAll().first { it.title == "Done one" }.id
        game.dispatch(Event.CompleteDaily(done))
        tasks.save(TaskDraft(type = TaskType.HABIT, title = "A habit"))
        tasks.save(TaskDraft(type = TaskType.TODO, title = "A to-do"))

        val s = widget.load()
        assertEquals(listOf("Open one", "Done one"), s.dailies.map { it.title })
        assertEquals(listOf(false, true), s.dailies.map { it.done })
        assertEquals(1, s.finished)
    }

    @Test fun tickingScoresOnceAndAnotherTapTakesItBack() = runTest {
        onboarded()
        val id = daily("Stretch")
        widget.toggle(id)
        assertTrue(tasks.get(id)!!.task.doneToday)
        assertEquals(listOf("DONE"), kinds())
        val xp = game.observeCharacter().first().xp
        assertTrue(xp > 0)

        widget.toggle(id)
        assertFalse(tasks.get(id)!!.task.doneToday)
        assertEquals(0, game.observeCharacter().first().xp)
        assertEquals(listOf("DONE", "UNDONE"), kinds())
    }

    @Test fun aWidgetLeftOverMidnightNeverTakesBackYesterdaysTick() = runTest {
        onboarded()
        val id = daily("Stretch")
        widget.toggle(id)                                       // ticked on Monday
        clock.at(monday.plusDays(1), 9)                         // Tuesday morning; nothing has rolled over yet
        assertTrue("the stale tick is still stored", tasks.get(id)!!.task.doneToday)

        widget.toggle(id)                                       // the tap on the widget
        assertEquals("Monday's tick was kept and Tuesday's added; nothing was taken back", listOf("DONE", "DONE"), kinds())
        assertTrue(tasks.get(id)!!.task.doneToday)
        assertEquals(2, tasks.get(id)!!.task.streak)
        assertEquals("a ticked daily is not a missed one", 50, game.observeCharacter().first().health)
    }

    @Test fun drawingAfterMidnightShowsAFreshUntickedList() = runTest {
        onboarded()
        val id = daily("Stretch")
        widget.toggle(id)
        assertTrue(widget.load().dailies.single().done)
        clock.at(monday.plusDays(1), 7)
        assertFalse("the new day starts unticked", widget.load().dailies.single().done)
        assertEquals(id, widget.load().dailies.single().id)
    }

    @Test fun missedDamageIsAppliedOnceWhateverNumberOfTimesTheWidgetDraws() = runTest {
        onboarded()
        daily("Never done")
        clock.at(monday.plusDays(1), 7)
        widget.load(); widget.load(); widget.load()
        assertEquals(listOf("MISSED"), kinds())
    }

    @Test fun onlyDailiesCanBeTicked() = runTest {
        onboarded()
        val todo = tasks.save(TaskDraft(type = TaskType.TODO, title = "Tax"))
        widget.toggle(todo)
        widget.toggle("not-a-task")
        assertEquals(emptyList<String>(), kinds())
    }

    @Test fun theWidgetViewReportsTheLanguageAndFollowsChangesWhileASessionIsAlive() = runTest {
        onboarded()
        val id = daily("Stretch")
        val first = widget.view()
        assertEquals(app.habitrpg.data.repo.AppLanguage.SYSTEM, first.language)
        assertEquals(listOf(false), first.state.dailies.map { it.done })

        val seen = mutableListOf<WidgetView>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { widget.observe().collect { seen += it } }
        // wait for the first emission from the real database threads
        withTimeout(10_000) { while (seen.isEmpty()) kotlinx.coroutines.delay(20) }

        settings.setLanguage(app.habitrpg.data.repo.AppLanguage.GERMAN)
        withTimeout(10_000) { while (seen.last().language != app.habitrpg.data.repo.AppLanguage.GERMAN) kotlinx.coroutines.delay(20) }
        widget.toggle(id)
        withTimeout(10_000) { while (seen.last().state.dailies.singleOrNull()?.done != true) kotlinx.coroutines.delay(20) }
        job.cancel()

        assertEquals(app.habitrpg.data.repo.AppLanguage.GERMAN, seen.last().language)
        assertTrue(seen.last().state.dailies.single().done)
    }

    @Test fun theWidgetViewBeforeOnboardingIsJustTheInvitation() = runTest {
        val v = widget.view()
        assertFalse(v.state.ready)
    }
}
