package app.habitrpg.time

import app.habitrpg.data.repo.withLanguage
import app.habitrpg.data.repo.AppLanguage
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.habitrpg.MainActivity
import app.habitrpg.R
import app.habitrpg.data.db.ReminderDao
import app.habitrpg.data.repo.ReminderScheduler
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.GameDay
import app.habitrpg.domain.ReminderPolicy
import app.habitrpg.domain.TaskType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminders: ReminderDao,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    suspend fun onFire(reminderId: String) {
        // A reminder deleted since the alarm was set: nothing to do, and nothing to reschedule.
        val reminder = reminders.get(reminderId) ?: return
        // Schedule the next occurrence first, so a failure below can never end the chain of reminders.
        scheduler.schedule(reminder)

        val item = tasks.get(reminder.taskId) ?: return
        val today = GameDay.today(clock, settings.current().dayStartHour)
        if (!ReminderPolicy.shouldNotify(item.task, today)) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val words = context.withLanguage(AppLanguage.fromTag(settings.current().language))
        val text = words.getString(
            when (item.type) {
                TaskType.HABIT -> R.string.app_reminder_habit
                TaskType.DAILY -> R.string.app_reminder_daily
                TaskType.TODO -> R.string.app_reminder_todo
            },
        )
        val notification = Notification.Builder(context, Notifications.REMINDERS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(item.id.hashCode(), notification)
    }
}
