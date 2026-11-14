package app.habitrpg.data

import app.habitrpg.data.repo.ReminderDraft
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.domain.Constants
import app.habitrpg.domain.Event
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeRepositoryTest : RepoTestBase() {
    private val day1 = LocalDate.of(2026, 3, 2)

    private suspend fun healthNow() = game.observeCharacter().first().health
    private suspend fun missedEvents() = db.eventDao().getAll().count { it.kind == "MISSED" }
    private suspend fun everyDay() = tasks.save(TaskDraft(type = TaskType.DAILY, title = "d", schedule = Schedule.Weekdays.EVERY_DAY))

    // ---- rollover exactly once ----

    @Test fun workerAndAppOpenRunningTogetherDamageOnlyOnce() = runTest {
        everyDay()
        game.dispatch(Event.Rollover)           // first run anchors day 1
        clock.at(day1.plusDays(1))
        repeat(5) { game.dispatch(Event.Rollover) } // app open, hourly worker, app open again...
        assertEquals(Constants.MAX_HEALTH - 2, healthNow())
        assertEquals(1, missedEvents())
    }

    @Test fun reopeningTheDatabaseAfterRebootDoesNotRepeatTheDamage() = runTest {
        // "Reboot": the rollover date lives in the database, not in memory, so a fresh repository sees it.
        everyDay()
        game.dispatch(Event.Rollover)
        clock.at(day1.plusDays(1))
        game.dispatch(Event.Rollover)
        val afterReboot = app.habitrpg.data.repo.GameRepository(db, settings, clock, app.habitrpg.data.repo.PlayFeedback())
        afterReboot.dispatch(Event.Rollover)
        assertEquals(1, missedEvents())
        assertEquals(Constants.MAX_HEALTH - 2, healthNow())
    }

    @Test fun clockMovedBackThenForwardNeverDoublePunishes() = runTest {
        everyDay()
        game.dispatch(Event.Rollover)
        clock.at(day1.plusDays(1)); game.dispatch(Event.Rollover) // day 2: one miss
        clock.at(day1);             game.dispatch(Event.Rollover) // user sets the clock back a day
        clock.at(day1.plusDays(1)); game.dispatch(Event.Rollover) // and forward again
        assertEquals("day 2 was already scored", 1, missedEvents())
    }

    @Test fun threeDayAbsenceIsTreatedAsPaused() = runTest {
        val id = everyDay()
        game.dispatch(Event.Rollover)
        game.dispatch(Event.CompleteDaily(id)) // streak 1
        clock.at(day1.plusDays(3))
        game.dispatch(Event.Rollover)
        assertEquals(Constants.MAX_HEALTH, healthNow())
        assertEquals(0, missedEvents())
        assertEquals("streak frozen", 1, tasks.get(id)!!.task.streak)
    }

    @Test fun twoDayAbsenceDamagesTwice() = runTest {
        everyDay()
        game.dispatch(Event.Rollover)
        clock.at(day1.plusDays(2))
        game.dispatch(Event.Rollover)
        game.dispatch(Event.Rollover)
        assertEquals(2, missedEvents())
        assertEquals(Constants.MAX_HEALTH - 4, healthNow())
    }

    @Test fun travellingWestAcrossTheDateLineDoesNotReplayADay() = runTest {
        everyDay()
        fun repo(c: MutableClock) = app.habitrpg.data.repo.GameRepository(db, settings, c, app.habitrpg.data.repo.PlayFeedback())
        val nz = MutableClock(clock.now, ZoneId.of("Pacific/Auckland"))
        repo(nz).dispatch(Event.Rollover)          // anchors to the local date in Auckland
        nz.at(LocalDate.of(2026, 3, 4), 1)
        repo(nz).dispatch(Event.Rollover)          // a new Auckland day: one miss
        // Same instant, but the phone now shows Los Angeles time, which is an earlier calendar date.
        val la = MutableClock(nz.now, ZoneId.of("America/Los_Angeles"))
        repo(la).dispatch(Event.Rollover)
        la.at(LocalDate.of(2026, 3, 4), 12)        // later that same Los Angeles day
        repo(la).dispatch(Event.Rollover)
        assertEquals(1, missedEvents())
    }

    // ---- day start ----

    @Test fun changingTheDayStartHourNeverDealsDamage() = runTest {
        everyDay()
        settings.setDayStartHour(4)
        clock.at(day1.plusDays(1), 2) // 02:00 still belongs to day 1 with a 04:00 start
        game.dispatch(Event.Rollover)
        game.setDayStartHour(0)       // now it is "day 2"
        game.dispatch(Event.Rollover)
        assertEquals(0, missedEvents())
        assertEquals(Constants.MAX_HEALTH, healthNow())
        assertEquals(0, settings.current().dayStartHour)
    }

    // ---- reminders ----

    @Test fun savingRemindersStoresAndSchedulesThem() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Water",
            reminders = listOf(ReminderDraft(9 * 60), ReminderDraft(18 * 60 + 30, 0b0000001))))
        val item = tasks.get(id)!!
        assertEquals(listOf(9 * 60, 18 * 60 + 30), item.reminders.map { it.minuteOfDay })
        assertEquals(0b0000001, item.reminders.last().weekdayMask)
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(item.reminders.map { it.id }.toSet(), scheduler.scheduled.map { it.id }.toSet())
    }

    @Test fun editingRemindersCancelsTheOldAlarms() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Water", reminders = listOf(ReminderDraft(9 * 60))))
        val oldId = tasks.get(id)!!.reminders.single().id
        tasks.save(TaskDraft(id = id, type = TaskType.HABIT, title = "Water", reminders = listOf(ReminderDraft(10 * 60))))
        assertTrue(oldId in scheduler.cancelled)
        assertEquals(listOf(10 * 60), tasks.get(id)!!.reminders.map { it.minuteOfDay })
    }

    @Test fun deletingATaskCancelsItsAlarms() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Water", reminders = listOf(ReminderDraft(9 * 60))))
        val rid = tasks.get(id)!!.reminders.single().id
        tasks.delete(id)
        assertTrue(rid in scheduler.cancelled)
        assertEquals(0, db.reminderDao().getAll().size)
    }

    @Test fun aReminderWithNoWeekdaysIsDropped() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Water", reminders = listOf(ReminderDraft(9 * 60, 0))))
        assertTrue(tasks.get(id)!!.reminders.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }
}
