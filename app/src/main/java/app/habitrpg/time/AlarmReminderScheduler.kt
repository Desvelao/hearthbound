package app.habitrpg.time

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.habitrpg.data.db.ReminderEntity
import app.habitrpg.data.repo.ReminderScheduler
import app.habitrpg.domain.ReminderTimes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One alarm per reminder, always for its next occurrence; the receiver schedules the one after it. Uses exact
 * alarms when the user allows them (Android 12+ can revoke that) and falls back to inexact ones otherwise.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: ReminderEntity) {
        val next = ReminderTimes.next(ZonedDateTime.now(clock), reminder.minuteOfDay, reminder.weekdayMask)
        if (next == null) {
            cancel(reminder.id)
            return
        }
        val at = next.toInstant().toEpochMilli()
        val intent = pendingIntent(reminder.id)
        try {
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        } catch (_: SecurityException) {
            // Exact alarms were revoked between the check and the call.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    override fun cancel(reminderId: String) {
        alarms.cancel(pendingIntent(reminderId))
    }

    /** The data URI makes each reminder's PendingIntent distinct, so one alarm never replaces another's. */
    private fun pendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION)
            .setData(Uri.parse("habitrpg://reminder/$reminderId"))
            .putExtra(ReminderReceiver.EXTRA_ID, reminderId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds abstract fun scheduler(impl: AlarmReminderScheduler): ReminderScheduler
}
