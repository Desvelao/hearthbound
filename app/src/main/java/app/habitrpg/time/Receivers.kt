package app.habitrpg.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.habitrpg.data.db.ReminderDao
import app.habitrpg.data.repo.ReminderScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Re-creates every alarm from the database. Idempotent, so it is safe to call at any time. */
@Singleton
class ReminderManager @Inject constructor(
    private val reminders: ReminderDao,
    private val scheduler: ReminderScheduler,
) {
    suspend fun rescheduleAll() {
        reminders.getAll().forEach(scheduler::schedule)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun handler(): ReminderHandler
    fun manager(): ReminderManager
}

private fun BroadcastReceiver.work(context: Context, block: suspend (ReminderEntryPoint) -> Unit) {
    val entry = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block(entry)
        } finally {
            pending.finish()
        }
    }
}

/** Fires when a reminder's alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        work(context) { it.handler().onFire(id) }
    }

    companion object {
        const val ACTION = "app.habitrpg.REMINDER"
        const val EXTRA_ID = "reminder_id"
    }
}

/**
 * Alarms do not survive a reboot, an app update, or a clock or time-zone change, and exact-alarm permission can be
 * granted later; all of these re-create them.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only the system should be able to trigger this; ignore anything that is not one of the actions we asked for.
        if (intent.action !in HANDLED) return
        work(context) { it.manager().rescheduleAll() }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
