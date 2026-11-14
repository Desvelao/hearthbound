package app.habitrpg

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.ReminderEntity
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.NoopReminderScheduler
import app.habitrpg.data.repo.PlayFeedback
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.Constants
import app.habitrpg.domain.Event
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import app.habitrpg.time.AlarmReminderScheduler
import app.habitrpg.time.RolloverWorker
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

private class TestClock(var now: Instant, private val tz: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = tz
    override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)
    override fun instant(): Instant = now
    fun at(date: LocalDate, hour: Int = 12) { now = date.atTime(hour, 0).toInstant(ZoneOffset.UTC) }
}

@RunWith(RobolectricTestRunner::class)
class AlarmReminderSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 3, 2)
    private val clock = TestClock(day.atTime(8, 0).toInstant(ZoneOffset.UTC))
    private val scheduler = AlarmReminderScheduler(context, clock)
    private val shadow get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private fun reminder(id: String, minute: Int = 9 * 60, mask: Int = 0b1111111) = ReminderEntity(id, "task", minute, mask)
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun schedulesTheNextOccurrence() {
        scheduler.schedule(reminder("r1"))
        assertEquals(at(day, 9), shadow.nextScheduledAlarm!!.triggerAtMs)
    }

    @Test fun afterTheTimeHasPassedItIsTomorrow() {
        clock.at(day, 10)
        scheduler.schedule(reminder("r1"))
        assertEquals(at(day.plusDays(1), 9), shadow.nextScheduledAlarm!!.triggerAtMs)
    }

    @Test fun schedulingTheSameReminderTwiceKeepsOneAlarm() {
        scheduler.schedule(reminder("r1")); scheduler.schedule(reminder("r1"))
        assertEquals(1, shadow.scheduledAlarms.size)
    }

    @Test fun differentRemindersGetDifferentAlarms() {
        scheduler.schedule(reminder("r1")); scheduler.schedule(reminder("r2", 18 * 60))
        assertEquals(2, shadow.scheduledAlarms.size)
    }

    @Test fun cancelRemovesOnlyThatAlarm() {
        scheduler.schedule(reminder("r1")); scheduler.schedule(reminder("r2", 18 * 60))
        scheduler.cancel("r1")
        assertEquals(listOf(at(day, 18)), shadow.scheduledAlarms.map { it.triggerAtMs })
    }

    @Test fun aReminderWithNoDaysCancelsItself() {
        scheduler.schedule(reminder("r1"))
        scheduler.schedule(reminder("r1", mask = 0))
        assertNull(shadow.nextScheduledAlarm)
    }

    @Test fun fallsBackToAnInexactAlarmWhenExactAlarmsAreDenied() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        scheduler.schedule(reminder("r1"))
        assertNotNull("a reminder must still be scheduled", shadow.nextScheduledAlarm)
    }
}

@RunWith(RobolectricTestRunner::class)
class RolloverWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = TestClock(LocalDate.of(2026, 3, 2).atTime(12, 0).toInstant(ZoneOffset.UTC))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = File.createTempFile("workertest", ".preferences_pb").also { it.delete() }
    private lateinit var db: AppDatabase
    private lateinit var game: GameRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        game = GameRepository(db, SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { prefs }), clock, PlayFeedback())
    }

    @After fun tearDown() { db.close(); prefs.delete() }

    private fun worker() = TestListenableWorkerBuilder<RolloverWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(c: Context, name: String, p: WorkerParameters): ListenableWorker = RolloverWorker(c, p, game)
        }).build()

    @Test fun theWorkerRunsTheRolloverOnceNoMatterHowOftenItFires() = runBlocking {
        TaskRepository(db, NoopReminderScheduler).save(TaskDraft(type = TaskType.DAILY, title = "d", schedule = Schedule.Weekdays.EVERY_DAY))
        game.dispatch(Event.Rollover)                     // first run anchors the date
        clock.at(LocalDate.of(2026, 3, 3))
        repeat(3) { assertEquals(ListenableWorker.Result.success(), worker().doWork()) }
        game.dispatch(Event.Rollover)                     // and the app opening afterwards adds nothing
        assertEquals(Constants.MAX_HEALTH - 2, game.observeCharacter().first().health)
        assertEquals(1, db.eventDao().getAll().count { it.kind == "MISSED" })
    }
}
